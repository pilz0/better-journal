# Freaklog Android
A (partly vibecoded) fork from https://github.com/isaakhanimann/psychonautwiki-journal-android/

## FreakQuery

This project includes the `:android-freakquery` module, a Kotlin/Android port of the FreakQuery DSL.

Use `FreakQueryRepository` when queries should run against the app's Room `experiences_db` data:

```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val freakQueryRepository: FreakQueryRepository
) : ViewModel() {
    val lastDose = freakQueryRepository.queryFlow("last|dose")
}
```

`FreakQueryRepository` reads `ExperienceRepository`, flattens `Experience` + `Ingestion` rows into FreakQuery logs, and supports `query`, `queryFlow`, `render`, and `renderFlow`.

> [!WARNING]  
> This app is still in development and might break your database with updates

> [!WARNING]  
> migrating back to psylog/psychonautwiki journal might cause issues/requiere manual json edits because of custom roas

## Building with nix
```
nix build .#apk
```
The unsigned apk should be under `result/bin/app-release-unsigned.apk`
```
nix build .#aab
```
The unsigned app bundle should be under `result/bin/`

## Building with Gradle

```bash
# Run unit tests
./gradlew testDebugUnitTest --no-daemon

# Build unsigned APK
./gradlew assembleRelease --no-daemon

# Build unsigned AAB
./gradlew bundleRelease --no-daemon
```

## features added
- Discord webhook integration for logging ingestions
- AI chatbot (Google Generative AI) for harm-reduction guidance
- Tolerance calculator with exponential decay model
- Custom substance recipes
- Home screen widget (Glance AppWidget)
- Improved timeline visualization
- Spray calculator for volumetric dosing
