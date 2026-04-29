# Security policy

## Supported versions

Only the most recent release on the [GitHub releases page](../../releases)
and the current default branch are supported. Older versions do not receive
security fixes.

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues
or pull requests.**

Instead, use GitHub's private vulnerability reporting:

1. Open a new private vulnerability advisory via the **Security** tab of this
   repository on GitHub (`Security → Advisories → Report a vulnerability`).
2. Fill in a clear description of the issue, including reproduction steps,
   the affected component (e.g. webhook, AI chatbot, Room database, signing,
   release pipeline), the version where the issue was discovered, and the
   impact you believe it has.

You should receive an acknowledgement within 7 days. If you do not, please
escalate by emailing the repository owner directly (see the GitHub profile
of the owner listed in [`CODEOWNERS`](.github/CODEOWNERS)).

We aim to provide an initial assessment within 14 days and a fix or
mitigation within 90 days, depending on severity.

## Supply-chain security

This repository follows several supply-chain hardening practices that are
also relevant to security researchers:

- Every third-party GitHub Action is pinned to a full commit SHA (see the
  workflows under `.github/workflows/`). Tag references are forbidden.
- Pull-request builds **never** receive secrets. Signing, Cloudflare R2
  uploads, Google Play uploads, and GitHub release creation all live in
  `.github/workflows/release.yml`, which only runs from successful builds
  on the default branch or tagged commits and is gated through GitHub
  Environments.
- Build artifacts are signed and an SLSA build provenance attestation is
  generated via `actions/attest-build-provenance`.
- The repository runs:
  - **CodeQL** for `java-kotlin` and the `actions` workflow language.
  - **OSV-Scanner** against the dependency graph.
  - **OpenSSF Scorecard** for repository-level supply-chain hygiene.
  - **gitleaks** for accidental secret commits.
  - **Detekt** + **Android Lint** with SARIF uploaded to code scanning.
  - **Dependency Review** on every pull request.
  - **`step-security/harden-runner`** on every job.
- Dependabot is enabled for `gradle` and `github-actions` ecosystems and
  must be re-pinned to a SHA whenever it bumps an action.

## Scope

In-scope:

- The application code under `app/src/`.
- The CI/CD pipelines under `.github/workflows/`.
- The build configuration (Gradle, Detekt, lint).

Out of scope:

- Vulnerabilities in third-party services the app talks to (e.g. Discord,
  Google Generative AI). Please report those upstream.
- Issues that require physical access to an unlocked, rooted device.
- Self-XSS or social-engineering scenarios.
