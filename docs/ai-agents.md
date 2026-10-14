# AI agents: quickstart + working rules

This file is the compact, high-signal entrypoint for modern coding agents.
Use it before reading the full `AGENTS.md`.

## 1) First-pass workflow

1. Read this file, then `AGENTS.md`.
2. Find relevant code with `rg`/`glob` before editing.
3. Make small, scoped edits.
4. Run the narrowest relevant checks first, then broader checks.
5. Keep security/supply-chain guardrails intact (pinned actions, least permissions).

## 2) Fast file map

- App entry + nav: `app/src/main/java/foo/pilz/freaklog/MainActivity.kt`
- Data layer:
  - Room DB: `app/src/main/java/foo/pilz/freaklog/data/room/AppDatabase.kt`
  - Substance parsing: `app/src/main/java/foo/pilz/freaklog/data/substances/`
  - AI chatbot: `app/src/main/java/foo/pilz/freaklog/data/ai/`
- UI tabs: `app/src/main/java/foo/pilz/freaklog/ui/tabs/`
- Schedules/reminders: `app/src/main/java/foo/pilz/freaklog/scheduled/`
- CI/workflows: `/home/runner/work/better-journal/better-journal/.github/workflows/`

## 3) Command map

```bash
# Unit tests (full)
./gradlew testDebugUnitTest --no-daemon

# Unit tests (same partitioning as CI)
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.*" --no-daemon
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.*" --no-daemon
./gradlew testDebugUnitTest -PtestExclude=foo.pilz.freaklog.data.*,foo.pilz.freaklog.ui.* --no-daemon

# Static analysis
./gradlew detekt --no-daemon
./gradlew :app:lintDebug --no-daemon

# Coverage
./gradlew :app:koverHtmlReportDebug :app:koverXmlReportDebug :app:koverVerifyDebug --no-daemon

# Build artifacts
./gradlew assembleRelease --no-daemon
./gradlew bundleRelease --no-daemon
```

## 4) High-impact invariants

- Android package namespace is `foo.pilz.freaklog` (not upstream `com.isaakhanimann.journal`).
- Room schema version is `27` with `@AutoMigration` chain in `AppDatabase.kt`.
- AI chat uses the custom Gemini REST stack in `data/ai/`; do not switch to archived `generative-ai-android`.
- Preserve workflow security posture: `permissions: {}` default + pinned action SHAs.
- If dependencies/plugins change, regenerate `gradle.lock`:
  - `nix run --inputs-from . gradle2nix -- -t assembleRelease -t bundleRelease`

## 5) Task-oriented guide

- **UI change:** read relevant screen + ViewModel + route in `MainActivity.kt`.
- **DB/entity change:** update entity + DAO + `AppDatabase.kt` version/migrations + tests.
- **Workflow/CI change:** keep permissions minimal and action pins immutable except explicit upgrades.
- **AI feature change:** update `AiChatbotRepository`, `GeminiChatSession`, and `AiTools` consistently.

## 6) Deep reference

For full architecture, migrations, exported data coverage, CI topology, and porting notes, read:

- `/home/runner/work/better-journal/better-journal/AGENTS.md`
