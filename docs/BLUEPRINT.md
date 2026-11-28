# NEXORA — Version 1 Build Contract

**Status:** Scope baseline. This document defines the first deliverable. Changes require the user's approval.

## 1. Product

NEXORA is an offline-first Android character-chat app. The user selects a fictional adult character and chats with that character in a conventional messaging interface. The user does not write prompts or configure model instructions.

The first release is deliberately a focused chat app, not a game platform or media library.

## 2. Version 1 user experience

The app has four primary areas:

1. **Character list** — browse a small set of bundled fictional adult characters in a vertical list. Each row shows a portrait thumbnail, character name, and short description. Selecting a row opens that character's profile.
2. **Character profile** — view the character description and start or resume a conversation.
3. **Chat** — send messages and receive locally generated model responses. The app automatically supplies the selected character definition and conversation context.
4. **Settings / Model manager** — see the active model, import or download a compatible GGUF model, select an installed model, and view basic model status.

Conversation history is saved locally and remains associated with its character. Switching models must not delete or rewrite character profiles or saved chat history.

### Character selection decision — Question 1

**Approved choice: B — Character list.**

The v1 character-selection screen is a list, not a portrait-card grid and not a category-first browser. Each list row contains:
- A portrait thumbnail.
- The character's display name.
- A short description.
- A tap action that opens the character's profile.

This is a documented design decision. It is not represented as implemented until the screen exists in the app and is verified.

## 3. Character system

- Character profiles are app-owned structured data, separate from model files.
- Each profile contains a stable ID, display name, portrait asset, description, personality traits, and conversation style.
- The app supplies character instructions automatically; the user never needs to write hidden prompts.
- Characters are fictional adults.
- Portraits are static assets bundled with the app in v1. In-app image generation and user-created portrait tooling are excluded from v1.
- The initial release starts with a small approved set of characters. The user can help define and approve their appearance and profiles before those assets are finalized.

## 4. Local model and chat

- Android app written in Kotlin.
- Local inference through llama.cpp using compatible GGUF models.
- No remote inference, paid API, account, subscription, advertising, or server required for chatting.
- The app includes one verified starter model only if distribution size and licensing permit; otherwise the user imports/downloads the model through the app's model manager.
- Model downloads require an internet connection. Once a model is installed, chat must work in airplane mode.
- Model compatibility is validated before activation. The app must not claim every GGUF model is supported.
- Only one model runs at a time in v1.
- Model switching preserves characters and conversation records. A conversation may sound different under a different model; identical personality or response quality across models is not guaranteed.
- Chat requests include the selected character profile and a bounded amount of conversation history. Context limits and generation settings are handled by the app.
- Generation can be cancelled, and the UI must show loading/error states rather than appearing frozen.

## 5. Local persistence

- Store character definitions, conversation records, active model selection, and app settings locally.
- Use stable character IDs so conversations remain attached to the same character if portraits or model files change.
- Do not store persona definitions inside a model file.
- Do not erase conversations when a model is removed or changed.
- Provide clear handling for insufficient storage and failed model imports.

## 6. Explicit v1 exclusions

Not included in the first release:
- Voice input/output or live voice calls.
- Image or video generation.
- Media gallery or phone-photo import.
- Online character marketplace or community sharing.
- Cloud accounts, sync, remote databases, or remote inference.
- Complex relationship meters, unlock systems, branching games, or scenario generators.
- Guaranteed compatibility with every GGUF file.
- Guaranteed identical character behavior across different models.

These are excluded to keep the first build testable and achievable. Any addition requires explicit scope approval.

## 7. Build milestones and acceptance tests

### Milestone A — Android shell
- Project builds as a debug APK.
- App launches on the target Pixel 9 Pro XL.
- Character list, profile, chat, and settings screens navigate correctly.
- Placeholder chat UI is clearly identified as not yet connected to inference.

### Milestone B — one local model
- Integrate llama.cpp and load one selected GGUF model on-device.
- Send a message and receive a generated response.
- Verify repeated multi-turn chat, cancellation, error handling, and airplane-mode operation.
- Measure memory use, startup time, and generation speed on the target phone.

### Milestone C — persistent character chat
- Bundle the approved initial character profiles and portraits.
- Character instructions are applied automatically.
- Multiple characters maintain separate conversation histories.
- Closing and reopening the app restores conversations correctly.

### Milestone D — model management
- Import a compatible GGUF from Android's document picker.
- Validate file access and model load before marking it usable.
- Switch between two verified compatible models.
- Confirm character profiles and chat records remain intact.
- Reject incompatible or failed models without damaging the active model or saved data.

A milestone is complete only after its stated tests pass. A successful Gradle build alone does not prove on-device inference works.

## 8. Known technical risks

- GGUF compatibility varies; model metadata, tokenizer, chat template, and llama.cpp support must be checked.
- An 8B quantized model may run on the target phone, but speed, memory pressure, thermal behavior, and context capacity must be measured on-device.
- Downloading arbitrary models is not equivalent to supporting all models. The app will maintain a tested compatibility list.
- Character consistency depends partly on the selected model. The app can preserve profile and chat data, but cannot guarantee identical output across models.
- Model licensing and distribution terms must be checked before bundling any model.

## 9. Development controls

- Work only on `rebuild/clean-nexora` for this rebuild.
- Keep `main` untouched.
- Do not merge or modify `main` without the user's explicit approval.
- Do not add features outside this contract without approval.
- Report build, automated test, and physical-device results separately.
- Never describe a feature as working until it has been built and tested.
