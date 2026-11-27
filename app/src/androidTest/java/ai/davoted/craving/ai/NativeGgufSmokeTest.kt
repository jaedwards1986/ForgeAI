package ai.davoted.craving.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real JNI/GGUF smoke test. Supply -e davoted.gguf.path /absolute/device/path/to/model.gguf
 * to connectedAndroidTest. Without a supplied model it is reported as skipped, never passed.
 */
@RunWith(AndroidJUnit4::class)
class NativeGgufSmokeTest {
    @Test
    fun loadsRealGgufAndGeneratesTokens() {
        val path = InstrumentationRegistry.getArguments().getString("davoted.gguf.path")
        assumeTrue(
            "Set instrumentation argument davoted.gguf.path to a real on-device GGUF file",
            !path.isNullOrBlank()
        )
        val model = File(requireNotNull(path))
        assertTrue("GGUF file does not exist: $path", model.isFile)
        assertTrue("GGUF file is unexpectedly small", model.length() > 1024L * 1024L)
        model.inputStream().use { input ->
            val magic = ByteArray(4)
            assertTrue("Could not read GGUF header", input.read(magic) == 4)
            assertTrue("File does not have GGUF magic", magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46)))
        }

        val runtime = JniLlamaCppRuntime()
        assertTrue("libdavoted_llama is not packaged or could not be loaded", runtime.isAvailable)
        try {
            assertTrue("llama.cpp failed to load the supplied GGUF", runtime.loadModel(model.absolutePath, 2048, 4))
            val response = runtime.generate(
                prompt = "You are a concise factual assistant. Reply with the words: local inference works.",
                maxTokens = 48,
                temperature = 0.0f
            )
            assertFalse("Native generation returned an empty response", response.isBlank())
        } finally {
            runtime.close()
        }
    }
}
