# Security practices

This document describes how QuantumMPV is built, tested and released securely.
To **report a vulnerability**, follow [the security policy](../SECURITY.md): use
GitHub private vulnerability reporting (Security tab → "Report a vulnerability"),
not a public issue.

## Automated controls

| Area | Control | Where |
| --- | --- | --- |
| SAST | CodeQL for Kotlin/Java, C/C++, Python and GitHub Actions on every PR, every master push and weekly | `.github/workflows/codeql.yml` |
| Android lint | Android Lint (including its security checks) for debug and release variants, `abortOnError = true` | `ci.yml`, `app/build.gradle.kts` |
| Android policy | Manifest, exported components, permissions, FileProvider paths, network security config, backup rules, debuggable/minified build types, R8 log stripping, committed keystores, trust-all TLS, disabled SSH host-key checks, weak ciphers, WebView bridges, dynamic code loading, mutable PendingIntents, exported dynamic receivers | `tools/check_android_security.py`, `config/security/android-security-policy.json`, `security.yml` |
| Secrets | Gitleaks over PR/push ranges and the full history weekly. GitHub secret scanning with push protection. | `gitleaks.yml`, repository settings |
| Dependencies | Dependabot alerts and security updates. Weekly version updates with a 7-day cooldown. Dependency review blocks new high/critical advisories in PRs. Gradle graph submitted from master. | `dependabot.yml`, `dependency-review.yml`, `dependency-submission.yml` |
| Supply chain | Gradle wrapper validation. SHA-pinned actions. Checksum-verified native AARs, detekt and actionlint. Hash-pinned Python tooling. zizmor workflow audit. SBOM and build provenance attestations on stable releases. | `ci.yml`, `workflow-audit.yml`, `release.yml`, `native-artifacts.json` |
| Release integrity | Tag must be on master with passing required checks. Emulator tests on the tag. Version, `versionCode` and non-debuggable checks. Signing isolated from the build. Certificate pinning against `RELEASE_SIGNING_CERT_SHA256`. | `release.yml`, `docs/RELEASE.md` |

## Severity and exceptions

Critical, high and medium findings block merges and releases. Low and
informational findings are annotated only. See the severity table in
[CI_CD.md](CI_CD.md#severity-policy). An exception needs a written justification
in a reviewed file:

- `config/security/android-security-policy.json` for Android policy exceptions
  (for example, the global cleartext exception and every exported component and
  permission);
- an inline `// security-policy: allow <rule> -- <reason>` comment for one source
  line;
- `.github/zizmor.yml` for workflow findings, scoped to a file and line.

Blanket suppressions are not accepted.

## Secret management

- Signing credentials (`SIGNING_KEYSTORE`, `SIGNING_KEY_ALIAS`,
  `SIGNING_STORE_PASSWORD`, `KEY_PASSWORD`) live in GitHub environment secrets
  (`production`, `preview`). Workflows reference them only in signing steps,
  never echo them, and remove the temporary keystore on exit.
- Workflows triggered by pull requests never receive secrets. Fork PRs get a
  read-only `GITHUB_TOKEN`. No workflow uses `pull_request_target`.
- The app ships no API keys. Users provide their own keys for AI and subtitle
  providers. The app stores them, along with server passwords and tokens,
  encrypted with an Android Keystore AES-GCM key
  (`NetworkCredentialCipher`, `EncryptedStringPreference`).
- **If a secret is ever committed, removing it from the tree is not enough.**
  Revoke or rotate it at the issuer first, then clean the history if needed.
  The weekly full-history Gitleaks scan exists to catch such cases.

## Application security posture (audit of 2026-10-29)

These are the verified protections. Evidence is in
[audit/2026-10-29-ci-security-privacy-audit.md](audit/2026-10-29-ci-security-privacy-audit.md).

- **Exported entry points:** `PlayerActivity` and `MediaInfoActivity` sanitize
  incoming intents (`PlayerIntentSanitizer`). The sanitizer blocks app-private
  paths, the app's own content authorities and mpv pseudo-protocols.
- **TLS and SSH:** there are no custom TrustManagers or HostnameVerifiers. User CAs
  are trusted only in debug builds. SFTP uses strict, user-confirmed host keys.
- **Local servers:** loopback proxies bind `127.0.0.1` and use random capability
  tokens. The Cast server binds the LAN address for the Chromecast and is guarded
  by a 128-bit path token.
- **Intents and receivers:** all PendingIntents are immutable. All dynamic
  receivers are `RECEIVER_NOT_EXPORTED`.
- **Updater:** it refuses downloads without a published SHA-256 and re-verifies
  before install. Android's same-signer rule provides authenticity.
- **Release builds:** R8 minification is on, and `Log.v`/`Log.d` are stripped.

Known residual risks, tracked in the audit report:

- cleartext HTTP, FTP and unsigned SMB are allowed for LAN use;
- `Log.w`/`Log.e` calls in release builds include some URLs and secure-folder
  paths;
- secure-folder files are app-private but not encrypted at rest;
- cloud backup includes the Room database (history and server list; secrets
  inside it stay encrypted).

## Release security measures

See [RELEASE.md](RELEASE.md). In short:

- releases come only from tags on master whose required checks passed;
- the build job has no secrets;
- the signing job runs no build tooling and is gated by the `production`
  environment;
- every APK is checked for package name, version, `versionCode`, debuggable flag,
  native contents and signing certificate;
- published releases include SHA-256 checksums, an SPDX SBOM and Sigstore-backed
  build provenance.
