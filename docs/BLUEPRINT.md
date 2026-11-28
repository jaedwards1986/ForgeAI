# NEXORA — Product Blueprint (Draft 1)

## 1. Product purpose

Build a private Android entertainment app for adults. It should provide varied, interactive experiences without depending on AI chat, hosted services, or a permanent internet connection.

The app is a tool for exploring fictional adult-themed stories and organizing media the owner chooses to add. All characters and scenarios are fictional adults.

## 2. First-release experience

The home screen has four clear destinations:

1. **Stories** — choose a story, make decisions, and follow branching paths with multiple endings.
2. **Scenario Studio** — combine available characters, settings, moods, and story premises to create a fresh scenario from locally stored templates.
3. **My Library** — browse personal images and videos imported by the user, with favorites and collections.
4. **Saved** — return to bookmarked stories, scenarios, and media.

A Settings area provides privacy, appearance, storage, and content controls.

## 3. Design direction

- Visual style: dark, polished, private, and cinematic; restrained accent color rather than a cluttered neon look.
- Layout: simple bottom navigation with large, clear entry points.
- Home: a featured story, a quick-start scenario tile, and recently opened items.
- Story reader: readable text, optional artwork, clear choices, and an always-visible save/continue action.
- Scenario Studio: straightforward selectors and a preview before starting.
- Library: grid/list toggle, folders or collections, favorites, and playback/viewing controls.
- Accessibility: scalable text, strong contrast, clear touch targets, and reduced-motion option.

The interface must remain usable on a phone screen and must not require accounts or network access.

## 4. Functional requirements

### Interactive stories
- Stories are stored locally as structured content.
- Choices lead to defined branches and endings.
- Save progress and resume later.
- Browse by theme and completion status.
- The app ships with sample, non-explicit placeholder story content until the user approves the content plan and supplies or approves media.

### Scenario Studio
- Build scenarios from locally bundled templates and user-editable choices.
- Randomize compatible combinations for variety.
- Allow reroll, edit, favorite, and save.
- Do not claim AI-generated prose: the first version uses authored templates and modular text.

### Personal media library
- Import images and videos selected by the user.
- Organize into collections and favorites.
- Support slideshow and video playback using Android-supported formats.
- Keep imported media on-device; do not upload or transmit it.
- Provide delete and storage-management controls.

### Privacy and offline operation
- No account, advertising SDK, subscription, analytics service, or remote inference.
- No server dependency for core features.
- Optional app PIN gate.
- Store app data locally and use Android's supported private app storage.
- Explain clearly that uninstalling the app may remove locally stored data unless the user exports or backs it up.

## 5. Technical approach

- Android application, with a web-based HTML/CSS/JavaScript interface packaged inside an Android WebView if that remains the simplest maintainable approach.
- Kotlin handles Android integration, local file access, app lock, and media playback.
- Local structured data stores story definitions, progress, scenario templates, favorites, and settings.
- Use Android's system file picker for media import and avoid broad storage permissions.
- No backend service is part of the design.

## 6. First build milestone

The first milestone is a real installable debug APK with:
- Home and navigation.
- A working story reader with branching choices.
- Scenario Studio using local templates and randomization.
- A local media library that can import and display user-selected files.
- Favorites and saved progress.
- Settings and an optional PIN gate.
- Verified airplane-mode operation for all core features.

## 7. Acceptance checks

The milestone is not considered complete until:
- The app installs and launches on the target Android phone.
- Navigation and all primary screens work.
- Story choices reach the expected branches and endings.
- Story progress and favorites survive closing and reopening the app.
- Scenario combinations change when rerolled and remain locally available.
- Imported media can be viewed or played and remains available after restart.
- Core features work in airplane mode.
- No remote server is contacted for ordinary app use.

## 8. Explicit exclusions from the first release

- No chatbot or local LLM.
- No remote server, user account, cloud sync, or paid API.
- No scraping or automatic downloading of adult material.
- No bundled third-party pornographic media without clear rights and approval.
- No claim that a static template generator is an AI.

## 9. Decisions to confirm during design

These are design choices, not blockers to creating the first prototype:
- Final app color palette and visual style.
- Whether the primary home emphasis is Stories, Scenario Studio, or My Library.
- The initial story themes and how much authored content to include.
- Whether PIN protection is enabled by default.
- Supported media formats and whether export/backup is included in the first release.

## 10. Delivery rule

Work on the rebuild branch. Keep the original main branch untouched. Build and test each milestone before describing it as working. Do not merge to main without the user's explicit approval.
