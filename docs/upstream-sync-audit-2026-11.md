# Upstream mpvRx audit (November 2026)

Upstream: [Riteshp2001/mpvRx](https://github.com/Riteshp2001/mpvRx) `master` at `9b32eba35` (2026-11-05).
QuantumMPV: `master` at `d0b97b76`.

## History

- The last real merge base is `a83c34932` (2026-08-21). Every later sync was a selective port, not a merge.
  The latest is #108, which ported 3.0.0–3.0.2 fixes.
- Tags `v3.0.0`, `v3.0.1` and `v3.0.2` are not ancestors of QuantumMPV `master`.
- Since the merge base, upstream has 1,028 non-merge commits: 449 `fix`, 286 `feat`, 35 `refactor` and 30 `perf`.
- Kotlin sources: 781 upstream files differ from QuantumMPV, and 241 upstream files have no QuantumMPV counterpart.
  To compare them, `app.gyrolet.mpvrx` is mapped to `com.quantummpv.app`.

## Comparison matrix

| Area | Status | Notes |
| --- | --- | --- |
| mpv core fixes (runtime properties, timing reads, secondary-subtitle ASS, WebDAV NFD, TheIntroDB v4, tree loading state) | Already integrated | Ported in #108 and earlier commits. `tools/test_upstream_release_ports.py` guards the runtime-property, secondary-subtitle and TheIntroDB v4 ports. The timing-read, WebDAV NFD and tree loading-state ports have no regression check. |
| Profile hub tab (`ui/browser/profile/ProfileScreen.kt`) | Integrated in this change, adapted | Ports the hero, avatar picker, crop and rotate, display name, Recents and Playlists shelves, shortcut tiles, and the recent-item action sheet. The Snapshots shelf and watch-statistics bento are replaced with a Library Insights tile (see exclusions). |
| Recents and Playlists folded into Profile | Integrated in this change | Same as upstream `MainTab { HOME, MUSIC, NETWORK, JELLYFIN, PROFILE }`. The screens remain reachable from Profile, the More menu and Music › Playlists. |
| `LocalIsMainTabPage` (Back button when a tab screen is pushed standalone) | Integrated in this change | Used by Recents and Playlists. |
| Snapshots / frame capture (`ui/framecapture`, `domain/framecapture`, `FrameCaptureDao`, `SnapshotFolderDao`) | Missing, excluded | This is a separate feature. It needs a Room schema migration (new entities), player-side capture hooks and a media library. It is too large to port blind without device testing. |
| Watch statistics (`WatchStatsRepository`, `WatchStatsScreen`) | Missing, excluded | Needs session recording inside the player service. QuantumMPV's Library Insights covers library statistics. |
| Liquid-glass system (`ui/liquidglass`, `LiquidGlass*`) | Conflicts with customization | QuantumMPV deliberately supports only Classic and Clear Glass (`tools/test_quantummpv_custom_features.py`). |
| Animated / seasonal backgrounds (`ui/background`) | Missing, excluded | Decorative. Conflicts with QuantumMPV's wallpaper and surface-style choices. |
| Lyrics providers (`data/lyrics/providers/*`, provider picker) | Missing, not yet ported | Self-contained, but adds 19 network clients for third-party lyric sites. Needs a privacy review (see PRIVACY.md) before porting. |
| DLNA, SMB share discovery, LAN server discovery | Missing, not yet ported | Self-contained network feature. Should be ported on its own with device testing. |
| TV remote / pairing server (`remote/*`, `ui/remote`, `ui/tv`) | Missing, excluded | Opens a local network listener. Needs a security review. |
| Image viewer (`ui/imageviewer`), ZIP archive media | Missing, not yet ported | New feature with no dependency on this change. |
| Launcher icon variants (`ClassicMainActivity`, `PurpleMainActivity`, `LauncherIconManager`) | Excluded | Conflicts with QuantumMPV branding and its single launcher identity. |
| Material 3 expressive wrappers (`AppAlertDialog`, `AppTextField`, `ConnectedShapes`, `AppToast`) | Missing, not needed for this port | Profile uses QuantumMPV's existing Material 3 components instead. |
| Thumbnail, MediaStore and memory performance work (`ThumbnailLoadPolicy`, `MediaStoreLiveMonitor`, `BitmapMemoryCache`, `TreeViewCache`, `MediaScanFastPaths`) | Missing, not yet ported | Touches the indexer that QuantumMPV customized heavily. Port per commit and profile on hardware. |
| KMP cross-platform branch, Play Store branch | Not relevant | These are separate upstream product lines. |
| Upstream dock search, dock menu and Support | Not applicable | The QuantumMPV dock, the More menu and the Support screen are QuantumMPV-only. |

## Recommended next ports

Port each of these as its own PR with device validation:

1. Thumbnail and MediaStore performance commits (`perf:` scope).
2. Lyrics providers, after a privacy review.
3. Snapshots, together with its Room migration. The Profile screen already has a slot where its shelf would go.
4. DLNA and SMB discovery.

## Owner decisions (November 2026)

- **Launcher icon and icon builder:** keep the QuantumMPV launcher icon. Do not port mpvRx's purple icon
  or its mpvRx logo assets. The icon builder may be ported only if it offers QuantumMPV artwork.
- **Liquid Glass:** port it as a separate, experimental option for player controls. Keep the app-wide
  surface-style picker limited to Classic and Clear Glass, as `tools/test_quantummpv_custom_features.py` requires.

## Porting plan for the rest of 3.0.0–3.0.2

A trial merge of v2.7.2 → v3.0.2, with packages mapped to `com.quantummpv.app`, merged about 470 files
cleanly and left 224 files with 1,664 conflicts plus 58 delete/modify conflicts. The rest is ported one area
per PR, each passing ktlint, lint, unit tests and the debug build:

1. Performance and loading (thumbnail policy, instant reopen, live library updates, range-chunk cache, SMB).
2. Player (two-finger speed, persistent status bar, TV-style volume/brightness capsule, orientation,
   landscape layout, mini player expanding to the full player).
3. mpvlib 1.1.4 and hdr-toys.
4. Snapshots and watch statistics, with a Room migration (fills the Profile tab).
5. Lyrics rework.
6. Ambient modes and subtitle gestures.
7. Material 3 Expressive restyle, colour picker, animated backgrounds, Liquid Glass (experimental, player only),
   icon builder (QuantumMPV artwork only).
8. Image viewer, ZIP media, DLNA discovery, media widget.
