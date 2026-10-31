# UI, UX and Motion Audit (October 2026)

Scope: the Compose UI of the `app` module (browser tabs, pushed screens, player overlay, sheets,
panels, theme and motion tokens). Reference principles: Apple's fluid-interface guidance (respond on
press, 1:1 tracking, interruptible springs, velocity handoff, momentum projection, symmetric paths,
rubber-banding, reduced motion, multimodal feedback). The app keeps its own identity: Material 3
Expressive, the Google Sans Rounded type, the floating pill dock and the glass and Classic surfaces.

How findings were verified: by reading the code paths end to end. No device or emulator was
available in the audit sandbox (no `/dev/kvm`), so nothing here was observed at runtime, profiled, or
compared with screenshots. Each "Problem" names the code that causes it.

Priority key: Critical = visible defect for every user; High = broken setting, accessibility gap or
clear interaction defect; Medium = polish with a clear benefit; Low = consistency or maintenance.

## 1. Architecture summary

- Navigation: AndroidX Navigation3 `NavDisplay` in `ui/utils/ScreenNavigation.kt`, inside a root
  `SharedTransitionLayout` (`MainActivity.Navigator`). Forward, pop and predictive-back specs share
  one `screenNavTransition`. Styles: Slide (default), Depth, Fade through, Crossfade, None. The base
  is 300 ms × the user's animation-speed multiplier, with `FastOutSlowInEasing`.
- Root tabs: `ui/browser/MainScreen.kt`, a `HorizontalPager` with the `ExpressivePillNavigationBar`
  dock and a separate floating Search button that morphs into `MediaSearchScreen` through a shared
  element.
- Player: `PlayerActivity` is View-based and hosts Compose `PlayerControls`, `PlayerSheets`
  (custom `AnchoredDraggableState` sheet in `presentation/components/PlayerSheet.kt`) and
  `PlayerPanels`.
- Theme: `MpvrxTheme` provides `LocalMotionPolicy` (system animator scale, Battery Saver, the app's
  Motion style and Performance mode), spacing, glass config and the Haze source.
- Motion tokens: `ui/theme/Motion.kt` (`AppMotion.spatial`, `Spatial.*`, `Effect.*`, `Reduced*`).
  Usage is uneven: 128 `tween(` and 118 `spring(` call sites, about 20 distinct literal durations,
  and the `AppMotion.Duration` / `AppMotion.tween` tokens have no callers.

## 2. Findings

### Design

| # | Screen / component | Existing | Problem and why it matters | Recommendation | Complexity | Priority | Files |
|---|---|---|---|---|---|---|---|
| D1 | Player HUD scrims | `Box(...).background(TopHudScrim).alpha(x)` | `Modifier.alpha` only applies to content after it in the chain, so the 55 % black top and bottom gradients were drawn at full strength all the time, darkening 120 dp of video at each edge even with the controls hidden. | Draw the gradient in `drawBehind` with the animated alpha. **Implemented.** | Low | Critical | `ui/player/controls/PlayerControls.kt` |
| D2 | Music tab "configure server" cards | Three near-copies, `RoundedCornerShape(24.dp)`, button order differs | Inconsistent primary action placement; shape bypasses `AppShapeScale`. | Extract one `ServerSetupCard(icon, title, message, primary, secondary)` using `AppShapeScale.extraLarge`. | Low | Low | `ui/browser/MainScreen.kt` ~466-620 |
| D3 | Loading states | `LoadingState` in Folders and Videos; bare 48 dp `CircularProgressIndicator` in Recents and Playlist detail | Two loading languages across tabs. | Use one loading component with a neutral title. | Low | Low | `RecentlyPlayedScreen.kt`, `PlaylistDetailScreen.kt` |
| D4 | Hard-coded English | Recents remove dialog, `RemoveFromPlaylistDialog` | Not translatable; manual plurals. | Move to `strings.xml` plurals. | Low | Medium | same files |

### UX

| # | Screen / component | Existing | Problem and why it matters | Recommendation | Complexity | Priority | Files |
|---|---|---|---|---|---|---|---|
| U1 | Remove from playlist | Confirm dialog, then a "Deleted successfully" toast | The action never touches files, but there was no way back from a slip. | Undo snackbar that re-inserts the exact removed rows (same ids and positions). **Implemented**; the dialog is kept. | Medium | High | `PlaylistDetailScreen.kt`, `PlaylistDetailViewModel.kt`, `PlaylistRepository.kt`, `SelectionManager.kt` |
| U2 | Remove from Recents | Confirm dialog, rows deleted by path | Same forgiveness gap; the entities are not kept, so Undo needs them fetched first. | Snapshot the entities before deleting, then add the same Undo pattern. | Medium | Medium | `RecentlyPlayedViewModel.kt` |
| U3 | Player single tap | Deferred 250 ms in regions with a double-tap action | Show and hide controls lag by the double-tap window. This is inherent where double-tap exists. | Keep, but only defer where a double-tap action is configured (already the case unless "wait everywhere" is on). | n/a | Low | `GestureHandler.kt` |

### Animation and transitions

| # | Screen / component | Existing | Problem and why it matters | Recommendation | Complexity | Priority | Files |
|---|---|---|---|---|---|---|---|
| A1 | Player sheet scrim | Separate `animateFloatAsState` keyed on `targetValue` | The scrim held at 50 % during a drag until the target flipped, then animated on its own spring, so it was disconnected from the sheet. It also recomposed the sheet each frame. | Derive the scrim from the sheet's live offset in the draw phase. **Implemented.** | Low | High | `PlayerSheet.kt` |
| A2 | Player sheet, external swipe release | Settled by position only (10 %), no velocity | A short, fast upward flick snapped closed; the release had a velocity seam. | Track the swipe with `VelocityTracker1D`, project momentum (deceleration 0.99), and spring to the target starting at the release velocity. **Implemented.** | Medium | High | `PlayerSheet.kt` |
| A3 | Player sheet dismissal | `onDismissRequest` called directly and by the `currentValue` collector | Could fire twice after the sheet had opened. | Call directly only if the sheet never opened. **Implemented.** | Low | Medium | `PlayerSheet.kt` |
| A4 | Player panels | `fadeIn() + slideIn{it/3}` / `fadeOut() + slideOut{it/2}` with library defaults | Enter and exit travelled different distances; no reduced-motion or Off handling. | Same edge and distance both ways on the critically damped token spring; fade under reduced motion; none when Off. **Implemented.** | Low | High | `PlayerPanels.kt`, `Motion.kt` |
| A5 | Video-open overlay, Cinema bars | `animateDpAsState` on `height` | Re-measured both bars every frame. | Fixed-height bars scaled in a graphics layer from their outer edge. **Implemented.** | Low | Medium | `AnimationStyles.kt` |
| A6 | Pull to refresh | Content and indicator chased the pull with bouncy springs, read in composition | Content lagged and wobbled behind the finger; recomposed every frame; no feel at the commit point. | Read the pull state directly in the draw phase (1:1), rubber-band past the threshold, one haptic tick when a release would refresh. **Implemented.** | Low | High | `PullRefreshBox.kt` |
| A7 | List insert, remove and re-sort | `animateItem()` used once (search history) | Removed rows vanished and neighbours jumped; sort changes snapped. | Shared `appItemMotion()` (fade plus critically damped placement; travel dropped under reduced motion) on Recents and Playlists. **Implemented.** Extend to folders, videos and music next. | Low | Medium | `ui/utils/LazyItemMotion.kt`, `RecentlyPlayedScreen.kt`, `PlaylistScreen.kt` |
| A8 | Nav pill indicator | `DampingRatioLowBouncy` spring on tap | Overshoot after a tap with no momentum. It is part of the Expressive identity, so it was left as is. | If you want it calmer, use damping 0.85-0.9 for taps and keep the bounce for fling settles. | Low | Low | `MainScreen.kt` ~909-922 |
| A9 | Controls show and hide (Default style) | 100 ms tween in, 300 ms tween out | Tweens don't carry velocity when visibility flips mid-animation. Works acceptably because the HUD rarely reverses mid-flight. | Optional: move the Default style to springs. | Medium | Low | `AnimationStyles.kt` |
| A10 | Duration tokens | `AppMotion.Duration` / `AppMotion.tween` unused; about 20 literal durations | Timing drifts screen to screen. | Migrate raw tweens gradually, starting with ScreenNavigation and AudioPlayerControls. | Medium | Low | many |

### Micro-interactions

| # | Component | Problem | Change | Priority | Files |
|---|---|---|---|---|---|
| M1 | Playlist reorder | `ReorderFeedback` (pickup, tick per slot, confirm on drop) existed but was never wired; the held row had no lift. | Wired to the drag handle and move callback; the held row scales to 1.02 (off under reduced motion). **Implemented.** | Medium | `PlaylistDetailScreen.kt` |
| M2 | Volume gesture limits | Silent clamp at 0 %, 100 % and the boost cap. | One haptic tick when the value arrives at a limit. **Implemented.** | Medium | `GestureHandler.kt` |
| M3 | Thumbnails | Bitmaps popped in over the placeholder. | Late-arriving (decoded or generated) thumbnails fade in over 180 ms; memory-cache hits within 48 ms still appear at once so fast scrolling stays crisp. **Implemented** for grid and list `VideoCard`. Apply the same to `FolderCard` next. | Low | `VideoCard.kt` |
| M4 | Horizontal swipe to seek | The seek was measured from the touch-down point, so it jumped by about 1.5 × touch slop × sensitivity the moment it engaged. | Measure from the activation point: hysteresis first, then 1:1. **Implemented.** | Medium | `GestureHandler.kt` |

### Accessibility

| # | Component | Problem | Change | Priority |
|---|---|---|---|---|
| X1 | Player controls styles | Cinematic, Fluid expand and Spring bounce matched before the reduced-motion check, so they still scaled or slid with bouncy springs for users who asked for less motion. | Reduced motion now wins for every style (plain fade). **Implemented** in `AnimationStyles.kt`. | High |
| X2 | Video-open animation | Gated only on "animations disabled". | Zoom, slide and bars fall back to the fade from black under reduced motion. **Implemented.** | Medium |
| X3 | Buffering indicator, skip-chip offset | Library-default fade and spring regardless of motion settings. | Follow the HUD style and motion policy. **Implemented.** | Medium |
| X4 | Haptic feedback setting | `ProvideAppHaptics` (gates on the app's Haptic feedback setting and the system haptic setting) was defined but never installed, so the toggle had no effect on any `LocalHapticFeedback` call (player gestures, tab ticks, long-press selection, sliders). | Installed inside `MpvrxTheme`. **Implemented.** Haptics now also use the app's tuned vibration table. | High |

### Performance

Found by reading the code; nothing was profiled.

| # | Finding | Change |
|---|---|---|
| P1 | HUD scrim alpha read in composition (`.alpha(x)`) recomposed the controls layout on every frame of each show and hide. | Draw-phase read (D1). |
| P2 | Sheet scrim alpha read in composition. | Draw-phase read (A1). |
| P3 | Pull-to-refresh offsets read in composition. | Graphics-layer reads (A6). |
| P4 | Cinema bars animated `height`. | Layer scale (A5). |
| P5 | Not changed: during pager drags the nav pill recomputes tab widths and offsets in composition from `currentPageOffsetFraction` (`MainScreen.kt` ~892-988). | Candidate for a custom `Layout` that reads the offset in the placement phase. Profile first. |
| P6 | Not changed: `FolderListScreen` and `PlaylistScreen` lists have keys but no `contentType`. | Add `contentType` to help item reuse. |

### Technical architecture

- `AppMotion` is the right home for timing. Prefer `AppMotion.spatial(spec, reduced)` and the new
  `Spatial.StandardOffset` over inline `spring()` / `tween()` so reduced motion and Off are handled
  in one place.
- `SelectionManager` gained `announceDeleteSuccess` so a screen can confirm a result itself (Undo)
  without a duplicate toast. Failure toasts are unchanged.
- `PlayerSheet`'s `remember { AnchoredDraggableState(...) }` has no keys, so it keeps the density
  captured on first composition. This was not changed.

### Useful new features and integrations (not implemented)

- Undo for removing items from Recents (U2), using the same snackbar pattern.
- App shortcuts (static `shortcuts.xml`) for "Resume last played" and "Search", which bring the
  first screen closer to the user's intent without adding a dependency.
- A predictive-back preview for the player sheet and panels. They currently close on Back without
  a preview.

## 3. Implementation plan and status

1. D1, X4: visible defect and broken setting. **Done.**
2. A1-A4, A6: sheet, panel and refresh interaction quality. **Done.**
3. X1-X3: reduced motion in the player. **Done.**
4. M1-M4, A5, A7, U1: micro-interactions, list motion, Undo. **Done.**
5. Next: U2, D2-D4, A10 token migration, P5 profiling, `FolderCard` thumbnail fade, `animateItem` on
   the folder, video and music lists.

## 4. Verification

Run on JDK 17 (as CI uses) with SDK platform 37 and NDK 27.3:

- `:app:compileStandardDebugKotlin`: pass, with no new warnings in touched files.
- `:app:testStandardDebugUnitTest`: 267 tests, 0 failures. Robolectric needs JDK 17 or 21; JDK 25
  fails with "Unsupported class file major version 69".
- `:app:lintStandardDebug`: 0 errors. The new warnings these changes introduced were fixed.
- `:app:ktlintCheck ktlintKotlinScriptCheck`: pass. detekt 1.23.8 with `config/detekt/detekt.yml`:
  no findings.
- `:app:assembleStandardDebug` and `:app:assembleStandardDebugAndroidTest`: pass.
- `tools/validate_quantummpv_identity.py`, `test_quantummpv_custom_features.py`,
  `test_upstream_release_ports.py`, `test_native_artifacts.py`: pass.

Not verified: no emulator or device was available, so these were not observed or measured: the
animations, gesture feel, haptics, light and dark themes, screen sizes, reduced-motion behaviour and
frame timing. Instrumented tests were built but not run. Check these on a device:

1. Controls hidden: no dark bands at the top and bottom of the video.
2. Player sheet: drag it, flick it, grab it mid-close. The scrim follows the sheet, and a quick
   upward flick opens it.
3. Pull to refresh tracks the finger and ticks once at the threshold.
4. Playlist: remove items, then Undo. Reorder with haptics.
5. System "Remove animations" plus each controls style: only fades.
6. Gesture settings: turning Haptic feedback off silences every haptic.
