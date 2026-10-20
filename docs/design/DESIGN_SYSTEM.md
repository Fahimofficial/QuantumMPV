# QuantumMPV design system — "Quiet Geometry"

Status: adopted October 2026. Base: v1.4.0 + audit branch.
Scope of verification: source inspection and builds only. No emulator or device was available, so
**no screen in this document has been visually verified**; every visual claim is a design intent.

## 1. Inventory (what exists today)

~35 `Screen` destinations plus MainScreen's 6 pager tabs (Home, Music, Recents, Playlists, Network,
Jellyfin), 22 player sheets, 9 draggable player panels, ~42 `AlertDialog` call sites and 16
`ModalBottomSheet` call sites.

| Area | Built from | Works well | Weak |
|------|------------|------------|------|
| Theme | `MpvrxTheme`: 32 palettes + custom + dynamic, AMOLED, Google Sans Rounded / system font, circular theme reveal | Wide personalisation, contrast enforcement (4.5:1) in palette derivation | Three overlapping token sets (`AppShapes`, `AppShapeScale`, `DesignTokens.Radius`/`Spacing`); 445 raw `RoundedCornerShape` literals, 13 distinct radii, half off-scale (6/10/14/18/24 dp) |
| Surfaces | `SurfaceStyle` Classic / Clear Glass, Haze backdrop, blur **off** by default | Glass is already opt-in and cheap by default | — |
| Home / library | Floating dock (pill + Search circle), `VideoCard`/`FolderCard` at 16 dp | Dense, scannable lists; tabular figures on durations | Card/chip/thumbnail radii differ by screen |
| Player | One `ConstraintLayout` HUD, 0.55 black scrims, 44 dp circular `ControlsButton`, five seekbar styles, reduced-motion-aware control show/hide | Scrims keep controls legible over bright footage; timers use tabular figures | Sheets have no visible drag affordance although they drag to dismiss; panel headers duplicated 9× with 24 dp vs 32 dp close icons |
| Settings | me.zhanghai preferences inside `PreferenceCard` (32 dp, `surfaceContainerLow`) with accent-bar section headers | Consistent card/divider rhythm | — |
| Motion | `AppMotion` (spring tokens, `MotionPolicy` for system/user reduced motion) | Infinite animations all gated | App-wide screen transitions ignore reduced/off motion |

## 2. The four directions

| | Neo-brutalism | Glassmorphism | Bauhaus | Japandi |
|---|---|---|---|---|
| Strength | Memorable, strong hierarchy | Depth; separates floating chrome from video | Geometric order, clear primary vs secondary | Calm, content first, low clutter |
| Weakness for a player | Thick borders and hard shadows fight the video and add overdraw; harsh over 32 user palettes | Blur cost (Haze per surface), legibility varies with footage | Pure primaries clash with user palettes; can feel rigid in dense settings | Can waste space; low-contrast controls are hard to find |
| Dark-room playback | Poor | Good if restrained | Neutral | Good |
| Performance | Extra draw passes | Highest GPU cost | Cheapest | Cheap |
| Fits existing code | Needs a rewrite of every surface | Already implemented (Clear Glass, opt-in) | Matches the existing concentric-radius rule and accent bars | Matches `surfaceContainer*` tonal surfaces |
| Verdict | **Reject** | **Keep, limited to floating chrome**, never default | **Adopt its structure** | **Adopt its restraint** |

## 3. Decision

None of the four is adopted as a whole system. QuantumMPV uses **Quiet Geometry**: Japandi restraint
for surfaces and colour, Bauhaus discipline for shape and hierarchy, and the existing opt-in glass only
for chrome that floats over video (dock, mini player, player buttons).

- **From Japandi:** tonal `surfaceContainer*` layers instead of borders and shadows; one accent (the
  palette primary) used for state, never decoration; generous but fixed spacing steps; content and
  video always dominate.
- **From Bauhaus:** a small geometric shape scale with the concentric rule (inner radius = outer −
  padding); circles mean "action", rounded rectangles mean "content"; a single visual weight per
  role (primary transport is the only filled, large control).
- **Rejected:** neo-brutalist borders/offset shadows; blur on lists, settings or dialogs; decorative
  motion; beige/primary-colour palettes that override the user's chosen theme.

Why: it keeps the 32 user palettes working (the identity comes from shape, rhythm and motion, not a
fixed colour), costs nothing at render time, and keeps controls legible over any footage.

## 4. Tokens

Existing tokens stay authoritative; this system adds the missing roles rather than a fourth scale.

**Colour (from the active `ColorScheme`)**
- Background `surface`; grouped content `surfaceContainerLow`; raised/sheets `surface` +1 dp tonal;
  floating chrome `surfaceContainerHigh` (Classic) or Clear Glass.
- Accent `primary` for selection, focus and progress only. Status uses `error` / `tertiary`.
- Over video: content `onSurface` of the dark scheme, button container `surfaceContainer` at 55 %,
  scrims black 55 % → 0 over 120 dp.

**Typography (Google Sans Rounded, system font for scripts it lacks)**
Screen title `titleLarge`; section `titleMedium`/emphasised; body `bodyLarge`/`bodyMedium`;
metadata `labelMedium` `onSurfaceVariant`; **all changing numbers use tabular figures (`tnum`)**.

**Shape** (`AppShapeScale`; concentric)
4 chip/badge · 8 thumbnail inner · 12 menu tile · 16 card · 20 menu panel · 28 sheet/dialog · full
circle = action. New code must not introduce off-scale radii.

**Spacing** (`MaterialTheme.spacing`): 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64.

**Icons** (`ui.theme.IconSize`, Material Symbols Rounded Filled only, via `ui/icons`)
`Small` 18 dp (inline/metadata) · `Standard` 24 dp (toolbar, sheet, panel actions) · `Large` 28 dp
(secondary transport) · `Hero` 36–44 dp (play/pause). Interactive icons always sit in a ≥ 48 dp
target and carry a content description unless a visible label names the action.

**Motion** (`AppMotion`)
Springs, no overshoot for layout (`Spatial.Standard`), light overshoot only for direct touch
feedback (press scale, selection pill). Press scale 0.92–0.95. Durations for tweens: 120 / 200 / 300
ms. Every transition collapses to a short fade under Reduced motion and to nothing under Off.

## 5. Shared components introduced or changed in this pass

| Component | Purpose |
|-----------|---------|
| `PlayerPanelHeader` | One header for all player panels: title, 24 dp close icon in a 48 dp target, labelled |
| `SheetDragHandle` (in `PlayerSheet`) | Visible affordance for the existing drag-to-dismiss; `title` becomes the sheet's accessibility pane title |
| `ui.theme.IconSize` | Named icon sizes for roles above |
| `ScreenNavDisplay` | Honours Reduced / Off motion: Off → no transition, Reduced → cross-fade instead of slide/scale/depth |
| `ControlsButton` (player HUD, 33 call sites) | Press springs to 0.92 like the dock and menu tiles; layer-phase only, off under Reduced motion |
| Touch targets | `MoreSheet` info button back to a 48 dp target (was forced to 24 dp); mini-player close container 32 → 40 dp with a 48 dp touch target |

Kept deliberately: the audio transport row steps down from the centre (play 44 → seek 34 → skip 28 dp).
That is the hierarchy rule above, not an inconsistency. The video HUD's 44 dp `ControlsButton` (20 dp
icon) is below the 48 dp target but enlarging it changes every landscape row; it needs device review.

## 6. Not done yet (recommended order)
1. Migrate the 445 raw radii to `AppShapeScale` screen by screen, with screenshots.
2. Player icon sizes per role (`IconSize`) across portrait/landscape control rows.
3. A shared `AppDialog` for the 42 `AlertDialog`s and a shared sheet for the 16 `ModalBottomSheet`s.
4. Deferred seekbar position reads (see the audit report) before any new seekbar visuals.
5. Visual QA on device: bright/dark footage, landscape, TalkBack, font scale 1.3–2.0.
