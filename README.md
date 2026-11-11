# Freaklog Android
A (partly vibecoded) fork from https://github.com/isaakhanimann/psychonautwiki-journal-android/

> [!WARNING]  
> This app is still in development and might break your database with updates

> [!WARNING]  
> This is a sloppy vibecoded fork and might have unknown issues

> [!WARNING]  
> migrating back to psylog/psychonautwiki journal might cause issues/requiere manual json edits because of custom roas

## AI assistant
Optional, off by default (Settings → AI assistant). It can search your journal and answer
harm-reduction questions using the bundled substance data and, if enabled, anodyne.wiki.
Bring your own provider: Google Gemini, OpenAI, Anthropic, OpenRouter, Mistral, Groq,
DeepSeek, or a self-hosted OpenAI- or Anthropic-compatible server such as Ollama, LM Studio,
llama.cpp or vLLM. API keys are stored encrypted on the device. Your messages and the
journal data the assistant looks up are sent to the provider you choose.

## Health Connect and Google Health Coach

Settings → **Health Connect & Google Health** lets you select medications, review a
snapshot of your own logged doses in the preceding 30 days, and refresh them into
Health Connect. Nothing is selected or exported by default. The export contains
self-reported FHIR R4 MedicationStatements, not prescriptions or a claim that you
are currently taking a medication. It preserves dose estimates and custom units
as text, excludes named consumers, future doses and journal notes, and uses stable
record IDs so refreshing updates edits and removes deleted, deselected or expired
doses. Refresh is manual; generate a new preview after journal changes. The
**Remove medication export** action deletes only Freaklog's dedicated Health Connect
source. Revoking write permission stops sharing; it does not remove previously shared data.

In Google Health, open Connections → Apps and services → Partner apps →
Health Connect, enable **Personal health records**, and permit reading medications
from Freaklog. Enable Coach and consent to its personalization separately.
[Google's medical-record setup guide](https://support.google.com/googlehealth/answer/16998660?hl=en)
and [supported Health Connect data](https://support.google.com/googlehealth/answer/14506680?hl=en)
describe the receiving side. Coach availability depends on Google's device,
subscription, age, account and regional requirements. Freaklog cannot verify that
Coach has ingested a particular record.

Medical-record export checks the device's Health Connect feature availability
before requesting `WRITE_MEDICAL_DATA`. On devices without this feature, use
**Copy summary for Coach** and paste the reviewed summary into a conversation, or
**Save FHIR medication file** for compatible tools. A JSON FHIR file is not a
document upload format promised by Google Health. Saved files and copied text
contain sensitive medication information. Removing the Health Connect source
does not remove copies another app already received.

The same screen has a separate, default-off **Share vitals with Freaklog AI**
setting. With your Health Connect permissions, the configured AI provider can
receive heart-rate minimum/maximum/mean, total sleep minutes and mean blood
pressure for the last 1–7 days when you ask. Missing permissions and absent
measurements are reported explicitly. This sends health information to the
provider selected in AI settings; switching this off cannot retract an existing
conversation. Timeline vitals and Health Connect blood-pressure logging also
remain available in the journal.

Implementation follows Android's [medical-record write guide](https://developer.android.com/health-and-fitness/health-connect/medical-records/write-data)
and [supported FHIR data format](https://developer.android.com/health-and-fitness/health-connect/medical-records/data-format).
Distributors must declare the new medical-data write permission and its use case
in the Google Play Console before releasing through Play.

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

Pull-request build, Android emulator, dependency review and CodeQL jobs use the
explicit Ubuntu 24.04 runner label to avoid changes to the moving latest image.

The GitHub repository must have its dependency graph enabled for the Dependency
Review workflow. Enabling vulnerability alerts also enables the dependency graph;
this does not require enabling automatic dependency-update PRs.

An optional local emulator environment is available with `nix develop .#android-test`.
It includes the locked SDK, emulator and available x86_64 Google APIs system images.
The default shell keeps these larger downloads optional. On an x86_64 Linux host
with KVM, create an API 36 AVD with `avdmanager`, start `emulator -avd <name>`,
then run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=foo.pilz.freaklog.data.health.MedicationHealthConnectIntegrationTest \
  --no-daemon
```

The medication acceptance test uses synthetic records in an isolated Health Connect
source and deletes that source afterward. It requires API 36+ with the personal
health record feature available; an unavailable feature produces a skipped test,
which does not establish platform acceptance.

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
