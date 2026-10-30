# CI/CD, security, privacy and release audit (2026-10-29)

Scope: the whole repository at `master` 1ccedb3d, the GitHub repository settings
readable with a maintainer token, the last ~60 workflow runs, and the published
releases. Findings cite files and lines as of that commit.

## 1. Baseline

| Item | Finding |
| --- | --- |
| Platform | Android app, single module `:app`, Kotlin + Compose, package/namespace `com.quantummpv.app` |
| Toolchain | Gradle wrapper 9.6.0, AGP 9.5.0, Kotlin 2.4.21, KSP 2.3.12, JDK 17, NDK 27.3.13750724, CMake 3.22.1, compileSdk 37, targetSdk 36, minSdk 26 |
| Variants | Flavors `standard`, `noVulkan`, `fongmi`. Build types `debug` (`.debug` suffix), `preview` (init from release, unsigned), `release` (R8 + resource shrinking). ABI splits arm64-v8a/armeabi-v7a/x86/x86_64 + universal for `standard`; universal only for the others |
| Signing | No Gradle signing config. APKs are signed in workflows with `apksigner` from four secrets. No keystore is committed. |
| Native code | libmpv/FFmpeg AARs downloaded from pinned GitHub releases and verified by SHA-256 (`native-artifacts.json`, `tools/verify_native_artifacts.py`) |
| Tests | 51 unit test files (~291 tests, Robolectric/MockK), 2 instrumented test files (Room migrations, OSD font), Python regression scripts in `tools/` |
| Static analysis | Android Lint (`abortOnError`), ktlint with baseline, detekt 1.23.8 |
| Repository settings | Public. Secret scanning + push protection on. Dependabot alerts and security updates on. Private vulnerability reporting on. CodeQL default setup off (advanced workflow used). Default workflow token read-only. Automatic dependency submission on. |
| Branch protection (master) | Required checks: `Lint, test, and build debug APK`, `ktlint + detekt` (strict). PR required, 0 approvals, conversation resolution, no force push/deletion. Admins not enforced. |
| Environments | `Production` and `Preview` exist with **no protection rules**; `github-pages` limited to master |

## 2. Initial findings

### Workflow and release problems

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| W1 | High | **Prepare Release never succeeded** (9 failures, 1 cancelled). It pushed a version-bump commit to protected `master` (`protected branch hook declined`). It also failed with "No version change to commit" when the version was already set. Even on success, a tag pushed with `GITHUB_TOKEN` does not trigger `release.yml`. | `prepare-release.yml`; runs 2026-09-30 → 2026-10-27 |
| W2 | High | **Releases were not gated on CI.** `v1.4.5-preview.1` was published from commit 6fb9145f, whose `Lint, test, and build debug APK` check had **failed**. | check runs of 6fb9145f |
| W3 | High | **Master validation was routinely cancelled.** `cancel-in-progress: true` applied to pushes, so merging several PRs quickly cancelled CI, CodeQL and secret scans on intermediate master commits (28 cancelled master runs across 7 workflows in a 60-run sample). | `ci.yml`, `codeql.yml`, `gitleaks.yml`, `static-analysis.yml` concurrency |
| W4 | High | **Signing secrets were reachable from the build job.** Release, pre-release and preview signing ran in the same job as Gradle and third-party plugins, with `contents: write`. The `Production`/`Preview` environments have no reviewers or branch/tag policies, and preview builds could be dispatched from any branch. | `release.yml`, `pre-release.yml`, `preview.yml` |
| W5 | Medium | **Template injection:** `${{ inputs.version }}` and `${{ inputs.tag_name }}` were expanded directly in shell before validation (zizmor `template-injection`, high). Only users with write access can dispatch, but it is still an injection point. | `prepare-release.yml:38`, `release.yml:53` |
| W6 | Medium | **Cache poisoning exposure:** release/pre-release jobs that sign and publish restored the shared Gradle cache (zizmor `cache-poisoning`). | `release.yml:98`, `pre-release.yml:36` |
| W7 | Medium | **CodeQL Action pinned to an annotated tag object** of an old `v3` (87ef0dc9…), not a commit. CodeQL Action v3 is deprecated in December 2026. | `codeql.yml` |
| W8 | Medium | **Persisted Git credentials** in 12 checkouts that never push (zizmor `artipacked`). | all workflows |
| W9 | Medium | **Brittle exact-version assertion:** `test_native_artifacts.py` asserted `agp = "9.4.1"`, so every AGP Dependabot PR failed CI (PR #158). | `tools/test_native_artifacts.py:18` |
| W10 | Low | A failing Gradle step was followed by an `always()` APK verification that failed with `find: … No such file or directory`, obscuring the real failure. | `ci.yml`, `instrumented-tests.yml` |
| W11 | Low | No dependency review, no workflow linting/auditing, no release checksums file, no SBOM, no provenance, and the signing certificate was never compared with the expected key. | — |
| W12 | Low | Duplicate Gradle dependency submission: `dependency-submission.yml` and GitHub's automatic dependency submission both run. | workflow list |
| W13 | Low | `upstream-sync-check.yml` opens PRs with `GITHUB_TOKEN`, so CI never runs on them automatically. Its PR body uses literal `\n`. | `upstream-sync-check.yml` |
| W14 | Info | `tools/validate_ci_assets.py` is stale (old action SHAs, needs Pillow) and is not used by any workflow. `tools/validate_p0_files.py`, `analyze_dead_code.sh` and `optimize_release.sh` are also unused. | `tools/` |
| W15 | Info | Inconsistent Netty pin: `constraints` request `netty-codec:4.2.19.Final` but `resolutionStrategy` forces all `io.netty` to `4.1.137.Final`. | `app/build.gradle.kts`, `build.gradle.kts` |

### Application security (no Critical or High issues found)

| ID | Severity | Finding | Evidence | Assessment |
| --- | --- | --- | --- | --- |
| A1 | Medium | Cleartext HTTP permitted globally | `res/xml/network_security_config.xml` base-config | By design for LAN/IPTV/WebDAV. Jellyfin has its own opt-in. Recorded as accepted exception `nsc-base-cleartext`. |
| A2 | Medium | FTP without TLS. SMB with signing and encryption not required. WebDAV over HTTP unless `useHttps`. | `FtpClient.kt:20`, `SmbClient.kt:69-70`, `WebDavClient.kt:70` | Typical for LAN players. Consider an "require SMB signing/encryption" option and an FTPS option. |
| A3 | Medium | `MANAGE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage` + exported player accepting `file://` | `AndroidManifest.xml` | App-private paths are blocked by `PlayerIntentSanitizer`. All-files access needs Play policy justification if published there. |
| A4 | Low | Release builds keep `Log.w/e` calls that include download URLs (may contain Jellyfin `api_key` or Xtream credentials) and secure-folder file paths | `AppDownloadManager.kt:138,272`, `SecureFolderRepository.kt:115,156,169,471` | Readable only via adb/root on modern Android. Use `redactUriForLog` (`utils/LogRedaction.kt`). |
| A5 | Low | `TorrentSelectionActivity` relaunches the player as an internal launch without the private-path filter on the torrent source | `TorrentSelectionActivity.kt:104-168` | Requires user consent. Confirm that `file:///data/data/<pkg>/…` torrent sources are rejected. |
| A6 | Low | Secure-folder media is app-private but **not encrypted**, while a comment in `data_extraction_rules.xml` says it is | `SecureFolderRepository.kt:30-48` | Fix the comment or add encryption. |
| A7 | Low | `data_extraction_rules.xml` (Android 12+) does not exclude `sftp_known_hosts.xml`; `backup_rules.xml` does | `res/xml/*` | Inconsistent, low impact (host keys are public). |
| A8 | Low | Cast media server binds the LAN interface. Its token is sent over plain HTTP. | `CastMediaServer.kt:38,178-185` | Required for Chromecast. Token is random per session. |
| A9 | Info | `FOREGROUND_SERVICE_SPECIAL_USE` is declared but no service uses `specialUse`. `MODIFY_AUDIO_SETTINGS`, `WAKE_LOCK` and `POST_PROMOTED_NOTIFICATIONS` have no direct Kotlin reference. | `AndroidManifest.xml` | Review and remove unused permissions. |

Checked and found OK: no WebView JS bridges, no custom TrustManager or
HostnameVerifier, user CAs trusted only in debug builds, SFTP strict host keys,
Keystore AES-GCM for every stored credential, PBKDF2 + Keystore-HMAC PIN, all
PendingIntents immutable, all dynamic receivers not exported, FileProvider
limited to specific cache dirs, loopback proxies on 127.0.0.1 with capability
tokens, no raw SQL with user input, no hardcoded API keys, no dynamic code
loading, `ProcessBuilder` argv (no shell) for yt-dlp with `--`, SHA-256-verified
updates and yt-dlp downloads.

## 3. Privacy assessment

Collected or stored locally (Room `mpvrx.db` and preferences):

- watch history and resume positions;
- file paths and titles;
- playlists, bookmarks, download history and stream URLs;
- network share and media-server accounts (usernames, plus Keystore-encrypted
  passwords and tokens);
- user-supplied AI and subtitle API keys (encrypted);
- secure-folder file index;
- local crash reports.

Sent off the device:

| Destination | Data | Trigger |
| --- | --- | --- |
| api.github.com, fahimofficial.github.io | none beyond the HTTP request | Update check, **automatic at startup when "auto update" is on** (default off) |
| IntroDB, TheIntroDB, AniSkip, Jikan, anime-skip, Wyzie/TMDB | title parsed from the media, IMDb/TMDB IDs, season/episode | **Automatic during playback, on by default** (`enable_introdb = true`) |
| Subtitle providers (SubDL, SubSource, Jimaku, BetaSeries, Subs.ro, scrapers) | search query or file title, IMDb ID, season/episode, user API key | User search |
| OpenAI, Groq, OpenRouter, Together, Anthropic, OpenCode | **extracted media audio** or subtitle text, user API key | User starts AI subtitles or translation |
| translate.googleapis.com (unofficial endpoint) | lyric text | User translates lyrics |
| lrclib.net, api.deezer.com | track and artist names | Lyrics / artist image lookup |
| Torrent trackers and peers | user IP, info hash | User starts a torrent (consent dialog) |
| Google Play services (Cast SDK) | Cast discovery, Play services telemetry | CastContext initialised when the Cast button is composed |
| User-configured servers (Jellyfin, Navidrome, Audiobookshelf, Seerr, SMB/FTP/SFTP/WebDAV) | credentials, playback requests | User |
| Android cloud backup / device transfer | the Room database (history, server list, encrypted secrets) | Android Auto Backup (`allowBackup` defaults to true) |

There are no analytics or crash-upload SDKs: Firebase, Sentry, ACRA and Bugsnag
are absent. `crashx` stores reports locally and shares them only through the
share sheet. LeakCanary is debug-only.

Mismatches between `PRIVACY.md` and the actual behavior:

1. PRIVACY.md says network requests happen only when the user asks. Intro/outro
   skip lookups are automatic and on by default, and they send the media title
   and episode IDs to third parties.
2. PRIVACY.md does not mention that media **audio** is uploaded to AI providers for
   AI subtitles, or that text goes to LLM providers and an unofficial Google
   Translate endpoint.
3. Lyrics (lrclib), artist images (Deezer), torrent IP exposure, Google Play
   services/Cast, and fetching the upstream contributors list from GitHub are not
   listed.
4. The permissions section omits `RECORD_AUDIO`, `MANAGE_EXTERNAL_STORAGE`,
   `REQUEST_INSTALL_PACKAGES` and `USE_BIOMETRIC`.
5. The stored-data section omits server credentials, API keys, download history
   and the secure folder. It also does not say that cloud backup includes the
   database.
6. The "no crash-reporting service" claim is accurate but should mention local
   crash reports and the share flow.

Required manual legal and operational reviews. Automated checks cannot establish
compliance with GDPR, India's DPDP Act 2023 or any other law.

- [ ] Update `PRIVACY.md` for items 1-6 and have it reviewed by someone qualified
      for the jurisdictions where the app is distributed.
- [ ] Decide whether IntroDB lookups should default off or ask for consent on
      first use. They send content metadata to third parties without a prompt.
- [ ] Add an in-app disclosure before the first AI-subtitle use stating that media
      audio is uploaded to the selected provider under the user's own account.
- [ ] Review the third-party providers' terms and data-processing policies,
      especially the unofficial Google Translate endpoint and the scraped
      subtitle sites.
- [ ] If distributing on Google Play: complete the Data safety form, justify all
      files access, and review `REQUEST_INSTALL_PACKAGES`, `RECORD_AUDIO` and the
      foreground-service types against policy.
- [ ] Decide whether the database should be in cloud backup, and whether to
      exclude watch history and server lists from it.
- [ ] Document a data deletion path (clear history, remove accounts) in
      `PRIVACY.md`.

## 4. Regression coverage gaps

Well covered by unit tests: intent sanitization, credential encryption and
recovery, update integrity, playback state, track restore, settings backup, and
the DB upgrade path 23/24/25 → 26.

Not covered by automated tests:

- app startup and the Koin graph;
- libmpv playback initialisation;
- local library scanning and permissions;
- live SMB/FTP/WebDAV/SFTP connections;
- Jellyfin and Navidrome login;
- the end-to-end updater (download, install);
- Cast;
- the secure-folder biometric gate;
- download foreground services.

Room schema 22 is missing from `app/schemas`, so `22 → 23` and `24 → 25` cannot be
validated with `MigrationTestHelper`, and `1 → 19` has no schemas at all.

## 5. Changes made

See the report in the pull request description and [CI_CD.md](../CI_CD.md).
