# Freaklog Android
A (partly vibecoded) fork from https://github.com/isaakhanimann/psychonautwiki-journal-android/

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

## Features added
- Multi-webhook Discord logging with per-webhook names, templates, enable/disable state and resend/edit/delete handling.
- FreakQuery support in webhook templates, including compact list rendering and a configurable compact separator.
- Optional Anodyne-style substance links in webhook output.
- AI chatbot powered by Google Generative AI for harm-reduction guidance.
- Tolerance calculator with an exponential decay model.
- Custom substances, custom routes of administration and custom units.
- Custom substance recipes.
- Inventory tab for substances on hand.
- Redose recommendation controls based on onset, come-up and peak timing.
- Home screen widgets, including timeline/heatmap style widget support.
- Improved timeline visualization and cumulative dose display.
- Statistics upgrades for substance usage, routes, dosage charts, trends and frequency.
- Spray calculator and volumetric dosing helpers.
- Reminders with quick logging actions.
- Optional app lock and haptic feedback settings.
- Import/export support for the expanded Freaklog data model.
