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
| AI | Google Generative AI SDK |
| Serialization | kotlinx.serialization (JSON) |
| Build | Gradle (Kotlin DSL), AGP |
| CI | GitHub Actions |

## Project structure

```
app/src/main/java/foo/pilz/freaklog/
├── MainActivity.kt             # Entry point, sets up NavGraph + theme
├── data/
│   ├── ai/                     # AI chatbot (Google Generative AI)
│   │   ├── AiChatbotRepository.kt
│   │   └── AiChatViewModel.kt
│   ├── export/                 # JSON export/import serializers
│   │   ├── InstantSerializer.kt
│   │   └── JournalExport.kt    # @Serializable data classes for export
│   ├── room/                   # Room database
│   │   ├── AppDatabase.kt      # Database, version, auto-migrations
│   │   ├── SprayDao.kt / SprayRepository.kt
│   │   ├── experiences/
│   │   │   ├── ExperienceDao.kt
│   │   │   ├── ExperienceRepository.kt
│   │   │   ├── CustomRecipeDao.kt / CustomRecipeRepository.kt
│   │   │   ├── entities/       # Experience, Ingestion, CustomUnit, Spray, …
│   │   │   └── relations/      # ExperienceWithIngestions, IngestionWithCompanion, …
│   │   └── reminders/
│   │       ├── ReminderDao.kt
│   │       ├── RemindersRepository.kt
│   │       └── entities/Reminder.kt
│   ├── substances/             # Substance data (parsed from assets/Substances.json)
│   │   ├── AdministrationRoute.kt   # Enum of routes (ORAL, INSUFFLATED, …)
│   │   ├── classes/            # Substance, Roa, RoaDose, RoaDuration, Tolerance, …
│   │   ├── parse/              # SubstanceParser (JSON → Kotlin models)
│   │   └── repositories/       # SubstanceRepository, SearchRepository
│   └── webhook/
│       └── WebhookService.kt   # Discord webhook integration
├── di/
│   ├── AppModule.kt            # Provides DB, DAOs, DataStore, CoroutineScope
│   ├── JournalApplication.kt   # @HiltAndroidApp Application class
│   └── RepositoryModule.kt     # Binds SubstanceParser/Repository interfaces
├── scheduled/
│   ├── NotificationScheduler.kt
│   └── ReminderReceiver.kt     # BroadcastReceiver for scheduled reminders
└── ui/
    ├── Constants.kt
    ├── HeatmapWidgetProvider.kt # Glance AppWidget for heatmap
    ├── WidgetProvider.kt        # Glance AppWidget (recent ingestions)
    ├── main/                   # MainScreen, bottom navigation scaffold
    ├── tabs/
    │   ├── journal/            # Journal tab (list, calendar, timeline, edit)
    │   │   ├── JournalViewModel.kt
    │   │   ├── calendar/       # CalendarJournalScreen + DayViewModel
    │   │   ├── allingestions/  # AllIngestionsScreen
    │   │   ├── components/     # ExperienceRow, RelativeDateText, …
    │   │   └── experience/
    │   │       ├── edit/       # EditExperienceScreen + ViewModel
    │   │       ├── recommendations/ # AiChatBottomSheet
    │   │       └── timeline/   # Timeline rendering (drawables, screen, utils)
    │   ├── safer/              # Harm-reduction screens
    │   │   ├── tolerance/      # ToleranceCalculator, ToleranceTextParser, Screen
    │   │   ├── spray/          # SprayCalculatorScreen + ViewModel
    │   │   ├── DoseGuideScreen.kt
    │   │   ├── ReagentTestingScreen.kt
    │   │   └── VolumetricDosingScreen.kt
    │   ├── search/             # Substance search + detail screens
    │   ├── settings/           # Settings, webhook, export, reminders, …
    │   │   ├── combinations/   # UserPreferences (DataStore)
    │   │   ├── customunits/    # Custom unit management
    │   │   ├── customrecipes/  # Custom recipe management
    │   │   ├── reminders/      # Reminder management
    │   │   └── colors/         # Color preference screens
    │   └── stats/              # Statistics tab
    ├── theme/                  # Color, typography, theme (Material3)
    └── utils/
        ├── getDateFromString.kt
        ├── getInteractionExplanationURLForSubstance.kt
        ├── getTimeDifferenceText.kt
        ├── HapticFeedbackManager.kt
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

Current schema version: **15**. All migrations from v1 to v15 are handled by `@AutoMigration`.

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

**Relations** (in `experiences/relations/`):

- `ExperienceWithIngestions`
- `ExperienceWithIngestionsAndCompanions`
- `ExperienceWithIngestionsCompanionsAndRatings`
- `ExperienceWithIngestionsTimedNotesAndRatings`
- `IngestionWithCompanion`, `IngestionWithExperienceAndCustomUnit`
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
- Template-based message formatting: `{user}: [{dose} {units} ]{substance} via {route}`
- Optional blocks in `[…]` are omitted when any enclosed placeholder is empty
- Exponential-backoff retry (up to 3 attempts)
- `sendWebhook` / `editWebhook` / `deleteWebhookMessage`

### AI chatbot (`data/ai/`)

`AiChatbotRepository` builds a harm-reduction context prompt from the current experience's ingestions and recent history, then initialises a `GenerativeModel` (Gemini) with the API key stored in `UserPreferences`.  
The API key and model name are user-configurable in Settings → AI.  
`AiChatViewModel` manages the conversation state as a `StateFlow<List<ChatMessage>>`.

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
| Settings | `ui/tabs/settings/` | Preferences, webhook, export/import, reminders |

### Timeline rendering

The timeline is drawn on a `Canvas` using a set of `*Timeline` drawable classes under `ui/tabs/journal/experience/timeline/drawables/timelines/`. Each class handles a different combination of known duration phases:
- `TotalTimeline`, `OnsetTimeline`, `OnsetComeupTimeline`, `OnsetComeupPeakTimeline`, `OnsetComeupPeakTotalTimeline`, etc.

### Tolerance calculator (`ui/tabs/safer/tolerance/`)

`ToleranceTextParser` converts PsychonautWiki tolerance text (e.g. `"1-3 days"`, `"almost immediately"`) to float days. `ToleranceCalculator` applies an exponential-decay model to the ingestion history: `level = Σ exp(-0.693 * elapsed / halfLife)`, capped at 1.

### Widgets (`ui/`)

Two Glance AppWidgets:
- `WidgetProvider` — shows the most recent ingestion
- `HeatmapWidgetProvider` — shows an ingestion heatmap calendar

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
  (`EntityBuilders.experience(...)`, `EntityBuilders.ingestion(...)`).
  Reuse them rather than constructing entities by hand.
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
- `@Keep` annotation on `AdministrationRoute` is a workaround for an AGP/R8 bug stripping enum metadata used in Navigation Compose serialized routes (see issue tracker link in the source file)
