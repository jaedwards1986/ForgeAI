#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <fstream>
#include <limits>
#include <mutex>
#include <string>
#include <vector>

namespace {
constexpr const char * TAG = "DavotedLlama";
constexpr int32_t MAX_BATCH = 512;

std::mutex g_mutex;
std::atomic_bool g_cancelled{false};
bool g_backend_initialized = false;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;

void throw_java(JNIEnv * env, const char * message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) {
        env->ThrowNew(type, message);
    }
}

void release_model_locked() {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

bool process_tokens(
    llama_context * context,
    const std::vector<llama_token> & tokens,
    int32_t start_position,
    bool request_last_logits
) {
    auto * batch = llama_batch_ext_init(context);
    if (batch == nullptr) {
        return false;
    }

    bool ok = true;
    for (size_t offset = 0; offset < tokens.size() && ok; offset += MAX_BATCH) {
        llama_batch_ext_clear(batch);
        const size_t end = std::min(tokens.size(), offset + MAX_BATCH);

        for (size_t i = offset; i < end; ++i) {
            const int32_t index = llama_batch_ext_add_token(batch, 0, tokens[i]);
            if (index < 0) {
                ok = false;
                break;
            }
            const llama_pos position = start_position + static_cast<llama_pos>(i);
            if (!llama_batch_ext_set_pos(batch, index, &position)) {
                ok = false;
                break;
            }
            const bool want_logits = request_last_logits && (i + 1 == tokens.size());
            if (!llama_batch_ext_set_output_logits(batch, index, want_logits)) {
                ok = false;
                break;
            }
        }

        if (ok && llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            ok = false;
        }
    }

    llama_batch_ext_free(batch);
    return ok;
}

std::string token_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(256);
    int32_t count = llama_token_to_piece(vocab, token, buffer.data(),
                                         static_cast<int32_t>(buffer.size()), 0, false);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(vocab, token, buffer.data(),
                                     static_cast<int32_t>(buffer.size()), 0, false);
    }
    if (count <= 0) {
        return {};
    }
    return std::string(buffer.data(), static_cast<size_t>(count));
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_ai_davoted_craving_ai_JniLlamaCppRuntime_nativeLoadModel(
    JNIEnv * env, jobject, jstring j_path, jint context_size, jint thread_count
) {
    if (j_path == nullptr || context_size < 512 || thread_count < 1) {
        throw_java(env, "Invalid model path or llama.cpp configuration.");
        return JNI_FALSE;
    }

    const char * path = env->GetStringUTFChars(j_path, nullptr);
    if (path == nullptr) {
        return JNI_FALSE;
    }
    const std::string model_path(path);
    env->ReleaseStringUTFChars(j_path, path);

    std::ifstream model_file(model_path, std::ios::binary);
    char magic[4] = {};
    if (!model_file.read(magic, sizeof(magic)) ||
        magic[0] != 'G' || magic[1] != 'G' || magic[2] != 'U' || magic[3] != 'F') {
        throw_java(env, "Selected file is not a readable GGUF model.");
        return JNI_FALSE;
    }
    model_file.seekg(0, std::ios::end);
    if (model_file.tellg() <= 4) {
        throw_java(env, "GGUF model file is empty or truncated.");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    release_model_locked();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (g_model == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "GGUF model load failed: %s", model_path.c_str());
        throw_java(env, "llama.cpp could not load the GGUF model.");
        return JNI_FALSE;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_size);
    context_params.n_batch = static_cast<uint32_t>(std::min<int>(MAX_BATCH, context_size));
    context_params.n_ubatch = context_params.n_batch;
    context_params.n_threads = std::clamp<int>(thread_count, 1, 8);
    context_params.n_threads_batch = context_params.n_threads;
    context_params.offload_kqv = false;

    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        release_model_locked();
        throw_java(env, "llama.cpp could not initialize the inference context.");
        return JNI_FALSE;
    }

    g_cancelled.store(false);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Loaded GGUF model on arm64 CPU: %s", model_path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_davoted_craving_ai_JniLlamaCppRuntime_nativeGenerate(
    JNIEnv * env, jobject, jstring j_prompt, jint max_tokens, jfloat temperature
) {
    if (j_prompt == nullptr || max_tokens < 1 || max_tokens > 4096 ||
        temperature < 0.0f || temperature > 2.0f) {
        throw_java(env, "Invalid generation request.");
        return nullptr;
    }

    const char * prompt_chars = env->GetStringUTFChars(j_prompt, nullptr);
    if (prompt_chars == nullptr) {
        return nullptr;
    }
    const std::string prompt(prompt_chars);
    env->ReleaseStringUTFChars(j_prompt, prompt_chars);

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr) {
        throw_java(env, "No GGUF model is loaded.");
        return nullptr;
    }

    g_cancelled.store(false);
    llama_memory_clear(llama_get_memory(g_context), true);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const int32_t capacity = static_cast<int32_t>(llama_n_ctx(g_context));
    std::vector<llama_token> prompt_tokens(static_cast<size_t>(capacity));
    const int32_t prompt_count = llama_tokenize(
        vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
        prompt_tokens.data(), capacity, true, true
    );

    if (prompt_count <= 0 || prompt_count >= capacity - 1) {
        throw_java(env, "Prompt is empty or exceeds the configured context window.");
        return nullptr;
    }
    prompt_tokens.resize(static_cast<size_t>(prompt_count));

    if (!process_tokens(g_context, prompt_tokens, 0, true)) {
        throw_java(env, "llama.cpp failed while evaluating the prompt.");
        return nullptr;
    }

    auto sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    if (sampler == nullptr) {
        throw_java(env, "llama.cpp could not initialize its sampler.");
        return nullptr;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    if (temperature <= 0.001f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string output;
    llama_pos position = static_cast<llama_pos>(prompt_count);
    const llama_pos context_limit = static_cast<llama_pos>(llama_n_ctx(g_context));
    for (int generated = 0; generated < max_tokens && position < context_limit; ++generated) {
        if (g_cancelled.load()) {
            break;
        }

        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }
        llama_sampler_accept(sampler, token);
        output += token_piece(vocab, token);

        std::vector<llama_token> next_token{token};
        if (!process_tokens(g_context, next_token, position, true)) {
            llama_sampler_free(sampler);
            throw_java(env, "llama.cpp failed while generating a token.");
            return nullptr;
        }
        ++position;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_davoted_craving_ai_JniLlamaCppRuntime_nativeCancel(JNIEnv *, jobject) {
    g_cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_ai_davoted_craving_ai_JniLlamaCppRuntime_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancelled.store(true);
    release_model_locked();
    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }
}
