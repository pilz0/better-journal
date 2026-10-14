# Nix CI Checks and Builds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run Nix flake checks and release package builds (`.#apk`, `.#aab`) in GitHub Actions CI, archive built binaries as artifacts, and gate CI completion on their success.

**Architecture:** Add a new `nix-build` matrix job to `.github/workflows/build.yml` running on `ubuntu-latest` with Nix installed via `cachix/install-nix-action`. It builds `.#{apk,aab}` and uploads the unsigned artifacts. Update the `ci-passed` aggregator job to require `nix-build`. Update repository documentation to note the addition of Nix CI builds.

**Tech Stack:** GitHub Actions, Nix flakes, nixpkgs, gradle2nix.

---

### Task 1: Add `nix-build` job to `.github/workflows/build.yml` and update `ci-passed`

**Files:**
- Modify: `.github/workflows/build.yml:380-433`

- [ ] **Step 1: Add `nix-build` matrix job in `.github/workflows/build.yml`**
Add the `nix-build` job with `app-type: [ apk, aab ]`, `step-security/harden-runner`, `actions/checkout`, `cachix/install-nix-action`, `nix build .#${{ matrix.app-type }}`, and artifact upload using `actions/upload-artifact`.

- [ ] **Step 2: Update `ci-passed` dependencies in `.github/workflows/build.yml`**
Add `nix-build` to the `needs:` array of `ci-passed`.

- [ ] **Step 3: Verify workflow syntax and security invariants**
Run the nix check derivation for workflow security invariants:
`nix run --inputs-from . nixpkgs#python3 -- -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))"` or run `nix flake check` to verify invariants.

- [ ] **Step 4: Commit changes**
`git add .github/workflows/build.yml && git commit -m "ci: add nix-build matrix job and wire into ci-passed"`

---

### Task 2: Update documentation in `AGENTS.md`

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Update CI/CD workflow table and descriptions in `AGENTS.md`**
Update the documentation to mention the `nix-build` matrix job (building APK and AAB) alongside `nix-checks` in `build.yml`.

- [ ] **Step 2: Verify documentation formatting**
Ensure markdown tables and formatting remain clean and accurate.

- [ ] **Step 3: Commit changes**
`git add AGENTS.md && git commit -m "docs: document nix-build job in CI/CD section of AGENTS.md"`

---

### Task 3: Comprehensive Verification

**Files:**
- Read-only verification across `.github/workflows/build.yml`, `flake.nix`, `AGENTS.md`.

- [ ] **Step 1: Run Nix flake checks**
Run `nix flake check` to verify that `nix-workflow-security-invariants` and other flake checks pass.

- [ ] **Step 2: Verify git status and diff**
Ensure no unneeded files were modified and clean working tree.
