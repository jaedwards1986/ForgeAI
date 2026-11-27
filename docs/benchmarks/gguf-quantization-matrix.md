# DavotedCraving GGUF quantization benchmark

Status: protocol only. No quantization has been selected or declared successful until the same model family is measured on the target Pixel 9 Pro XL.

## Candidate order

Use the same base model and tokenizer/chat template for every comparison. Compare available, legally redistributable GGUF builds in this order:

1. Q4_K_S — current accepted default candidate.
2. Q4_K_M — quality/size midpoint.
3. Q5_K_M — quality-oriented alternative.
4. IQ4_XS — optional smaller candidate only when the model publisher provides a compatible build.
5. Q3_K_M — last-resort size comparison; do not select solely because it fits.

Do not compare unrelated fine-tunes as if quantization were the only changed variable. Record the exact repository, immutable revision, filename, SHA-256, file size, architecture, tokenizer, and chat-template metadata.

## Target-device protocol

Run on a physical Google Pixel 9 Pro XL, arm64-v8a, with the same app build and device state. Record Android build, free storage, battery level, thermal status, and background process state.

For each candidate, perform three cold-load trials and five generation trials after load:

- Validate file length, GGUF magic, and SHA-256 before loading.
- Record native model-load duration and whether the JNI library is present.
- Record process PSS/RSS before load, after load, and at peak generation.
- Use a fixed 2,048-token context for the first comparison; repeat the winner at 4,096 tokens.
- Use a fixed prompt suite with factual multi-source synthesis, long-context recall, structured summarization, and multi-turn continuity.
- Generate at least 256 tokens per trial, using identical sampler settings and prompt text.
- Record prompt-evaluation tokens/second, generation tokens/second, time-to-first-token, completion length, failures, and thermal throttling.
- Repeat the winning candidate after a force-stop and device restart.

Never claim a candidate is viable based on a successful JNI load alone. It must also complete the quality suite and remain within measured memory/thermal limits.

## Decision gates

A candidate is eligible only if all of these are true:

- 5/5 generation trials complete without crash, native error, or empty output.
- 3/3 cold-load trials succeed.
- The factual benchmark does not invent unsupported details or merge archive facts with unsourced claims.
- The continuity benchmark retains required facts across the complete prompt and conversation history.
- Peak memory leaves a documented safety margin for Android and the UI; no out-of-memory termination.
- No sustained thermal throttling during the repeat run.
- Measured latency is acceptable for interactive use.

Choose the smallest candidate that passes every gate. If Q4_K_S fails quality, memory, or thermal gates, evaluate Q4_K_M and Q5_K_M rather than silently changing the default.

## Result sheet

| Candidate | SHA-256 | Size | Cold load (3) | Peak PSS | Prompt tok/s | Gen tok/s | Quality pass | Thermal pass | Decision |
|---|---|---:|---|---:|---:|---:|---|---|---|
| Q4_K_S | pending | pending | pending | pending | pending | pending | pending | pending | pending |
| Q4_K_M | pending | pending | pending | pending | pending | pending | pending | pending | pending |
| Q5_K_M | pending | pending | pending | pending | pending | pending | pending | pending | pending |
| IQ4_XS | pending | pending | pending | pending | pending | pending | pending | pending | pending |
| Q3_K_M | pending | pending | pending | pending | pending | pending | pending | pending | pending |

## Reproducibility

Keep raw logcat output, benchmark prompt/version, model hash, app commit SHA, and device metadata with each result. Do not replace pending values with estimates or emulator measurements.
