# Upstream synchronization policy

QuantumMPV is an independent project with its own release identity. Upstream mpvRx tags are source references only; they are not QuantumMPV release tags.

## Version policy

The current QuantumMPV release line is **1.x**:

```text
v1.0.0 -> v1.1.0 -> v1.2.0
```

An upstream release such as `mpvRx v2.6.0` may be synchronized into QuantumMPV, but the resulting application keeps a QuantumMPV version such as `v1.1.0`. Upstream Android version codes must not be copied into QuantumMPV.

## Safe synchronization flow

When the upstream-release checker finds a new upstream release, it creates a review branch and pull request such as `sync/upstream-v2.6.0`. A clean merge is prepared automatically, while merge conflicts produce a review issue instead. The workflow never merges the branch into `master`.

Review or cherry-pick only the desired changes, resolve conflicts manually, and verify that QuantumMPV branding, updater endpoints, `QuantumMPV-` APK naming, database migrations, signing configuration, and version-code logic remain intact.

## Protected QuantumMPV files

These files require deliberate review after every upstream synchronization:

```text
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/quantummpv/app/domain/update/UpdateManager.kt
app/src/main/java/com/quantummpv/app/data/jellyfin/JellyfinClient.kt
app/src/main/java/com/quantummpv/app/preferences/MediaServerPreferences.kt
app/src/main/res/xml/network_security_config.xml
.github/workflows/
CHANGELOG.md
README.md
CITATION.md
```

The synchronization workflow does not merge into `master`, create tags, publish releases, or upload APKs. It may push a review branch and open a pull request. Merging the PR and releasing remain explicit maintainer decisions after CI passes.

## Attribution

Keep AGPL-3.0 notices, upstream copyright notices, third-party licenses, and the mpvRx attribution in `CITATION.md`. Release notes should identify upstream work as synchronized source material, not as an official mpvRx release.
