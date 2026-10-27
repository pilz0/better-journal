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

## AI agent docs and environment

- Agent instructions: `AGENTS.md` and `.github/copilot-instructions.md`, generated from `nix/ai/` (edit there, then `nix run .#ai-sync`)
- Agent environment: the flake devShell (`nix develop` or direnv); Copilot's cloud agent loads it in `.github/workflows/copilot-setup-steps.yml`

## Attribution
The original app was built by [https://github.com/isaakhanimann/](isaakhanimann) 

Some features and improvements (for example the whole achievement-code) were built for the fork on codeberg (https://codeberg.org/psychonaut-journal). Check out their fork if you are looking for a more stable, non-vibecoded fork.
