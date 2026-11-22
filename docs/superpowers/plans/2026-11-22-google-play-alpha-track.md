# Google Play Closed Testing (Alpha Track) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure the CI release workflow to upload Android App Bundles to both Google Play Internal and Closed Testing (Alpha) tracks simultaneously, with automated verification checks.

**Architecture:** Update the `upload-google-play` job in `.github/workflows/release.yml` to target `tracks: internal,alpha`, add an automated invariant check in `flake.nix` asserting that both tracks are configured, update AI/repo documentation in `nix/ai/reference.md`, and synchronize generated instruction files.

**Tech Stack:** GitHub Actions, YAML (`r0adkll/upload-google-play`), Nix Flakes (`actionlint`, checks), Shell / GNU grep.

---

### Task 1: Add automated invariant check in `flake.nix` for Google Play release tracks

**Files:**
- Modify: `flake.nix:254-268`

- [ ] **Step 1: Write the failing check in `flake.nix`**

Add `nix-google-play-release-tracks` check to `flake.nix` asserting that `.github/workflows/release.yml` specifies `tracks: internal,alpha`.

```nix
          nix-google-play-release-tracks = pkgs.runCommand "nix-google-play-release-tracks" { } ''
            set -eu
            ${pkgs.gnugrep}/bin/grep -q 'tracks: internal,alpha' ${./.github/workflows/release.yml} || {
              echo "Google Play upload step in .github/workflows/release.yml missing tracks: internal,alpha"
              exit 1
            }
            ${pkgs.coreutils}/bin/touch $out
          '';
```

- [ ] **Step 2: Run `nix flake check` to verify it fails**

Run: `nix flake check`
Expected: FAIL with "Google Play upload step in .github/workflows/release.yml missing tracks: internal,alpha"

- [ ] **Step 3: Commit the test check**

```bash
git add flake.nix && git commit -m "test: add nix check for google play release tracks"
```

---

### Task 2: Update `.github/workflows/release.yml` to deploy to `internal,alpha` tracks

**Files:**
- Modify: `.github/workflows/release.yml:396-425`

- [ ] **Step 1: Update `upload-google-play` in `.github/workflows/release.yml`**

Update the job and step names to mention both Internal & Alpha tracks, and update `tracks: internal` to `tracks: internal,alpha`.

```yaml
  upload-google-play:
    name: Upload to Google Play (Internal & Alpha tracks)
    needs: [preconditions, attest]
    if: needs.preconditions.outputs.should-release == 'true'
    runs-on: ubuntu-latest
    environment: internal-track
    permissions:
      contents: read
      actions: read
    steps:
      - name: Harden the runner
        uses: step-security/harden-runner@aebdcf6156ab6691adf63218240e476564a1ed4c # v2.23.0
        with:
          egress-policy: audit

      - name: Download signed AAB
        uses: actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333 # v8.0.2
        with:
          name: freaklog-aab
          path: app

      - name: Upload to Google Play (internal & alpha tracks)
        uses: r0adkll/upload-google-play@e738b9dd8f2476ea806d921b64aacd24f34515a5 # v1.1.5
        with:
          serviceAccountJsonPlainText: ${{ secrets.SERVICE_ACCOUNT_JSON }}
          packageName: foo.pilz.freaklog
          releaseFiles: app/freaklog.aab
          tracks: internal,alpha
```

- [ ] **Step 2: Run `nix flake check` to verify the check now passes**

Run: `nix flake check`
Expected: `nix-google-play-release-tracks` and `workflow-lint` pass.

- [ ] **Step 3: Commit the workflow change**

```bash
git add .github/workflows/release.yml && git commit -m "ci: publish releases to google play closed testing alpha track"
```

---

### Task 3: Update documentation and synchronize AI instructions

**Files:**
- Modify: `nix/ai/reference.md`
- Generated: `AGENTS.md`, `.github/copilot-instructions.md`

- [ ] **Step 1: Update `nix/ai/reference.md`**

Update references to `upload-google-play` to reflect the deployment to both Internal and Alpha tracks.

- [ ] **Step 2: Run `nix run .#ai-sync`**

Run: `nix run .#ai-sync`
Expected: `AGENTS.md` and `.github/copilot-instructions.md` are updated.

- [ ] **Step 3: Run `nix flake check`**

Run: `nix flake check`
Expected: All checks pass, including `ai-instructions-in-sync`.

- [ ] **Step 4: Commit documentation and regenerated instruction files**

```bash
git add nix/ai/reference.md AGENTS.md .github/copilot-instructions.md && git commit -m "docs: document google play alpha track in agent references"
```
