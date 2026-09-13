# Upstream synchronization policy

QuantumMPV is an independent project with its own release identity. Upstream mpvRx tags are source references only; they are not QuantumMPV release tags.

## Version policy

The current QuantumMPV release line is **1.x**:

```text
v1.0.0 -> v1.1.0 -> v1.2.0
```

An upstream release such as `mpvRx v2.6.0` may be synchronized into QuantumMPV, but the resulting application keeps a QuantumMPV version such as `v1.1.0`. Upstream Android version codes must not be copied into QuantumMPV.

## Safe synchronization flow

When the upstream-release checker opens an issue, review the upstream release notes and create a branch such as `sync/upstream-v2.6.0`. Merge or cherry-pick only the desired changes, resolve conflicts manually, and verify that QuantumMPV branding, updater endpoints, `QuantumMPV-` APK naming, database migrations, signing configuration, and version-code logic remain intact.

The synchronization workflow does not merge code, create tags, publish releases, or upload APKs. Those actions remain an explicit maintainer decision after CI passes.

## Attribution

Keep AGPL-3.0 notices, upstream copyright notices, third-party licenses, and the mpvRx attribution in `CITATION.md`. Release notes should identify upstream work as synchronized source material, not as an official mpvRx release.
