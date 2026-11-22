# Google Play Closed Testing (Alpha Track) CI Deployment Design

## 1. Goal
Configure Freaklog's CI/CD release workflow (`.github/workflows/release.yml`) to publish releases to the Google Play **Closed testing (Alpha)** track in addition to the existing **Internal** track.

## 2. Background & Requirements
- Currently, Freaklog's `release.yml` workflow uploads signed Android App Bundles (`freaklog.aab`) to Google Play's internal track on every successful push to `main` and on tag releases via `r0adkll/upload-google-play`.
- Google Play supports closed testing tracks, with the default closed track named `alpha`.
- The user requested that CI should also push to the Closed testing - Alpha track.
- Per user confirmation, builds will deploy to both `internal` and `alpha` tracks simultaneously during release runs.

## 3. Architecture & Design

### 3.1 GitHub Workflow (`.github/workflows/release.yml`)
Under the `upload-google-play` job:
- Update job name to `Upload to Google Play (Internal & Alpha tracks)`.
- Update step name to `Upload to Google Play (internal & alpha tracks)`.
- Change `tracks: internal` to `tracks: internal,alpha`.
- Maintain existing inputs:
  - `serviceAccountJsonPlainText: ${{ secrets.SERVICE_ACCOUNT_JSON }}`
  - `packageName: foo.pilz.freaklog`
  - `releaseFiles: app/freaklog.aab`
  - `environment: internal-track` (reuses existing secret scoping without needing GitHub repo environment reconfiguration).

### 3.2 Documentation & Instruction Sync
- Update `nix/ai/reference.md` to document the dual Internal + Alpha track target.
- Run `nix run .#ai-sync` to propagate updates to `AGENTS.md` and `.github/copilot-instructions.md`.

## 4. Verification & Testing
1. Validate workflow syntax and indentation.
2. Run `nix flake check` to ensure `ai-sync` outputs match source documentation and all flake checks pass.
3. Verify git diff to ensure no unintended files or formatting regressions are introduced.
