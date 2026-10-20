# QuantumMPV codebase audit — October 2026

Base: `origin/master` @ `a4b71535` (v1.4.0). Branch: `audit/quality-and-smoothness` (local, not pushed).
Scope note: ~220k lines of Kotlin. The audit traced the playback/subtitle, data/security and UI/motion
paths in depth; other areas (Jellyfin/Seerr/torrent/compressor UIs) were sampled, not exhaustively read.
The `graft` index referenced in AGENTS.md is not present in the repo and the tool is not installed,
so tracing used grep and direct reads.

Legend: **V** = verified in source, **S** = suspected (not proven at runtime), **M** = measured.

## Findings

### P1
| # | Area | Finding | Evidence | Status |
|---|------|---------|----------|--------|
| 1 | Playback | `MediaMetadataRetriever.setDataSource` on Main in `applyInitialVideoOrientation` (onCreate/onNewIntent); ANR risk for SAF/cloud/large files | `PlayerActivity.kt` | **Fixed** (V) |
| 2 | Subtitles | Saved `sid` is a raw mpv track ID; external subs are re-added in non-deterministic order (intent extras bypass `subtitleAddMutex`), so the wrong track can be restored | `PlayerActivity.kt` save ~4805 / restore ~4917; `TrackSelectionUtils.trackExists` | Open — needs a stable track key in `PlaybackStateEntity` (schema migration) |
| 3 | Subtitles | `sub-visibility` is never saved but forced on during restore (`restoreSubtitleVisibility = true`), overriding a user who hid subtitles and `sub-visibility=no` in mpv.conf | `TrackSelectionUtils.kt:77-81`, `applyPlaybackState` | Open — needs schema column; awaiting approval |
| 4 | Security | Navidrome request URL (Subsonic `t`/`s` auth) logged on failure; Log.d/v not stripped in release; stream URLs with `api_key` logged | `NavidromeClient.kt:196`, `proguard-rules.pro`, `PlayerActivity.kt:3822` | **Fixed** (V, R8 output checked) |
| 5 | A11y | Audio-player skip/seek, 10 panel Close buttons, speed steppers, mini-player close: no content description | `AudioPlayerControls.kt`, `components/panels/*`, `PlaybackSpeedSheet.kt`, `MiniPlayer.kt` | **Fixed** |

### P2
| # | Area | Finding | Status |
|---|------|---------|--------|
| 6 | Subtitles | Local subtitle scan launched adds fire-and-forget then auto-selected after a fixed 300 ms; with several or slow files nothing was selected | **Fixed**: adds are awaited |
| 7 | Coroutines | `runCatching` swallowed `CancellationException` in `addSubtitleSuspend`, `loadVideoPlaybackState` (cancel reported as "no saved state"), `saveVideoPlaybackState` (every debounce logged as ERROR) | **Fixed** |
| 8 | Memory | Cover art decoded at full resolution (`BitmapFactory` without `inSampleSize`) — a 3000 px cover ≈ 36 MB | **Fixed** for `EmbeddedArtworkResolver` (cap 1024 px, longest edge kept ≥ cap). `ThumbnailRepository.kt:839` remote thumbnail still open |
| 9 | Compose perf | `demuxer-cache-time` collected at the top of `PlayerControls`; every buffering update recomposed the whole controls body and restarted a keyed `LaunchedEffect` | **Fixed**: collected in an effect; only the seekbar reads the result |
| 10 | Dead code | `MediaScannerWorker` never enqueued (`triggerMediaScan` 0 callers) → FTS `media_index` always empty; `MediaSearchEngine.search`, `MediaFileRepository.searchMedia`, `MediaIndexRepository` unused | **Removed** (~210 lines). `MediaIndexDao`/`MediaIndexEntity` kept: part of the Room schema, migrations and tests |
| 11 | Service | `runBlocking(Dispatchers.IO)` on Main in `MediaPlaybackService.onTaskRemoved` (≤1.5 s); on timeout the write is cancelled mid-way and redone | Open (intentional, bounded; redesign needs device testing) |
| 12 | Security | `network_security_config` permits cleartext app-wide; only `JellyfinClient` refuses HTTP to public hosts | Open — product decision (LAN servers) |
| 13 | Compose perf | `SeekbarWithTimers` takes `position: Float`, so timers + track recompose every tick (PlayerControls, AudioPlayerControls). `MiniPlayer` already uses a deferred `drawBehind` read | Open — larger refactor of a 2k-line component |
| 14 | Touch targets | ~31 `IconButton`s forced below 40 dp (`MoreSheet` info 24 dp, Downloads 20 dp, mini-player close 32 dp) | Open — layout changes need visual review on device |
| 15 | i18n | ~150 hard-coded `Text("…")` literals; worst: `MusicLibraryContent.kt` (22), Lyrics sheets, Jellyfin/Seerr | Open |

### P3
- Two position sources still emit together (`propInt["time-pos"]` collector + 100/500 ms poll), contrary to the comment at `PlayerViewModel.kt:1761`. Open.
- Retriever leak on exception in the artwork fallback (`PlayerActivity.kt` ~7312). **Fixed** (try/finally).
- Per-item preference read in `MetadataRetrieval.enrichVideosIfNeeded`. **Fixed** (hoisted).
- Inline scrim `Brush` allocated per recomposition in `PlayerControls`. **Fixed** (top-level constants).
- Restored sid is `USER` or `INTERNAL` depending on `autoEnableSubtitles`, not on user intent (`PlayerActivity.kt:4929`). Open; touches selection policy.
- Exported `MediaScanReceiver` listens for deprecated `MEDIA_SCANNER_SCAN_FILE` (ignored by the handler; implicit broadcasts not delivered since API 26). Open, low impact.
- 208 raw `tween`/`spring` specs bypass `AppMotion` (largest: `AnimationStyles.kt` 46, `ScreenNavigation.kt` 19), so they don't collapse under Reduced motion. All infinite animations *are* gated. Open.
- Icon sizes for the same role differ (audio skip 28 dp vs seek 34 dp; video seek 20 dp; panel close 24/32 dp). Recommend `PlayerIconSize` tokens. Open.

### Checked and fine
No `GlobalScope`; one `runBlocking` (#11). All PendingIntents `FLAG_IMMUTABLE`. Services and FileProvider not exported; provider paths limited to cache/share dirs. No WebView. Backup rules exclude secure-folder media. Player receivers unregistered; service unbound; `onCleared` releases equalizer/syncplay/realtime subtitles. No raw material-icons imports — every icon goes through `ui/icons`. All infinite transitions respect reduced motion.

## Feature recommendations
| Feature | Value | Cost | Recommendation |
|---------|-------|------|----------------|
| Stable subtitle/audio track restore (key by external filename or lang+title+codec) + saved `sub-visibility` | High: fixes #2/#3 | Medium (Room migration + tests) | **Now**, with approval for the schema change |
| Single subtitle-add entry point (intent extras through `addSubtitleSuspend`) | High: removes ID races | Low–medium | Now, together with the above |
| Deferred seekbar position reads | Smoother controls during playback | Medium | Next |
| Shared `PanelHeader`/`PanelCloseButton` + `PlayerIconSize` tokens | Consistency, fewer a11y regressions | Low | Next |
| Drop the empty `media_index` FTS table (migration) or revive indexing properly | Cleanup / faster global search | Low (drop) / High (index) | Drop later; indexing only if library search becomes slow |
| Route `AnimationStyles`/`ScreenNavigation` specs through `AppMotion` | Reduced-motion correctness | Low | Later |

## Verification
| Check | Result |
|-------|--------|
| `:app:ktlintCheck` | pass |
| `:app:testStandardDebugUnitTest` | 196 tests, 0 failures (3 new: `LogRedactionTest`) |
| `:app:lintStandardDebug` / `:app:lintStandardRelease` | 0 errors (1338 / 1339 warnings, unchanged baseline) |
| `:app:assembleStandardDebug`, `:app:minifyStandardReleaseWithR8` | pass |
| R8 output | **M**: debug strings (`"Saving playback state for: "`, HLS routing) absent from release dex; error strings kept |
| `tools/*.py` validation (identity, custom features, upstream ports, native artifacts) | pass |
| Instrumented tests, device/emulator playback, profiling | **Not run** — no device/emulator in this environment. No performance numbers are claimed; the perf changes are source-inspection based |
