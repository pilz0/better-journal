# Copilot instructions for `pilz0/better-journal`

Start with `/home/runner/work/better-journal/better-journal/docs/ai-agents.md`, then use `AGENTS.md` as deep reference.

## Priorities

1. Keep edits minimal, scoped, and reversible.
2. Preserve security posture in workflows (`permissions: {}` default, pinned SHAs).
3. Validate with the smallest relevant checks first, then broader checks as needed.

## Quick commands

```bash
./gradlew testDebugUnitTest --no-daemon
./gradlew detekt --no-daemon
./gradlew :app:lintDebug --no-daemon
```

## Critical constraints

- Package namespace is `foo.pilz.freaklog`.
- AI chatbot code path is `data/ai/` custom Gemini REST implementation.
- If dependency/plugin versions change, regenerate `gradle.lock`:
  - `nix run --inputs-from . gradle2nix -- -t assembleRelease -t bundleRelease`
