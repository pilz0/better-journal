# Design Spec: Nix Flake Checks and Builds in CI

- **Date:** 2026-10-14
- **Topic:** Nix Checks and Builds in GitHub Actions CI
- **Status:** Approved

## 1. Context & Motivation

Freaklog supports building reproducible Android release packages (APK and AAB) via Nix (`flake.nix` + `gradle2nix`).
While local development allows developers to run:
- `nix flake check`
- `nix build .#apk`
- `nix build .#aab`

The GitHub Actions CI pipeline previously only executed `nix flake check` under the `nix-checks` job. The actual Nix package derivations (`.#apk` and `.#aab`) were not evaluated or built in CI. This meant that lockfile drift or Nix build sandbox incompatibilities could slip past CI unnoticed until a manual build or Hydra build was attempted.

The goal is to run both the Nix checks and the Nix package builds in GitHub Actions CI, gating the CI pipeline completion on their success, and uploading the resulting binaries as workflow artifacts.

## 2. Requirements & Goals

1. **Verify Invariants & Reproducibility:** Ensure `nix flake check` and `nix build` run in the CI pipeline for both `apk` and `aab` targets.
2. **Artifact Retention:** Archive the resulting unsigned APK and AAB from Nix builds as GitHub Actions artifacts (`freaklog-nix-apk`, `freaklog-nix-aab`).
3. **Supply Chain Security & Invariants:**
   - Adhere to the top-level `permissions: {}` deny-all configuration.
   - Use `step-security/harden-runner` with `egress-policy: audit`.
   - Pin third-party actions strictly by full commit SHA with version comments.
   - Pass the existing `nix-workflow-security-invariants` flake check.
4. **CI Pipeline Integration:** Include the new Nix build job in the `ci-passed` aggregator job requirements.

## 3. Detailed Architecture

### 3.1 `.github/workflows/build.yml`

#### Add `nix-build` Job
Add a dedicated matrix job:
- **Identifier:** `nix-build`
- **Name:** `Nix Build (${{ matrix.app-type }})`
- **Runs-on:** `ubuntu-latest`
- **Needs:** `[ wrapper-validation ]`
- **Permissions:** `contents: read`
- **Matrix:** `app-type: [ apk, aab ]`
- **Steps:**
  1. `step-security/harden-runner@ccd8616d44fd3846e67624a50d5aad6d37bf2d25 # v2.22.1`
  2. `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1`
  3. `cachix/install-nix-action@448cde739abec1eaf0646249e3d25bb8d9bc7702 # v31.11.2` with `extra_nix_config: extra-allowed-uris = http:// https://`
  4. Run `nix build .#${{ matrix.app-type }}`
  5. `actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2` with:
     - `name: freaklog-nix-${{ matrix.app-type }}`
     - `path: result/bin/*`
     - `if-no-files-found: error`

#### Update `ci-passed` Aggregator Job
Add `nix-build` to the `needs` array:
```yaml
needs: [ wrapper-validation, test, coverage, detekt, lint, build-app, architecture-and-hygiene, nix-checks, nix-build, build-test-fixtures ]
```

### 3.2 Documentation Updates
- Update `AGENTS.md` CI/CD section to document the `nix-checks` and `nix-build` jobs in `build.yml`.

## 4. Verification Plan

1. Verify workflow security invariants: run local flake check `nix flake check` or check `nix-workflow-security-invariants`.
2. Check YAML syntax of `.github/workflows/build.yml`.
3. Verify that `ci-passed` properly depends on `nix-build` and all actions have valid SHA hashes and comments.
