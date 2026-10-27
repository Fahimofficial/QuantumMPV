# mpvRx 3.0.0 – 3.0.2 synchronization review

Covers the upstream releases reported in issues #98 (v3.0.0), #104 (v3.0.1) and #105 (v3.0.2). Each release
contains the previous one, so this single review answers all three. Policy: [UPSTREAM_SYNC.md](UPSTREAM_SYNC.md)
and [UPSTREAM_INTEGRATION.md](UPSTREAM_INTEGRATION.md).

Sources: [v3.0.0](https://github.com/Riteshp2001/mpvRx/releases/tag/v3.0.0),
[v3.0.1](https://github.com/Riteshp2001/mpvRx/releases/tag/v3.0.1),
[v3.0.2](https://github.com/Riteshp2001/mpvRx/releases/tag/v3.0.2).

## Why the automatic merge fails

- The last shared commit is `a83c3493` (2026-08-21). Since then QuantumMPV has 1,774 commits and upstream has 1,018.
- Upstream code lives in `app.gyrolet.mpvrx`, QuantumMPV's in `com.quantummpv.app`, so every Kotlin file differs by path.
- From upstream v2.7.2 (the last selective port) to v3.0.2 there are 480 commits (442 non-merge) touching 769 files
  (+95,995 / −30,480).
- After mapping upstream paths onto QuantumMPV's, only **11 of the 442** commits apply cleanly to `master`.
  A three-way apply of the 125 small fix/perf commits (≤ 4 files, ≤ 150 lines) gives 13 clean, 78 conflicting
  and 34 that touch files QuantumMPV doesn't have.

A wholesale merge would replace QuantumMPV's identity, design system and security work, so the sync stays selective,
as the policy requires.

To list the commits again:

```sh
git remote add upstream https://github.com/Riteshp2001/mpvRx.git
git fetch upstream tag v2.7.2 tag v3.0.2
git log --no-merges --reverse --format='%h %s' v2.7.2..v3.0.2
```

## Ported in this branch

Every port was reviewed by hand against QuantumMPV's code; authorship and the upstream hash are kept in the commit.

| Upstream | Change | Notes |
|---|---|---|
| `d0d5355a8` | `setOptionString` uses `mpv_set_property` once the core is initialised | libmpv's option API is pre-init only. No caller reads the return value. |
| `9ced52e2f` | No integer fallback reads for `duration` / `time-pos` | These are floating-point properties. |
| `13bc2555e` | A secondary subtitle no longer forces the primary ASS override | The primary uses `scale` unless the user forces it. |
| `9602cd1d2` | NFD filenames keep their bytes in network and proxy URLs | `java.net.URI` NFC-normalised them, which gave HTTP 404. A regression test was added. |
| `529617be8` | Three-button navigation keeps Android's contrast scrim | |
| `82829422a`, `b3cb77969`, `f440f57ee` | Library and Tree cold loads show the Folders scanning state | Uses QuantumMPV's existing localised `loading_state_scanning_message` instead of upstream's hard-coded text. |
| `f5f616b5d` (vignette part, by hand) | Vignette fade uses `1.0 - smoothstep(0.1, 1.3, r)` | Reversed smoothstep edges are undefined in GLSL. QuantumMPV's Glow shader has no precision-qualified constructors, so the rest of that commit doesn't apply. |
| `afd38886a` (by hand) | TheIntroDB v4 API | QuantumMPV called v2 with a v1 fallback. v1 is removed and v2/v3 are frozen ([migration guide](https://github.com/TheIntroDB/theintrodb-npm/blob/main/docs/migration-v3-to-v4.md)). `TheIntroDbV4Test` parses the v4 examples from TheIntroDB's OpenAPI spec. |

Guards for these changes are in `tools/test_upstream_release_ports.py`.

## Applies cleanly but not ported

| Upstream | Reason |
|---|---|
| `50cf4a1c0`, `4aad98480` | Play Store build only. QuantumMPV has no scoped-storage flavour (`SCOPED_STORAGE_ONLY` is false everywhere). |
| `e12ca8e24`, `e42c3e58a`, `6bf0fc284` | Upstream's Kyant Liquid Glass. QuantumMPV has no Kyant backdrop or `liquidGlassEnabled`; `6bf0fc284` would not compile. |
| `86ba2c4f6` | Adds a closing brace for an upstream-only scope bug; QuantumMPV's `FoldersPreferencesScreen` doesn't have that bug. |

## Release features that need a product decision

These conflict with QuantumMPV's own direction and need a maintainer decision before anyone ports them:

- **Material 3 Expressive redesign, new icon, icon builder and splash** (3.0.0). QuantumMPV has its own identity and the
  "Quiet Geometry" design system (`docs/design`). Porting this would replace both.
- **Kyant Liquid Glass** (3.0.0–3.0.2). QuantumMPV kept Classic and Clear Glass (1.4.0). Upstream's version adds a native
  backdrop dependency and HDR-specific paths.
- **Profile hub, floating navigation rework.** QuantumMPV's navigation is planned separately (4 tabs, audit P2-4).
- **Play Store build.** It needs a new flavour, signing and asset split.
- **Animated Apple Music covers and the lrc.red lyrics provider.** These are new third-party services, so they need a
  privacy and terms review first. They are candidates for the planned Labs section.
- **Removal of the in-app HLS proxy** (3.0.2). QuantumMPV still uses its proxy (8 files), so removing it is a
  behaviour change in its own right.
- **Two-finger speed swipe, persistent status bar, live expanding music player, phone-to-TV remote.** These are new
  features rather than fixes, and they interact with the gesture and HUD changes in PR #106.

## Fixes worth porting next (need manual adaptation)

These are bug fixes that matter to QuantumMPV but conflict with its code:

1. **SMB stalls** (`14cb6290c`, `e59f3f82e`, `12186e541`): direct transport, bounded connects and listings, and safer
   reconnects. QuantumMPV's `SmbClient` differs; needs testing against a real SMB server.
2. **Shader commands only after libmpv initialisation** (`d8457c987`). QuantumMPV's `MPVView` init order differs.
3. **Rotation-correct startup from container headers** (3.0.1–3.0.2). QuantumMPV's orientation probe was reworked
   separately (off-main probe, CHANGELOG "Opening videos"), so compare behaviour rather than code.
4. **Buffering indicator accuracy and byte-range prefetch for slow servers** (3.0.1).
5. **Thumbnail work waits for scrolling to settle** (3.0.1).
6. **Lyrics no longer carry over to the next song** (3.0.1).
7. **Settings restore that skips damaged entries** (3.0.2). QuantumMPV's `SettingsBackupPolicy` (PR #106) already
   validates and skips entries; compare before porting the crash-revert part.
8. **mpvlib 1.1.4 native update.** QuantumMPV pins its own checksum-verified AARs (`mpvlib-v1.0.10`); updating them
   follows the native-artifact process, not a code port.

## Note on tap timing

Upstream tried showing the controls on the first tap (`7009cc9e7`) and reverted it a day later (`340e1a644`), because
double-tap seeking on the sides then flashed the controls. PR #106 (CP-21) acts immediately only in zones whose double
tap does nothing, and keeps waiting in the left and right seek zones, so it avoids that regression.
