# CI/CD architecture

QuantumMPV is a single-module Android app (`:app`, Kotlin, Jetpack Compose, AGP 9.x,
Gradle wrapper 9.6, JDK 17, compileSdk 37, targetSdk 36, minSdk 26) with native
libmpv/FFmpeg AARs fetched from pinned, checksummed GitHub releases
(`native-artifacts.json`). It ships three product flavors (`standard`, `noVulkan`,
`fongmi`) and three build types (`debug`, `preview`, `release`). Releases are APKs
published on GitHub, not AABs.

The pipeline has three stages:

1. **Pull request validation.** Every change must pass build, test, lint, static
   analysis, security and configuration checks before it can merge.
2. **Main branch validation.** The same workflows run on every push to `master`.
   Each master commit gets its own concurrency group, so a burst of merges never
   cancels or drops a master run and every merged commit has a complete result.
   Release workflows depend on this.
3. **Release validation.** Tag-triggered workflows re-check the tagged commit,
   build from the exact tag, run release-only checks, sign in an isolated job and
   publish with checksums, an SBOM and build provenance.

## Workflows

| Workflow | File | Trigger | Purpose | Merge / release gate |
| --- | --- | --- | --- | --- |
| Android CI | `ci.yml` | PR, push to master, manual | Gradle wrapper validation, native AAR checksum verification, QuantumMPV regression scripts, `lintStandardDebug`, `testStandardDebugUnitTest`, `assembleStandardDebugAndroidTest`, `assembleDebug` (all flavors), `lintStandardRelease`, APK native-content verification, debug APK and reports upload | **Required for merge** (`Lint, test, and build debug APK`) and for release |
| Static analysis | `static-analysis.yml` | PR, push to master, manual | ktlint (with baseline) and detekt (checksum-verified CLI) | **Required for merge** (`ktlint + detekt`) and for release |
| Instrumented tests | `instrumented-tests.yml` | PR, manual, called by `release.yml` | `connectedStandardDebugAndroidTest` on API 30 and API 34 emulators (Room migrations, subtitle font) | Recommended required for merge; **runs inside every stable release** |
| Security analysis | `security.yml` | PR, push to master, weekly, manual | `tools/check_android_security.py`: manifest, exported components, permissions, FileProvider, network security config, backup rules, debuggable/minify build types, committed keystores, insecure source patterns | Recommended required for merge; **required for release** (`Android security policy`) |
| CodeQL | `codeql.yml` | PR, push to master, weekly, manual | SAST for java-kotlin (manual Gradle build), c-cpp, python and actions; results in the Security tab | Recommended required for merge (`Analyze (java-kotlin)`); **required for stable release** |
| Secret scan | `gitleaks.yml` | PR, push to master, weekly, manual | Gitleaks over the commit range (PR/push) or full history (schedule) | Recommended required for merge; **required for release** (`Detect leaked secrets`) |
| Dependency review | `dependency-review.yml` | PR to master | Blocks newly added dependencies with high/critical advisories; advisory license report against an AGPL-compatible allow-list | Recommended required for merge |
| Workflow audit | `workflow-audit.yml` | PR, push to master, weekly, manual | actionlint (checksum-verified) with shellcheck, zizmor, Dependabot and workflow JSON-schema validation, JSON config validation, version-floor checks | Recommended required for merge |
| Dependency Submission | `dependency-submission.yml` | push to master, manual | Submits the resolved Gradle graph so Dependabot alerts, dependency review and the SBOM include Gradle dependencies | Not a gate |
| CI/CD Build | `build.yml` | push to master, manual | Builds unsigned release APKs of the selected flavors and uploads them for 14 days | Not a gate |
| Preview Build | `preview.yml` | push to master (app paths), manual | Signed preview APKs published to GitHub Pages and `latest.json` for the preview update channel | Not a gate |
| Prepare Release | `prepare-release.yml` | manual | Verifies master is prepared for a version, verifies its checks, creates the tag through the API and dispatches the release workflow | Release entry point |
| Make release | `release.yml` | `v1.X.Y` tag push, manual | Stable release pipeline (see below) | Release |
| Make Pre Release | `pre-release.yml` | `v1.X.Y-preview.N` tag push, manual | Tagged preview release | Release |
| Check upstream release | `upstream-sync-check.yml` | daily, manual | Opens a review PR (or issue on conflict) for new mpvRx upstream releases | Not a gate |

GitHub-managed features complement these workflows: Dependabot version and
security updates (`.github/dependabot.yml`), Dependabot alerts, secret scanning
with push protection, and private vulnerability reporting.

### Why there is no separate `pr-validation.yml`

`ci.yml` and `static-analysis.yml` are the PR validation workflows and also run on
`master`. A separate PR-only file would run the same 30-minute Gradle build twice,
and their job names are already the required status checks in branch protection.
Renaming them would silently disable merge protection until the rule was updated.

## Stable release pipeline (`release.yml`)

```
resolve ──► instrumented (API 30, 34) ──┐
   └──────► build (unsigned, no secrets) ┴─► sign (environment: production) ─► publish (contents: write)
```

| Job | Permissions | Does |
| --- | --- | --- |
| `resolve` | `contents: read`, `checks: read` | Validates `v1.MINOR.PATCH`, resolves the tag to a commit, requires that commit to be on `master`, requires `versionName` and the first `CHANGELOG.md` heading to match, and waits for the required checks on that commit (`tools/verify_commit_checks.py`) |
| `instrumented` | `contents: read` | Calls `instrumented-tests.yml` on the tagged commit |
| `build` | `contents: read` | Builds the three release flavors without a Gradle cache, checks application ID, `versionName`, the expected `versionCode` per ABI and that no APK is debuggable, verifies native contents, uploads unsigned APKs (1-day retention) |
| `sign` | none, `production` environment | Downloads the unsigned APKs, signs them with `apksigner`, verifies signatures, checks that all APKs share one certificate and that it matches `vars.RELEASE_SIGNING_CERT_SHA256` when set, uploads signed APKs (1-day retention). No Gradle or repository code runs here. |
| `publish` | `contents: write`, `id-token: write`, `attestations: write` | Re-verifies native contents, writes `SHA256SUMS`, exports the SPDX SBOM from the dependency graph, creates build provenance attestations and creates the GitHub release |

Published assets: `QuantumMPV-{universal,arm64-v8a,armeabi-v7a,x86,x86_64}-vX.Y.Z.apk`,
`QuantumMPV-fongmi-vX.Y.Z.apk`, `QuantumMPV-no-vulkan-vX.Y.Z.apk`,
`QuantumMPV-vX.Y.Z-SHA256SUMS.txt` and `QuantumMPV-vX.Y.Z-sbom.spdx.json`. The in-app
updater only considers assets that start with `QuantumMPV-` and end with `.apk`.
It reads checksums from the asset digest or the release notes, so the extra files
do not affect it.

ABI strategy: the build config enables ABI splits for `arm64-v8a`, `armeabi-v7a`,
`x86` and `x86_64` plus a universal APK for `standard`. `noVulkan` and `fongmi` are
universal-only (`universalOnlyDistributions`). The pipeline publishes exactly
those outputs and fails if any is missing.

## Security hardening applied to all workflows

- Top-level `permissions` default to read-only (`contents: read` or `{}`). Write
  scopes are granted per job only where needed.
- Every third-party action is pinned to a full commit SHA that was checked against
  the upstream tag. Dependabot keeps the pins current.
- `actions/checkout` uses `persist-credentials: false`, except in
  `upstream-sync-check.yml`, which needs the credential to push a review branch
  (documented in `.github/zizmor.yml`).
- No workflow uses `pull_request_target`. PR workflows from forks run with a
  read-only token and no secrets.
- User-controlled values (`inputs.*`, tag names) reach shell scripts only through
  environment variables and are validated before use.
- Signing secrets are referenced only in the step that signs. In `release.yml`
  they are confined to the `sign` job, which runs no build tooling.
- Release jobs do not restore the Gradle cache.
- Downloaded tools (actionlint, detekt, native AARs) are checksum-verified. Python
  tooling is installed with `pip --require-hashes`.
- Every job has `timeout-minutes`. PR runs cancel superseded runs of the same
  PR. Push, schedule and release runs are grouped per commit or tag, so they are
  never cancelled by later runs.
- Release and preview signing jobs use the `production` / `preview`
  environments. Configure their deployment policies (docs/RELEASE.md) so that
  only tags (and master, for preview) can obtain the signing secrets.
- Stable signing runs in a job without Gradle. Preview builds (`preview.yml`,
  `pre-release.yml`) still sign in the build job with the same key, because
  preview and stable share the application ID; splitting them the same way is
  a recommended follow-up.

## Severity policy

| Severity | Examples | Pull request | Release |
| --- | --- | --- | --- |
| Critical | Debuggable release, trust-all TLS, committed keystore, leaked secret | Blocks | Blocks |
| High | Exported provider, user CAs in release, high/critical dependency advisory, CodeQL error, zizmor high | Blocks | Blocks |
| Medium | New exported component or permission without review, mutable PendingIntent, zizmor medium | Blocks | Blocks |
| Low | Stale allowlist entry | Annotation only | Annotation only |
| Informational | Accepted, documented exceptions such as global cleartext | Annotation only | Annotation only |

Exceptions are documented, not silently suppressed:

- Android policy exceptions go in `config/security/android-security-policy.json`
  (with a justification) or in an inline `security-policy: allow <rule> -- <reason>`
  comment.
- Workflow exceptions go in `.github/zizmor.yml`, scoped to a file and line.
- Dependency advisories can be allowed by GHSA ID in `dependency-review.yml`, with
  a comment explaining why.

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `Lint, test, and build debug APK` fails at "Check native artifact and subtitle font regressions" | A version pin or font checksum in `tools/test_native_artifacts.py` no longer matches | Read the assertion message. AGP is checked against a minimum (9.4.1), not an exact version. |
| `Android security policy` fails with MAN002/MAN003 | A new exported component or permission | Review the intent handling or permission need, update `PRIVACY.md` if user data is affected, add it to the policy file with a justification |
| `zizmor` fails | New workflow pattern flagged | Fix it; if it is a false positive, add a scoped, justified ignore to `.github/zizmor.yml` |
| `Dependency review` reports snapshot warnings | The Gradle dependency snapshot for the PR head is not submitted yet | The action retries for 5 minutes. Re-run the job if automatic dependency submission was slow. |
| Release `resolve` job times out waiting for checks | A required check never ran on the tagged commit (for example, a commit from before these workflows existed, or a `[skip ci]` merge) | Run the missing workflow on that commit, then re-run the release |
| Release fails with "Run this workflow on the release tag" | It was started manually from a branch | Start it again with **Use workflow from → Tags → vX.Y.Z** |
| Prepare Release created the tag but did not start the release | The dispatch failed | Start Make release / Make Pre Release manually on that tag; do not delete and recreate the tag |
| Release `sign` job fails on the certificate | Wrong keystore in the environment secrets | Restore the original release key. Never publish with a different key: existing installs could not update. |
| Prepare Release fails with "master is not prepared" | `versionName` / `releaseVersionCode` not bumped on master | Open a PR that runs `python3 tools/prepare_release.py X.Y.Z` and updates `CHANGELOG.md` |

## Local validation

```bash
python3 tools/check_android_security.py
python3 tools/test_native_artifacts.py
python3 tools/validate_quantummpv_identity.py
actionlint                      # https://github.com/rhysd/actionlint
zizmor --config .github/zizmor.yml .
./gradlew :app:lintStandardDebug :app:testStandardDebugUnitTest assembleDebug
```
