# F-Droid readiness: QuantumMPV

Current standard variant application ID: `com.quantummpv.app`

## Distribution boundary

This repository keeps its existing Google Play and GitHub release variants unchanged. F-Droid must use a dedicated FOSS-only source set/flavor and its own build recipe. Never remove a Play dependency globally to make an F-Droid build compile.

## Current blockers

Google Cast framework and prebuilt app/libs mpv AARs/native artifacts. A FOSS variant needs a source-built, licensed mpv/FFmpeg/native dependency pipeline and a Cast-free implementation. Keep current standard/noVulkan/FongMi variants intact for Play/GitHub.

## What the workflow does

`.github/workflows/fdroid-readiness.yml` runs an informational, non-blocking static prerequisite audit. It reports known Google Play Services/Firebase/Cast dependencies, app-local prebuilt AAR/JAR/SO files and missing root licensing as warnings and in the job summary; these findings do not fail the check. The workflow does not check whether metadata has been submitted to the separate `fdroiddata` repository. That recipe must use the FOSS variant's application ID (which may differ from the current standard variant), and the workflow does **not** build or publish an APK.

## Required path to official F-Droid

1. Keep the project source public and provide a valid FOSS licence covering the app and redistributable assets.
2. Create a dedicated FOSS variant with a distinct application ID if required by F-Droid's fork policy. Keep Play/GitHub application IDs and variants untouched.
3. Remove proprietary SDKs only from that FOSS variant. Replace integrations with FOSS alternatives or disable them cleanly with clear UI behaviour.
4. Build every native dependency from licensed source; do not consume opaque prebuilt app binaries or download executable artifacts during the build.
5. Add a pinned, tested `fdroiddata` metadata recipe with exact version name/code, source commit, Gradle task and output APK path. Validate with `fdroid lint` and a real `fdroid build` in the official buildserver.
6. Submit a merge request to `https://gitlab.com/fdroid/fdroiddata`. GitHub Actions in this app repository cannot add an app to the official F-Droid catalogue by itself.

## Important

The F-Droid signing key is separate from the Play upload key and the GitHub release key. Do not reuse or expose Play production signing secrets in F-Droid jobs. F-Droid's own build infrastructure signs official-repository APKs.
