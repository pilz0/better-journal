# AI Agent Guide — Freaklog Android

## Repository overview

Freaklog is an Android journal app (fork of [PsychonautWiki Journal](https://github.com/isaakhanimann/psychonautwiki-journal-android)) that lets users log and track substance ingestions, view timelines, calculate tolerance, and interact with an AI chatbot. Package name: `foo.pilz.freaklog`.

## Key technologies

| Layer | Tech |
|-------|------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt (Dagger) |
| DB | Room (SQLite) |
| Nav | Navigation Compose |
| Async | Kotlin Coroutines + Flow |
| AI | Custom Gemini REST client (`v1beta` `generateContent`) — see [AI chatbot](#ai-chatbot-dataai) |
| Serialization | kotlinx.serialization (JSON) |
| Build | Gradle (Kotlin DSL), AGP |
| CI | GitHub Actions |

## Project structure

```
app/src/main/java/foo/pilz/freaklog/
├── MainActivity.kt             # Entry point, sets up NavGraph + theme
├── data/
│   ├── ai/                     # AI chatbot (custom Gemini REST client)
│   │   ├── AiChatbotRepository.kt   # Builds system prompt + creates GeminiChatSession
│   │   ├── AiChatViewModel.kt       # StateFlow<List<ChatMessage>> conversation state
│   │   ├── AiDateFormatter.kt       # Locale-stable date helpers used in prompts
│   │   ├── AiTools.kt               # Function-calling tool declarations + dispatcher
│   │   ├── GeminiChatSession.kt     # Stateful chat wrapper (history kept as raw JSON)
│   │   └── GeminiRestClient.kt      # v1beta generateContent HTTP client
│   ├── export/                 # JSON export/import serializers
│   │   ├── InstantSerializer.kt
│   │   └── JournalExport.kt    # @Serializable data classes for export
│   ├── room/                   # Room database
│   │   ├── AppDatabase.kt      # Database, version, auto-migrations + AutoMigrationSpec
│   │   ├── SprayDao.kt / SprayRepository.kt
│   │   ├── experiences/
│   │   │   ├── ExperienceDao.kt
│   │   │   ├── ExperienceRepository.kt
│   │   │   ├── CustomRecipeDao.kt / CustomRecipeRepository.kt
│   │   │   ├── entities/       # Experience, Ingestion, CustomUnit, Spray, …
│   │   │   └── relations/      # ExperienceWithIngestions, IngestionWithCompanion, …
│   │   ├── inventory/          # Stash / inventory tracking
│   │   │   ├── InventoryDao.kt
│   │   │   ├── InventoryItem.kt
│   │   │   └── InventoryRepository.kt
│   │   └── reminders/
│   │       ├── ReminderDao.kt
│   │       ├── RemindersRepository.kt
│   │       └── entities/Reminder.kt
│   ├── substances/             # Substance data (parsed from assets/Substances.json)
│   │   ├── AdministrationRoute.kt   # Enum of routes (ORAL, INSUFFLATED, …)
│   │   ├── classes/            # Substance, Roa, RoaDose, RoaDuration, Tolerance, …
│   │   │   └── roa/curve/      # IngestionCurve / BatemanCurve projection model
│   │   ├── parse/              # SubstanceParser (JSON → Kotlin models)
│   │   └── repositories/       # SubstanceRepository, SearchRepository
│   └── webhook/
│       └── WebhookService.kt   # Discord webhook integration
├── di/
│   ├── AppModule.kt            # Provides DB, DAOs, DataStore, @ApplicationScope CoroutineScope
│   ├── JournalApplication.kt   # @HiltAndroidApp Application class
│   └── RepositoryModule.kt     # Binds SubstanceParser/Repository interfaces
├── scheduled/
│   ├── NotificationScheduler.kt    # Wraps AlarmManager, schedules next reminder fire
│   ├── ReminderSchedule.kt         # Pure helper: next-fire math (interval / times-of-day)
│   ├── ReminderReceiver.kt         # BroadcastReceiver fired by AlarmManager
│   ├── ReminderActionReceiver.kt   # Handles notification action buttons (snooze/dismiss/log)
│   └── BootCompletedReceiver.kt    # Re-arms all enabled reminders after device reboot
└── ui/
    ├── Constants.kt
    ├── HeatmapWidgetProvider.kt # Glance AppWidget for heatmap
    ├── WidgetHeatmapModel.kt    # Pure model used by the heatmap widget renderer
    ├── WidgetProvider.kt        # Glance AppWidget (recent ingestions)
    ├── WidgetTimelineModel.kt   # Pure model used by the timeline widget renderer
    ├── main/                   # MainScreen, bottom navigation scaffold + navigation graphs
    ├── tabs/
    │   ├── journal/            # Journal tab (list, calendar, timeline, edit)
    │   │   ├── JournalViewModel.kt
    │   │   ├── calendar/       # CalendarJournalScreen + DayViewModel
    │   │   ├── allingestions/  # AllIngestionsScreen
    │   │   ├── components/     # ExperienceRow, RelativeDateText, …
    │   │   └── experience/
    │   │       ├── edit/       # EditExperienceScreen + ViewModel
    │   │       ├── recommendations/ # AiChatBottomSheet
    │   │       └── timeline/   # Timeline rendering (drawables, curve/, screen, utils)
    │   ├── inventory/          # Inventory tab (optional — gated by Settings flag)
    │   │   ├── InventoryScreen.kt
    │   │   └── InventoryViewModel.kt
    │   ├── safer/              # Harm-reduction screens
    │   │   ├── tolerance/      # ToleranceCalculator, ToleranceTextParser, Screen
    │   │   ├── spray/          # SprayCalculatorScreen + ViewModel
    │   │   ├── DoseGuideScreen.kt / DoseExplanationScreen.kt
    │   │   ├── ReagentTestingScreen.kt / DrugTestingScreen.kt
    │   │   ├── RouteExplanationScreen.kt
    │   │   ├── SaferHallucinogensScreen.kt
    │   │   ├── SaferUseScreen.kt
    │   │   └── VolumetricDosingScreen.kt
    │   ├── search/             # Substance search + detail screens
    │   ├── settings/           # Settings, webhook, export, reminders, …
    │   │   ├── combinations/   # UserPreferences (DataStore)
    │   │   ├── customunits/    # Custom unit management
    │   │   ├── customrecipes/  # Custom recipe management
    │   │   ├── reminders/      # Reminder management
    │   │   ├── colors/         # Color preference screens
    │   │   ├── lock/           # App-lock (biometrics / PIN) settings
    │   │   └── funny/          # Easter-egg screens
    │   └── stats/              # Statistics tab
    ├── theme/                  # Color, typography, theme (Material3)
    └── utils/
        ├── getDateFromString.kt
        ├── getInteractionExplanationURLForSubstance.kt
        ├── getTimeDifferenceText.kt
        ├── HapticFeedbackManager.kt / HapticProvider.kt
        └── keyboard/
```

## Build commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease --no-daemon

# Build release AAB (unsigned)  — output: app/build/outputs/bundle/release/app-release.aab
./gradlew bundleRelease --no-daemon

# Run all unit tests
./gradlew testDebugUnitTest --no-daemon

# Run tests by package (mirrors the CI matrix shards)
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.*" --no-daemon
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.*" --no-daemon
./gradlew testDebugUnitTest -PtestExclude=foo.pilz.freaklog.data.*,foo.pilz.freaklog.ui.* --no-daemon

# Static analysis (Detekt) and coverage (Kover) — see "Static analysis & coverage" below.
./gradlew detekt --no-daemon
./gradlew :app:koverHtmlReportDebug :app:koverXmlReportDebug :app:koverVerifyDebug --no-daemon
```

> **Important:** The Gradle AAB output is `app-release.aab`, **not** `app-release-unsigned.aab`.
> The Nix build is broken due to JitPack dependencies (compose-markdown).

## CI/CD (GitHub Actions)

Workflows live in `.github/workflows/`:

| Workflow file | Purpose |
|---------------|---------|
| `build.yml` | Wrapper validation, unit tests (3 shards), Detekt, Android Lint, Kover coverage gate, unsigned APK + AAB builds. Runs on every push/PR. |
| `release.yml` | **Secret-handling.** Sign + Cloudflare R2 + Google Play + GitHub release + SLSA build provenance. Triggered by `workflow_run` on a successful `Build and test` run on `main` / tags, or `workflow_dispatch`. Pull-request events never reach this workflow. |
| `codeql.yml` | CodeQL analysis for `java-kotlin` and `actions` languages with the `security-extended` query pack. |
| `instrumented-tests.yml` | Connected Android tests on emulators (API 31/34/35). Triggered by the `run-instrumented-tests` PR label, `workflow_dispatch`, or nightly schedule. |
| `scorecard.yml` | OpenSSF Scorecard supply-chain analysis (uploaded as SARIF + published to scorecard.dev). |
| `dependency-review.yml` | Blocks PRs that introduce known-vulnerable or AGPL dependencies. |

```
push/PR  ──► build.yml
              ├─ wrapper-validation
              ├─ test  (matrix: data | ui | misc)
              ├─ coverage  (Kover, hard-gated minimum)
              ├─ detekt    (SARIF → code scanning)
              ├─ lint      (SARIF → code scanning)
              ├─ build-app (matrix: apk | aab — unsigned)
              └─ ci-passed (aggregator status check)

main/tag ──► build.yml succeeds ──workflow_run──► release.yml
                                                    ├─ sign           (env: signing)
                                                    ├─ attest         (SLSA provenance)
                                                    ├─ upload-s3      (env: production)
                                                    ├─ upload-google-play (env: internal-track)
                                                    └─ create-release (tags only, env: production)
```

### Permissions & supply-chain model

- Every workflow declares `permissions: {}` (deny-all) at the top, with each
  job granting the minimum it needs.
- Every third-party action is pinned to a **full commit SHA** with the
  human-readable tag in a comment. Dependabot will edit these in place.
- Every job starts with **`step-security/harden-runner`** (audit mode).
- The Gradle wrapper is validated on every PR.
- Secrets only exist in `release.yml` and are scoped to GitHub Environments
  (`signing`, `internal-track`, `production`). PR builds cannot read them.
- Build artifacts are signed and an **SLSA build provenance** attestation is
  produced via `actions/attest-build-provenance`.

### Required secrets for CI

| Secret | Purpose |
|--------|---------|
| `KEYSTORE` | Base64-encoded `.jks` keystore file |
| `SIGNING_KEY_ALIAS` | Key alias inside the keystore |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_PASSWORD` | Key password |
| `R2_ACCOUNT_ID` | Cloudflare R2 account ID |
| `R2_ACCESS_KEY_ID` | Cloudflare R2 access key |
| `R2_SECRET_ACCESS_KEY` | Cloudflare R2 secret key |
| `R2_BUCKET` | Cloudflare R2 bucket name |
| `SERVICE_ACCOUNT_JSON` | Google Play service account JSON (plain text) |
| `GOOGLE_AI_API_KEY` | (optional) Google AI API key for the chatbot feature |

## Package naming

App package: `foo.pilz.freaklog`  
The upstream project uses `com.isaakhanimann.journal`. When porting upstream code, convert **all** package references to `foo.pilz.freaklog`.

## Dependency management

All library versions live in `gradle/libs.versions.toml` (Gradle Version Catalog).  
Plugin versions that can't use the catalog are pinned inline in the root `build.gradle.kts`:
- Hilt: `id("com.google.dagger.hilt.android") version "2.54"`
- KSP: `id("com.google.devtools.ksp") version "2.0.21-1.0.27"`
- Room Gradle plugin: `id("androidx.room") version libs.versions.roomRuntime`

To upgrade: edit version strings in `libs.versions.toml` (and root `build.gradle.kts` for the inline pins above). The `nl.littlerobots.version-catalog-update` plugin is present to assist with automated upgrades.

## Data layer

### Room database (`data/room/AppDatabase.kt`)

Current schema version: **17**. All migrations from v1 to v17 are handled by `@AutoMigration`.
The `16 → 17` step uses an `AutoMigrationSpec` (`AppDatabase.ReminderV16To17`) that
back-fills `scheduleType = 'INTERVAL'` on legacy reminder rows so they don't silently
disable when the new column default (`DAILY_AT_TIMES`) is applied without `timesOfDay`.

There is no schema version 13 — the migration jumps directly from 12 to 14
(`AutoMigration(from = 12, to = 14)`).

**Entities:**

| Entity | Description |
|--------|-------------|
| `Experience` | Top-level journal entry with title, date, notes |
| `Ingestion` | A single substance dose within an experience |
| `SubstanceCompanion` | Persisted colour assignment per substance name |
| `CustomSubstance` | User-defined substance not in PsychonautWiki |
| `CustomUnit` | Custom unit definition (e.g. "one tab = 100 µg") |
| `ShulginRating` | Shulgin + / ++ / +++ scale rating entry |
| `TimedNote` | Timestamped note within an experience |
| `Spray` | Spray bottle definition for the spray calculator |
| `Reminder` | Scheduled substance reminder |
| `CustomRecipe` | A named recipe (mix of substances) |
| `CustomRecipeComponent` | One ingredient line within a recipe |
| `InventoryItem` | Stash / inventory entry tracked by the Inventory tab |

**Relations** (in `experiences/relations/`):

- `ExperienceWithIngestions`
- `ExperienceWithIngestionsAndCompanions`
- `ExperienceWithIngestionsCompanionsAndRatings`
- `ExperienceWithIngestionsTimedNotesAndRatings`
- `IngestionWithCompanion`, `IngestionWithCompanionAndCustomUnit`, `IngestionWithExperienceAndCustomUnit`
- `CustomUnitWithIngestions`, `CustomRecipeWithComponents`

**Time storage:** All `Instant` values are stored as epoch milliseconds (Long) via `InstantConverter` and serialized via `InstantSerializer` (reads as Double for backwards-compat, writes as Long).

### Substance data (`data/substances/`)

Substance data is parsed from `app/src/main/assets/Substances.json` at startup by `SubstanceRepository`. The JSON is parsed into:

```
SubstanceFile
  ├── List<Category>       — psychedelic, stimulant, depressant, …
  └── List<Substance>
        ├── name, commonNames, url, isApproved
        ├── tolerance: Tolerance(full, half, zero)  — text descriptions
        ├── crossTolerances: List<String>
        ├── interactions: Interactions(dangerous, unsafe, uncertain)
        └── roas: List<Roa>
              ├── route: AdministrationRoute
              ├── roaDose: RoaDose(units, lightMin, commonMin, strongMin, heavyMin)
              ├── roaDuration: RoaDuration(onset, comeup, peak, offset, total, afterglow)
              └── bioavailability: Bioavailability(min, max)
```

`SearchRepository` provides fuzzy substance name search.

### Webhook (`data/webhook/WebhookService.kt`)

Sends Discord webhook messages on ingestion log. Supports:
- Template-based message formatting: `{user}: [{dose} {units} ]{substance} via {route}[ at {site}][\n> {note}]`
- Optional blocks in `[…]` are omitted when any enclosed placeholder is empty
- Exponential-backoff retry (up to 3 attempts)
- `sendWebhook` / `editWebhook` / `deleteWebhookMessage`
- `sendWebhook` and `editWebhook` return `WebhookResult(success, messageId, error)`; `deleteWebhookMessage` returns `Boolean` and handles failures internally

**Message ID storage:** Each `Ingestion` entity has a nullable `webhookMessageId` field that stores the Discord message ID. This ID enables editing and deleting webhook messages when ingestions are modified or removed.

**Custom unit handling:** When sending or editing webhooks, custom units are automatically converted to their base units. For example, if a user logs "1 tab" and the custom unit defines "1 tab = 100 µg LSD", the webhook will display "100 µg LSD".

**Per-ingestion webhook toggle:** Users can disable webhook notifications for individual ingestions during creation via a toggle in `FinishIngestionScreen`. The toggle only appears when a webhook URL is configured in Settings. Defaults to enabled for backward compatibility.

**Webhook operations:**
- **Send** (`FinishIngestionScreenViewModel.sendWebhookForIngestion`): Creates new Discord message on ingestion creation, stores message ID
- **Edit** (`EditIngestionViewModel.editWebhookForIngestion`): Updates existing Discord message when ingestion is modified, preserves message ID
- **Delete** (`EditIngestionViewModel.deleteWebhookForIngestion`): Removes Discord message when ingestion is deleted
- **Resend** (`EditIngestionViewModel.resendWebhook`): Sends a new webhook message and updates the stored message ID

### AI chatbot (`data/ai/`)

The AI chatbot does **not** use the archived `generative-ai-android` SDK. That SDK
(v0.9.0) strips the `thoughtSignature` field that Gemini 2.5 / 3-series models attach
to every `functionCall` part; omitting it on the next turn causes the API to return
HTTP 400 ("Function call is missing a thought_signature"). Instead the app talks to
Gemini directly:

| File | Role |
|------|------|
| `GeminiRestClient.kt` | Minimal HTTP client for `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`. Posts a raw `JSONObject`, returns a raw `JSONObject`, runs on `Dispatchers.IO`. |
| `GeminiChatSession.kt` | Stateful chat wrapper. Stores conversation history as raw JSON `Part`s so `thoughtSignature` (and any other server-only fields) survive round-trips. |
| `AiTools.kt` | Function-calling tool declarations the model may invoke (e.g. journal lookups). Also dispatches tool calls back to the right repository. |
| `AiDateFormatter.kt` | Locale/TZ-stable date strings used inside the prompt and tool results. |
| `AiChatbotRepository.kt` | Builds the harm-reduction system instruction from the current experience + recent history and constructs a fresh `GeminiChatSession` per call so settings changes (API key, model name) take effect immediately. |
| `AiChatViewModel.kt` | Exposes the conversation as a `StateFlow<List<ChatMessage>>` to the UI (`AiChatBottomSheet`). |

The API key and model name are user-configurable in Settings → AI and persisted via
`UserPreferences` (DataStore).

### Export / Import (`data/export/`)

`JournalExport` is a `@Serializable` data class that holds the full journal state. Serialized to / from JSON using kotlinx.serialization. `InstantSerializer` stores `Instant` as epoch milliseconds.

## UI layer

### Navigation

Routes and the `NavHost` are defined in `MainActivity.kt`. All screens are reachable from the bottom navigation bar (Journal, Search, Safer Use, Stats, Settings).

### Bottom navigation tabs

| Tab | Package | Description |
|-----|---------|-------------|
| Journal | `ui/tabs/journal/` | Experience list, calendar view, timeline, add/edit |
| Search | `ui/tabs/search/` | Substance search and detail pages |
| Safer Use | `ui/tabs/safer/` | Harm-reduction tools (tolerance, dosing, reagent tests) |
| Stats | `ui/tabs/stats/` | Usage statistics and charts |
| Inventory | `ui/tabs/inventory/` | Stash tracking — **optional**, hidden unless enabled in Settings |
| Settings | `ui/tabs/settings/` | Preferences, webhook, export/import, reminders, AI, lock |

Tabs are conditionally shown by `MainScreen` based on `MainViewModel` flags
(`activateSaferFlow`, `isStatsHiddenFlow`, `isInventoryEnabledFlow`, drug-list flag).

### Timeline rendering

The timeline is drawn on a `Canvas` using a set of `*Timeline` drawable classes under `ui/tabs/journal/experience/timeline/drawables/timelines/`. Each class handles a different combination of known duration phases:
- `TotalTimeline`, `OnsetTimeline`, `OnsetComeupTimeline`, `OnsetComeupPeakTimeline`, `OnsetComeupPeakTotalTimeline`, etc.

There is also a **Bateman-curve** rendering path used on the ingestion-logging
screen: `RoaDuration.toIngestionCurve()` produces an `IngestionCurve`
(`BatemanCurve`) which is rendered by `IngestionCurveDrawable`. `isCertain`
controls solid-vs-dotted strokes. `AllTimelinesModel` / `GroupDrawable` accept a
`useBatemanCurve` flag (default `false`); only the dose-pick screen passes
`true`, every other timeline surface keeps the legacy trapezoid rendering.

### Tolerance calculator (`ui/tabs/safer/tolerance/`)

`ToleranceTextParser` converts PsychonautWiki tolerance text (e.g. `"1-3 days"`, `"almost immediately"`) to float days. `ToleranceCalculator` applies an exponential-decay model to the ingestion history: `level = Σ exp(-0.693 * elapsed / halfLife)`, capped at 1.

### Widgets (`ui/`)

Two Glance AppWidgets:
- `WidgetProvider` — shows the most recent ingestion
- `HeatmapWidgetProvider` — shows an ingestion heatmap calendar

### Scheduled reminders (`scheduled/`)

Reminder firing is split between pure logic and Android plumbing:

- `ReminderSchedule.kt` — pure helper that computes the next fire time from a
  `Reminder` entity (interval-based or `DAILY_AT_TIMES`). Easy to unit-test on
  the JVM.
- `NotificationScheduler.kt` — wraps `AlarmManager`; calls `ReminderSchedule`
  and registers/cancels exact alarms.
- `ReminderReceiver.kt` — `BroadcastReceiver` fired by the alarm; posts the
  notification and re-arms the next occurrence.
- `ReminderActionReceiver.kt` — handles notification action buttons (snooze,
  dismiss, log ingestion).
- `BootCompletedReceiver.kt` — re-arms all enabled reminders after device
  reboot. Reminders are also re-armed on `MainActivity` launch as a safety
  net.

`@ApplicationScope` (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`)
from `AppModule` is used for background work that must outlive a UI scope
(e.g. reminder rearm and webhook send-after-dismiss).

## Dependency injection (Hilt)

`@HiltAndroidApp` is applied in `JournalApplication`.

| Module | Location | Provides |
|--------|----------|---------|
| `AppModule` | `di/AppModule.kt` | `AppDatabase`, all DAOs, `DataStore<Preferences>`, `@ApplicationScope CoroutineScope` |
| `RepositoryModule` | `di/RepositoryModule.kt` | Binds `SubstanceParserInterface`, `SubstanceRepositoryInterface`, `SearchRepositoryInterface` |

All ViewModels use `@HiltViewModel` + `@Inject constructor`. Repositories and DAOs are `@Singleton`.

## Testing conventions

- JVM unit tests: `app/src/test/java/foo/pilz/freaklog/` — run on the host JVM
  (optionally with Robolectric for tests that need a real Android runtime).
- Android instrumented tests: `app/src/androidTest/java/foo/pilz/freaklog/` —
  run on a device/emulator via `connectedDebugAndroidTest` (Robolectric is
  not used here; these need real Android).
- Frameworks available:
  - **JUnit 4** (`@Test`, `assertEquals`, `assertNull`, `assertTrue`, …) — primary.
  - **AssertK** (`assertk.assertThat(...).isEqualTo(...)`) — preferred for new code.
  - **MockK** + **MockK-android** for Kotlin-idiomatic mocks (suspend, top-level).
  - **Turbine** for `Flow`/`StateFlow` assertions.
  - **kotlinx-coroutines-test** (`runTest`) for coroutine-driven tests.
  - **Robolectric** when a real Android runtime is needed on the JVM.
  - **MockWebServer** for HTTP testing.
  - **androidx.room:room-testing** + `MigrationTestHelper` for Room schema tests.
- JSON parsing tests use `org.json:json` from `testImplementation`.
- Test fixture builders live in `app/src/test/java/foo/pilz/freaklog/testing/`
  (`EntityBuilders.experience(...)`, `EntityBuilders.ingestion(...)`, plus
  `RoomInMemoryExampleTest` showing the in-memory Room pattern). Reuse them
  rather than constructing entities by hand.
- Test JVMs run with **`-Duser.timezone=UTC`** and **`-Duser.language=en`**
  (set in `app/build.gradle.kts`); never assume the host TZ/locale.
- CI splits tests into 3 shards: `data.*`, `ui.*`, and **everything else** (via
  `-PtestExclude` to exclude the first two patterns) — see `.github/workflows/build.yml`.
- The legacy `isReturnDefaultValues = true` shim is **still on** for backwards
  compatibility, but new tests that touch Android SDK classes should
  `@RunWith(RobolectricTestRunner::class)` instead.
- Room migration tests live in
  `app/src/androidTest/java/foo/pilz/freaklog/data/room/AppDatabaseMigrationTest.kt`
  and exercise every registered `AutoMigration`. When you add a new schema
  version, also add the corresponding `from -> to` pair in `migrationPairs`.

### Static analysis & coverage

- **Detekt**: `./gradlew detekt`. Config in `config/detekt/detekt.yml`,
  baseline in `config/detekt/baseline.xml`. SARIF uploaded to code scanning.
- **Android Lint**: `./gradlew :app:lintDebug`. `checkDependencies = true`.
  SARIF uploaded to code scanning. `abortOnError = false` while we're
  cleaning up legacy issues; flip to `true` once clean.
- **Kover (coverage)**: `./gradlew :app:koverHtmlReportDebug
  :app:koverXmlReportDebug :app:koverVerifyDebug`. CI fails if coverage
  drops below the floor configured in `app/build.gradle.kts`. Ratchet
  upward as the suite grows.

### Test file locations

| Source file | Test file |
|-------------|-----------|
| `data/substances/classes/roa/RoaDose.kt` | `data/substances/classes/roa/RoaDoseTest.kt` |
| `data/substances/classes/roa/DurationRange.kt` | `data/substances/classes/roa/DurationRangeTest.kt` |
| `data/substances/classes/Substance.kt` | `data/substances/classes/SubstanceClassTest.kt` |
| `data/webhook/WebhookService.kt` | `data/webhook/WebhookServiceTest.kt` |
| `data/export/InstantSerializer.kt` | `data/export/InstantSerializerTest.kt` |
| `ui/utils/getTimeDifferenceText.kt` | `ui/utils/GetTimeDifferenceTextTest.kt` |
| `ui/utils/getInteractionExplanationURLForSubstance.kt` | `ui/utils/InteractionUrlTest.kt` |
| `ui/tabs/safer/tolerance/ToleranceCalculator.kt` | `ui/tabs/safer/tolerance/ToleranceCalculatorTest.kt` |
| `ui/tabs/safer/tolerance/ToleranceTextParser.kt` | `ui/tabs/safer/tolerance/ToleranceTextParserTest.kt` |

## Common patterns

### Adding a new screen

1. Create a `@Composable` function in `ui/tabs/<tab>/`
2. Add a named route in the `NavHost` in `MainActivity.kt`
3. Create a `@HiltViewModel` ViewModel with `@Inject constructor`
4. Inject DAOs/repositories via the ViewModel constructor
5. Collect `StateFlow`/`Flow` with `collectAsStateWithLifecycle()`

### Adding a new Room entity

1. Create the entity data class annotated with `@Entity`, `@PrimaryKey`
2. Add it to the `entities` list in `AppDatabase.kt` and bump `version`
3. Room can auto-migrate simple additions: add `AutoMigration(from = N, to = N+1)` if no `@RenameColumn`/`@DeleteColumn` spec is needed; otherwise write a manual `Migration` object
4. Create a DAO interface annotated with `@Dao`
5. Create a repository class annotated with `@Singleton @Inject constructor`
6. Add a `@Provides` binding in `AppModule.kt` if it is a new DAO

### Adding a new webhook template variable

1. Add the key–value pair to the `values` map in `WebhookService.sendWebhookWithRetry()`
2. Document it in the template hint shown in `WebhookSettingsScreen.kt`

## Performance notes

- **R8 full mode** (`android.enableR8.fullMode=true` in `gradle.properties`) — more aggressive dead-code elimination and inlining in release builds
- **Gradle configuration cache** (`org.gradle.configuration-cache=true`) — avoids re-running configuration phase on unchanged builds
- **Resource shrinking** (`isShrinkResources = true`) and **minification** (`isMinifyEnabled = true`) are on for release
- **`ndk.debugSymbolLevel = "FULL"`** generates full native symbols for crash reporting
- **Core library desugaring** (`isCoreLibraryDesugaringEnabled = true`) enables `java.time.*` APIs on API 31+

## Known issues / limitations

- Nix build is broken due to JitPack dependencies (`compose-markdown` from JitPack has no Nix derivation)
- AAB files from Gradle are named `app-release.aab` (NOT `app-release-unsigned.aab`) — the release workflow reflects this
- The AI chatbot requires a Google AI (Gemini) API key configured by the user in Settings
- There is no schema version 13 — the migration jumps directly from 12 to 14 (`AutoMigration(from = 12, to = 14)`)
- The `webhookMessageId` field in the `Ingestion` entity stores Discord message IDs for webhook operations (send, edit, delete)
- `@Keep` annotation on `AdministrationRoute` is a workaround for an AGP/R8 bug stripping enum metadata used in Navigation Compose serialized routes (see issue tracker link in the source file)
- `WebhookService.editWebhook` uses HTTP `PATCH`, which works on Android's okhttp-backed `HttpURLConnection` but throws `ProtocolException` on the JDK's plain `HttpURLConnection`; therefore `editWebhook` cannot be exercised by a plain JVM unit test. HTTP-level webhook tests use **MockWebServer** (`com.squareup.okhttp3:mockwebserver`).
- The `generative-ai-android` SDK is intentionally **not** used — see [AI chatbot](#ai-chatbot-dataai). Use `GeminiRestClient` / `GeminiChatSession` instead so `thoughtSignature` is preserved.
- `JournalExport` includes webhook configuration (full Discord webhook URLs) in plain text — treat exported journal files as sensitive credentials.
