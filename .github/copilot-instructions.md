<!-- Generated from nix/ai/ by `nix run .#ai-sync`. Do not edit by hand. -->

# Copilot instructions for `pilz0/better-journal`

Read this, then use `AGENTS.md` as the deep reference.

## Environment

The toolchain (JDK 17, Android SDK, ripgrep, jq) comes from the flake's devShell. Enter it with
`direnv allow` (see `.envrc`) or `nix develop`, or run single commands as `nix develop -c <cmd>`.
Don't install JDKs or SDKs by hand. If a tool is missing, add it to `devShells.default` in `flake.nix`.

## Agent instructions are generated

`AGENTS.md` and `.github/copilot-instructions.md` are generated from `nix/ai/*.md` by `nix/ai/default.nix`.
**Don't edit them directly.** Edit the sources, then run `nix run .#ai-sync`.
`nix flake check` (CI job `nix-checks`) fails when the committed files are out of date.

## Working rules

1. Keep edits minimal, scoped, and reversible.
2. Find relevant code with `rg` before editing.
3. Run the narrowest relevant checks first, then broader ones.
4. Preserve workflow security posture: `permissions: {}` by default, actions pinned to full commit SHAs, `step-security/harden-runner` first in every job.

## Fast file map

- App entry + nav: `app/src/main/java/foo/pilz/freaklog/MainActivity.kt`
- Room DB: `app/src/main/java/foo/pilz/freaklog/data/room/AppDatabase.kt`
- Substance parsing: `app/src/main/java/foo/pilz/freaklog/data/substances/`
- AI assistant: `app/src/main/java/foo/pilz/freaklog/data/ai/` (settings UI in `ui/tabs/settings/ai/`)
- UI tabs: `app/src/main/java/foo/pilz/freaklog/ui/tabs/`
- Schedules/reminders: `app/src/main/java/foo/pilz/freaklog/scheduled/`
- CI/workflows: `.github/workflows/`
- Nix build, checks, devShell: `flake.nix`, `nix/`

## Command map

```bash
# Unit tests (full, then the CI shards)
./gradlew testDebugUnitTest --no-daemon
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.*" --no-daemon
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.*" --no-daemon
./gradlew testDebugUnitTest -PtestExclude=foo.pilz.freaklog.data.*,foo.pilz.freaklog.ui.* --no-daemon

# Static analysis
./gradlew detekt --no-daemon
./gradlew :app:lintDebug --no-daemon

# Coverage
./gradlew :app:koverHtmlReportDebug :app:koverXmlReportDebug :app:koverVerifyDebug --no-daemon

# Nix: invariant checks, regenerate agent instructions, regenerate gradle.lock after dependency changes
nix flake check
nix run .#ai-sync
nix run --inputs-from . gradle2nix -- -t assembleRelease -t bundleRelease
```

## High-impact invariants

- Android package namespace is `foo.pilz.freaklog` (not upstream `com.isaakhanimann.journal`).
- Room schema version is `27` with an `@AutoMigration` chain in `AppDatabase.kt`.
- The AI assistant uses its own raw-HTTP clients for the OpenAI, Anthropic and Gemini APIs in `data/ai/provider/`. Don't add provider SDKs (the archived `generative-ai-android` drops Gemini's `thoughtSignature`), and keep the raw `NativeContent` round-trip intact.
- AI API keys are only stored encrypted (`AiProfileStore` + `AiKeyCipher`); never write them to plain preferences, logs or exports.
- If dependencies or plugins change, regenerate `gradle.lock` (command above) or the Nix build breaks.

## Task-oriented guide

- **UI change:** read the screen, its ViewModel, and its route in `MainActivity.kt`.
- **DB/entity change:** update the entity, DAO, `AppDatabase.kt` version/migrations, and tests.
- **Workflow/CI change:** keep permissions minimal. Only change action pins for explicit upgrades.
- **AI feature change:** provider protocol work goes in `data/ai/provider/` (one client per API standard, tested against MockWebServer); new tools go in `AiTools`/`AiSubstanceTools` with a label in `AiToolLabels` and a line in the system prompt in `AiChatbotRepository`.
- **Toolchain/environment change:** edit `flake.nix` (devShell). Copilot's cloud agent uses the same shell via `.github/workflows/copilot-setup-steps.yml`.
