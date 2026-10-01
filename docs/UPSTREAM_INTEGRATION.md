# Upstream integration and QuantumMPV-only feature protection

## Upstream review (mpvRx)

Official sources reviewed on 2026-10-01:

- [mpvRx v2.7.0 release](https://github.com/Riteshp2001/mpvRx/releases/tag/v2.7.0)
- [mpvRx v2.7.1 release](https://github.com/Riteshp2001/mpvRx/releases/tag/v2.7.1)
- [v2.6.0 to v2.7.0 comparison](https://github.com/Riteshp2001/mpvRx/compare/v2.6.0...v2.7.0)
- [v2.7.0 to v2.7.1 comparison](https://github.com/Riteshp2001/mpvRx/compare/v2.7.0...v2.7.1)
- [Upstream changelog](https://github.com/Riteshp2001/mpvRx/blob/master/CHANGELOG.md)

The v2.7.0 release is a broad 151-commit/318-file update. Its subtitle work includes mpv's bidi compatibility option and independent secondary-subtitle scale/position controls. The v2.7.x implementation labels the bidi behavior for RTL compatibility; it is not a general “force LTR” renderer. QuantumMPV already has independent secondary-track selection and automatic anti-overlap placement, so the port adds separate secondary scale and optional manual position while keeping automatic placement as the default. The RTL compatibility preference is exposed in global Subtitle settings and the live player panel, reloads subtitles when changed, and respects `mpv.conf` ownership.

The v2.7.1 follow-up is a 3-commit/24-file font update: Google Sans Flex for mpv OSD text, plus a searchable/downloadable Google Fonts experience. QuantumMPV already bundles Google Sans Flex for its own UI and a separately licensed Go Noto Current Unicode subtitle fallback. This integration reuses the existing Google Sans Flex resource for mpv OSD, verifies its SHA-256 when installing it, adds a system-font choice, and preserves explicit `osd-font` values from `mpv.conf`. The online Google Fonts catalog/downloader is not included in this selective port.

A wholesale merge is not appropriate: the two clients have substantially different package trees and app-specific identity, release workflows, native artifact verification, settings architecture, and playback modifications. Ports are therefore applied selectively instead of replacing QuantumMPV files with upstream versions.

## QuantumMPV-only features that must be preserved

| Feature | Durable implementation | Regression coverage |
| --- | --- | --- |
| Liquid Glass bottom navigation | `AppearancePreferences.glassBottomNavigation`, Appearance settings switch, `MainScreen` renderer binding | `tools/test_quantummpv_custom_features.py` |
| Unicode subtitle fallback | bundled Go Noto asset, checksum-verified `SubtitleFontInstaller`, configured `sub-fonts-dir` mirror | `tools/test_native_artifacts.py`, `SubtitleFontInstallerTest` |
| QuantumMPV app identity and protected behavior | app namespace, release/build identifiers, pinned native artifacts | `tools/validate_quantummpv_identity.py`, `tools/verify_native_artifacts.py` |
| OSD font configuration | `MpvOsdFont`, explicit `mpv.conf` ownership of `osd-font` | `MpvOsdFontTest`, `MpvConfigOverrideTest`, custom-feature CI check |
| RTL bidi subtitle compatibility | persisted preference and `sub-vsfilter-bidi-compat` runtime option, disabled when `mpv.conf` owns it | `MpvConfigOverrideTest`, `tools/test_quantummpv_custom_features.py` |
| Secondary subtitle presentation | independent scale, optional manual position, automatic anti-overlap default | `SubtitlePositioningTest`, `MpvConfigOverrideTest`, `tools/test_upstream_release_ports.py` |
| Android TV D-pad seekbar | TV-gated directional scrubbing, repeat acceleration, key-up commit and focus highlight | `tools/test_upstream_release_ports.py`, Android CI compilation/device tests |

## Safe upstream-update procedure

1. Fetch and inspect the exact upstream release/tag; compare its files and commit history with QuantumMPV before merging.
2. Prefer small, isolated ports or cherry-picks. Do not use broad `checkout --theirs` resolution on QuantumMPV settings, playback, release, identity, or native-artifact files.
3. When integrating a QuantumMPV-specific feature, retain its preference key, UI binding, runtime consumer, resources, and any required user configuration behavior.
4. Add or update a focused test and the `test_quantummpv_custom_features.py` or `test_upstream_release_ports.py` regression checks whenever a feature's storage or wiring changes.
5. Run the full Android CI workflow, native-artifact verification, and security workflows on the proposed integration branch. Merge only after required checks pass.

This document and its regression script are an explicit checklist: add every future downstream-only feature to the table and to its automated guard when it is introduced.
