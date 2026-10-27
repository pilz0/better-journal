# Freaklog Android
A (partly vibecoded) fork from https://github.com/isaakhanimann/psychonautwiki-journal-android/

> [!WARNING]  
> This app is still in development and might break your database with updates

> [!WARNING]  
> This is a sloppy vibecoded fork and might have unknown issues

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

After changing any dependency or plugin version, regenerate `gradle.lock`:
```
nix run --inputs-from . gradle2nix -- -t assembleRelease -t bundleRelease
```

## Building with Gradle

```bash
# Run unit tests
./gradlew testDebugUnitTest --no-daemon

# Build unsigned APK
./gradlew assembleRelease --no-daemon

# Build unsigned AAB
./gradlew bundleRelease --no-daemon
```

## Test and CI coverage

Every push and pull request runs the three JVM test shards, Kover's coverage
gate, Detekt, debug lint, release unit tests and lint, APK/AAB builds through
both Gradle and Nix, Android test APK compilation, architecture checks, and
Nix checks including actionlint and ShellCheck. Release unit-test components
are enabled explicitly because AGP 9 creates only debug unit tests by default.

Six additional compatibility jobs run scheduling, export and substance parsing
tests on JDK 17/21 with German/Berlin, US/New York and Japanese/Tokyo JVM
defaults. Compilation still targets Java 17; the test launcher selects the
runtime explicitly. Reproduce a compatibility run with:

```bash
nix develop -c ./gradlew :app:testDebugUnitTest --no-daemon \
  -PtestJavaVersion=21 -PtestTimezone=Europe/Berlin -PtestLanguage=de -PtestCountry=DE \
  --tests 'foo.pilz.freaklog.scheduled.ReminderSchedule*' \
  --tests 'foo.pilz.freaklog.data.export.*' \
  --tests 'foo.pilz.freaklog.data.substances.parse.*'
```

The build also calls the emulator workflow on every API from 31 through 36. Migration
tests validate each schema step, the full upgrade chain, legacy reminder data,
and change-log triggers and retention. Emulator tests also run weekly; a
manual workflow dispatch can select one API. Each runtime job uploads its
reports. The final `CI passed` gate requires every dependency to succeed,
including emulator tests; a skipped dependency cannot produce a green gate.

New regression tests cover DST gaps and overlaps, half-hour offsets, every
weekday mask, interval/end-date boundaries, all 1,440 daily time slots,
encrypted-backup tampering and truncation, and filtered-export dependencies.

## AI agent docs and environment

- Agent instructions: `AGENTS.md` and `.github/copilot-instructions.md`, generated from `nix/ai/` (edit there, then `nix run .#ai-sync`)
- Agent environment: the flake devShell (`nix develop` or direnv); Copilot's cloud agent loads it in `.github/workflows/copilot-setup-steps.yml`

## Attribution
The original app was built by [https://github.com/isaakhanimann/](isaakhanimann) 

Some features and improvements (for example the whole achievement-code) were built for the fork on codeberg (https://codeberg.org/psychonaut-journal). Check out their fork if you are looking for a more stable, non-vibecoded fork.
