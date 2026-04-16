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
| Build | Gradle (Kotlin DSL), AGP |
| CI | GitHub Actions |

## Project structure

```
app/src/main/java/foo/pilz/freaklog/
├── MainActivity.kt          # Entry point, sets up NavGraph + theme
├── data/
│   ├── ai/                  # AI chatbot (Google Generative AI)
│   ├── export/              # JSON export/import serializers
│   ├── room/                # Room database, DAOs, entities, repositories
│   │   ├── AppDatabase.kt
│   │   ├── experiences/     # Experience, Ingestion, CustomUnit, etc.
│   │   └── reminders/       # Reminder entities + DAO
│   ├── substances/          # Substance data model + JSON parser
│   │   ├── classes/         # Substance, Roa, Tolerance, Interactions, …
│   │   ├── parse/           # SubstanceParser (JSON → Kotlin models)
│   │   └── repositories/    # SubstanceRepository, SearchRepository
│   └── webhook/             # Discord webhook integration
├── di/                      # Hilt modules (AppModule, DatabaseModule, …)
├── scheduled/               # WorkManager background tasks
└── ui/
    ├── main/                # MainScreen, bottom navigation
    ├── tabs/
    │   ├── journal/         # Journal tab: experience list, timeline, edit
    │   ├── safer/           # Harm reduction: tolerance, reagent tests, etc.
    │   ├── search/          # Substance search + detail screens
    │   └── settings/        # Settings screens
    ├── theme/               # Color, typography, theme
    └── utils/               # Date helpers, haptics, URL utilities
```

## Build commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease --no-daemon

# Build release AAB (unsigned)
./gradlew bundleRelease --no-daemon

# Run unit tests
./gradlew testDebugUnitTest --no-daemon

# Run specific test package
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.*" --no-daemon
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.*" --no-daemon
```

## CI/CD (GitHub Actions)

Workflow file: `.github/workflows/build.yml`

| Job | Trigger | Description |
|-----|---------|-------------|
| `test` (matrix: data/ui/misc) | push/PR | Run unit tests in parallel by package |
| `build-app` (matrix: apk/aab) | push/PR | Build unsigned APK and AAB |
| `sign` (matrix: apk/aab) | main branch / tags | Sign with release keystore |
| `upload-s3` | main branch | Upload to Cloudflare R2 |
| `upload-google-play` | main branch | Upload AAB to Google Play internal track |
| `create-release` | tags | Create GitHub release with artifacts |

### Required secrets for CI

- `KEYSTORE` – Base64-encoded keystore file
- `SIGNING_KEY_ALIAS` – Key alias in keystore
- `SIGNING_STORE_PASSWORD` – Keystore password
- `SIGNING_KEY_PASSWORD` – Key password
- `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` – Cloudflare R2
- `SERVICE_ACCOUNT_JSON` – Google Play service account
- `GOOGLE_AI_API_KEY` – (optional) AI chatbot feature

## Package naming

App package: `foo.pilz.freaklog`  
The upstream project uses `com.isaakhanimann.journal`. All package refs are converted to `foo.pilz.freaklog`.

## Dependency management

Dependencies are managed via Gradle Version Catalog (`gradle/libs.versions.toml`).  
To upgrade dependencies, edit the version strings in that file and the corresponding entries in `build.gradle.kts` (root).

## Database

- Room database with schema version tracking. Schemas are stored in `app/schemas/`.
- When adding a new column or table, **always** add a migration in `AppDatabase.kt`.
- Schema export is configured via `room { schemaDirectory("$projectDir/schemas") }`.

## Testing conventions

- Unit tests live in `app/src/test/java/foo/pilz/freaklog/`
- Mirror the main source tree structure (same sub-packages)
- Use JUnit 4 (`@Test`, `assertEquals`, `assertNull`, etc.)
- No Mockito or other mocking framework is currently used — prefer pure unit tests with plain data classes
- For testing JSON parsing, include `org.json:json` in `testImplementation` (already present)
- Android-specific tests (needing a device/emulator) go in `app/src/androidTest/`

## Common patterns

### Adding a new screen
1. Create a Composable in `ui/tabs/<tab>/`
2. Add a route in the NavGraph in `MainActivity.kt` (or the relevant NavHost)
3. Create a ViewModel with `@HiltViewModel` + `@Inject constructor`
4. Add any new DAOs/repositories as Hilt-injected singletons

### Adding a new Room entity
1. Create the entity data class with `@Entity`, `@PrimaryKey`
2. Add it to `AppDatabase.kt` `entities` list and bump `version`
3. Add a migration in `AppDatabase.kt`
4. Create DAO + Repository
5. Register in the relevant Hilt module in `di/`

### Performance notes
- R8 full mode is enabled (`android.enableR8.fullMode=true`)
- Gradle configuration cache is enabled
- Minification + resource shrinking are on for release builds
- `ndk.debugSymbolLevel = "FULL"` is set for release crash reporting

## Known issues / limitations
- Nix build is broken due to JitPack dependencies (compose-markdown)
- AAB files from Gradle are named `app-release.aab` (NOT `app-release-unsigned.aab`)
- The AI chatbot requires a valid Google AI API key set at runtime
