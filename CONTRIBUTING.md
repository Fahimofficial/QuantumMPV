# Contributing to QuantumMPV

Thanks for helping improve QuantumMPV. This guide covers what you need to build
the app and what we expect in a pull request.

## Build requirements

- JDK 17 (Temurin recommended)
- Android SDK platform `android-37.0`, build tools `37.0.0`
- Android NDK `27.3.13750724`
- CMake `3.22.1`
- Git with full history (some build steps read tags)

Build and verify locally exactly as the CI does:

```bash
./gradlew :app:lintStandardDebug :app:testStandardDebugUnitTest \
          :app:assembleStandardDebugAndroidTest assembleDebug
./gradlew :app:lintStandardRelease
python3 tools/validate_quantummpv_identity.py
```

## Pull request expectations

- Branch off `master` and keep each PR focused on one change.
- Follow the repository `.editorconfig`; match the surrounding Kotlin style.
- Add or update tests for behavior changes. Unit tests live in `app/src/test`,
  instrumented tests in `app/src/androidTest`.
- Update `CHANGELOG.md` when the change is user visible.
- Do not commit generated build output, signing keys, or large binaries.
- CI must be green: lint, unit tests, debug build, release lint, and the
  identity validation script.
- Describe how you tested on device: Android version, device, and media used
  (relevant for HDR, shaders, subtitles, and Cast changes).

## Upstream relationship

QuantumMPV derives from the mpv-android / mpvKt lineage (mpvEx, mpvRx). When
your change fixes something that also exists upstream, please mention it so the
fix can be coordinated rather than diverging further.

## Reporting bugs and requesting features

Use the issue templates in `.github/ISSUE_TEMPLATE`. Security issues go through
[SECURITY.md](SECURITY.md), not the public tracker.

## Code of conduct

Participation is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
