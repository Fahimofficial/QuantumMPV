# QuantumMPV Native Artifact Audit

## Root cause

The app flavor configurations directly consumed three checked-in AARs:

- `app/libs/mpvlib.aar`
- `app/libs/mpvlib-no-vulkun.aar`
- `app/libs/mpvlib-fongmi.aar`

Those archives had no embedded revision/provenance metadata. The repository's own history shows a newer backend update (`mpvlib`/no-Vulkan `1.0.10`, FongMi frozen at `1.0.9`) while the checked-in AAR hashes differed from the authoritative release hashes. Therefore, source changes could be followed by a successful Gradle build that silently packaged an older native snapshot.

## Implemented architecture

- Added `native-artifacts.json` as the pinned manifest with exact download URLs, SHA-256 values, source commits, FFmpeg pins, ABI set, and explicit yt-dlp/QuickJS/media-server provenance.
- Added `tools/verify_native_artifacts.py`:
  - `--download` fetches only the exact manifest URLs into ignored `app/libs` paths.
  - Verifies AAR SHA-256 and all four supported ABIs.
  - Verifies required native libraries.
  - Verifies final APK metadata, flavor mapping, ABI set, required libraries, and byte-for-byte equality of packaged native libraries against the pinned AAR.
- Removed the three tracked AARs so a checked-in binary cannot bypass the fetch gate.
- Corrected `mpvlib-no-vulkun.aar` to `mpvlib-no-vulkan.aar` everywhere.
- Added embedded `assets/native-build-metadata.properties` and flavor-specific `BuildConfig` fields for artifact, mpv, FFmpeg, yt-dlp, and media-server provenance.
- Added native artifact fetch and final APK verification to every APK-producing workflow: build, CI, CodeQL build, instrumented-test build, preview, pre-release, and release.
- Added `tools/test_native_artifacts.py` regression coverage for manifest integrity, checksum shape, metadata synchronization, typo removal, workflow gates, and absence of tracked AARs.

## Security and workflow changes

- Replaced mutable action tags with immutable commit SHAs, retaining version comments.
- Pinned and SHA-256-verified the Detekt CLI JAR before execution.
- Release signing now uses environment-backed `apksigner` password handling (`env:KS_PASS`/`env:KEY_PASS`), a private temporary keystore, and an exit trap for deletion. Passwords are not command-line arguments or files.
- Existing release protections remain: production environment for stable release, read-only defaults, write permissions only on publishing/tagging jobs, and unsigned APKs are removed/refused before publication.
- No keystore, signing key, password, token, or API credential was added.

## Validation performed

Passed:

- Pinned AAR downloads and SHA-256 verification for Standard, No-Vulkan, and FongMi.
- AAR ABI/native-library presence checks for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.
- Synthetic APK verification including metadata and byte-for-byte native library comparison.
- Tampered-AAR negative test: verifier rejected the altered archive with a checksum mismatch.
- `tools/test_native_artifacts.py`.
- `tools/validate_ci_assets.py`.
- `tools/validate_p0_files.py`.
- `tools/validate_quantummpv_identity.py`.
- Python syntax compilation and `git diff --check`.
- Immutable action reference audit.
- Detekt JAR checksum calculation: `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.

Attempted but blocked by environment:

- `./gradlew tasks --all --no-daemon --max-workers=1` reached project configuration but failed because no Android SDK/`ANDROID_HOME` is installed in this sandbox.
- Consequently, real Standard, FongMi, No-Vulkan, release/preview APK builds, ktlint, Detekt Gradle execution, unit tests, native CMake compilation, and final signed APK inspection could not be performed here.

## Remaining limitations

- The upstream mpvlib release publishes exact mpvlib source commits and FFmpeg pins, but does not publish a separately versioned yt-dlp binary in the AAR; yt-dlp remains runtime-managed and is explicitly marked as not bundled.
- QuickJS-NG is vendored in QuantumMPV rather than fetched as a separate release; its deterministic source-tree hash is recorded in the manifest.
- Actual APK verification must still be observed in GitHub Actions after these changes; the workflow gates are now arranged so a stale or mismatched native artifact fails before publication.

## Follow-up dependency remediation

After the release hardening commit, GitHub reported 19 Dependabot findings. The Dependabot alert endpoint itself was unavailable to the configured GitHub token, so the repository SBOM and OSV advisory database were used to identify the package/version pairs without guessing.

The vulnerable build-tool copies were traced to Android Gradle Plugin 9.3.2, whose `builder` and `apkzlib` POMs require Bouncy Castle 1.79. AGP 9.4.1 changes those dependencies to 1.80.2. The application security constraints were upgraded to Bouncy Castle 1.85 for `bcprov-jdk18on`, `bcpkix-jdk18on`, and `bcutil-jdk18on`. OSV reports no matching advisories for the selected Bouncy Castle 1.85 artifacts or the already-constrained Commons Lang 3.18.0, jose4j 0.9.6, and JDOM 2.0.6.1 versions.

The dependency remediation is committed separately after the native pipeline fix. A full Android build remains dependent on an Android SDK-equipped runner; the sandbox still lacks `ANDROID_HOME`.

## Dependency graph refresh result

The explicit Gradle dependency-submission workflow was added and successfully run against commit `0a01383b`. The refreshed SBOM now reflects the current checkout. OSV reports no advisories for the patched application coordinates (`bcprov-jdk18on`, `bcpkix-jdk18on`, and `bcutil-jdk18on` 1.85; Commons Lang 3.18.0; jose4j 0.9.6; JDOM 2.0.6.1).

The refreshed graph still contains seven advisory matches in transitive Android build tooling: AGP 9.4.1's `builder`/`apkzlib` resolve Bouncy Castle 1.80.2, Android bundletool 1.18.3 resolves jose4j 0.9.5, Jetifier resolves JDOM 2.0.6, and Android tooling's commons-compress resolves Commons Lang 3.16.0. These are build-time dependencies from upstream Android tooling rather than APK runtime dependencies. AGP 9.5.0-alpha07 still publishes Bouncy Castle 1.80.2, so upgrading further within currently published AGP versions does not remove the advisories. They require an upstream Android tooling release with patched transitive versions; the repository now refreshes its graph automatically so those alerts will clear when upstream publishes them.

## Final remediation result

After adding the buildscript classpath resolution rules and rerunning dependency submission, the latest SBOM contains only patched versions: Bouncy Castle modules 1.85, Commons Lang 3.18.0, jose4j 0.9.6, and JDOM 2.0.6.1. A fresh OSV batch query over all 717 Maven components reports **zero advisory matches**.
