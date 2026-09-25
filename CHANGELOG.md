# Changelog

These notes are written in plain English and focus on what changed for real use.
## 1.1.0

The first stable QuantumMPV release since 1.0.0. It rolls the 1.1.0 preview work into one
signed build, catches up with the upstream **mpvRx v2.5.0 and v2.6.0** releases, and adds
emulator and static-analysis coverage to CI.

### Upstream sync (mpvRx v2.5.0 → v2.6.0)

- **Merged upstream v2.5.0 and v2.6.0** on a dedicated sync branch, resolving conflicts in
  favor of QuantumMPV branding, the `com.quantummpv.app` application ID, updater asset
  names, Jellyfin HTTP safeguards, and Room database migrations.
- **1.x line preserved**: upstream's 2.x version numbers are never adopted; QuantumMPV keeps
  its own 1.x release sequence.

### Tests and CI

- **Instrumented tests on emulators**: a new `Instrumented tests` workflow runs the Android
  instrumentation suite on API 30 and API 34 emulators for every pull request.
- **Unit tests**: added coverage for subtitle title matching, intent subtitle load policy,
  and episode scope matching.
- **Static analysis**: added a ktlint + detekt workflow so Kotlin style and code-quality
  issues surface in CI.
- **Changelog hygiene**: trimmed this file to QuantumMPV history, keeping the older upstream
  notes available in git history.

### Appearance (1.1.0 preview roll-up)

- **Focused Liquid Glass**: kept Liquid Glass as an opt-in treatment for the bottom
  navigation only, with solid defaults preserved and a small, performant spring bounce when
  the selected tab changes.
- **Opt-in Glass navigation**: Liquid Glass is limited to an independent bottom-navigation
  switch; cards and other app surfaces retain the solid default appearance.

## 1.1.0-preview.12 — Focused navigation polish

- **Focused Liuid Glass**: Kept Liquid Glass as an opt-in treatment for the bottom navigation only.
- **Solid defaults preserved**: Removed the app-wide backdrop, glass preference cards, strength slider, transparency fallback, and live preview.
- **Spring tab bounce**: Selected bottom-navigation items now use a small, performant bounce when selection changes, while respecting the existing navigation animation setting.

## 1.1.0-preview.9

- **Build correction**: Restored the AMOLED appearance switch declaration after removing the live preview section.

## 1.1.0-preview.8

- **Simplified appearance**: Removed the live appearance preview and animated Home background.
- **Solid defaults preserved**: The existing solid card and navigation appearance remains unchanged unless users explicitly enable the glass options.
- **Focused customization**: Retained the independent opt-in Glass-style cards and Glass bottom navigation switches.

## 1.1.0-preview.7

- **Build fix**: Corrected the glass-card preference wiring for the About libraries screen.

## 1.1.0-preview.6

- **Opt-in glass cards**: Added a Glass-style cards switch; the default remains the existing solid settings-card appearance.
- **Opt-in glass navigation**: Added an independent Glass bottom navigation switch with contrast-preserving tint and border treatment.
- **Live appearance preview**: Added a small preview for cards and navigation so changes can be evaluated before leaving Appearance settings.
- **Reset appearance**: Added a confirmation-protected action to restore the default theme, solid surfaces, and disabled animation.
- **Faint Home animation**: Reduced the optional animated Home background to a low-opacity, background-only accent effect.

## 1.1.0-preview.5

- **Modern glass cards**: Settings cards now use low-opacity theme-aware surfaces with soft outline borders for a cleaner glass-style appearance.
- **About card consistency**: About library cards use the same translucent surface and border treatment while retaining the subtle support-card shimmer.
- **Visible Home backdrop**: The opt-in animated Home accent renders above Home content surfaces while remaining subtle and battery-friendly.

## 1.1.0-preview.2

This preview adds optional appearance personalization while keeping the default experience unchanged.

### Appearance personalization

- **Animated Home background**: Added an opt-in, subtle accent animation behind the Home screen.
- **Battery-friendly behavior**: The animation is slow and lightweight, and automatically pauses during Android Battery Saver or reduced-motion mode.
- **Home-only scope**: The effect is limited to the Home tab and does not interfere with playback, downloads, or other screens.

## 1.1.0-preview.3

- **Visible Home backdrop**: Improved the opt-in animation rendering so its subtle accent motion remains visible above Home content surfaces.

## Initial release 1.0.0

This current-code release includes the main QuantumMPV features and the latest About-screen visual improvements.

### Visual polish

- Added a subtle automatic liquid-style shimmer to the About support card.
- Improved About-screen attribution contrast with solid-white writing text.

The first stable QuantumMPV release, bringing the current playback, library, streaming, download, customization, and branding work together in one public build.

### New features
- **Share-to-Quick-Download**: Send a supported web link to QuantumMPV from another Android app and choose Best available, up to 1080p, up to 720p, up to 480p, or Audio only before adding it to the persistent yt-dlp queue.
- **Library Insights**: Added an on-device diagnostics screen showing history entries, unique items, recent activity, missing local files, and network items without uploading playback data.
- **Download Quality Persistence**: The selected quick-download format is stored with the queue job and survives app restarts through the Room migration.
- **Support via UPI**: Added a clickable UPI support destination for `SimplyFahim@sbi` in the project documentation and websites.

### Fixes and release safety
- **Safe queue integration**: Quick downloads reuse the existing foreground yt-dlp service and durable queue rather than creating a second downloader.
- **Room migration**: Added a backward-compatible database migration for persisted download format selectors.
- **Website clarity**: Expanded the release site gallery and documented the new library and download workflows without replacing existing project details.

### Reliability & Release Safety
- **Durable yt-dlp Downloads**: yt-dlp download jobs are persisted in Room, restored after process recreation, and interrupted running jobs are requeued safely.
- **Atomic Download Queue Claims**: Only one worker can claim a queued yt-dlp job, preventing duplicate execution after service restart.
- **Foreground Download Recovery**: The download service requests intent redelivery so Android can restart the queue after process termination.
- **Database Migration Coverage**: The new database version and yt-dlp queue migration are covered by instrumentation tests.
- **CI Diagnostics**: Lint report collection and release APK artifact validation are more reliable.

### Branding
- **QuantumMPV Logo**: The supplied logo is applied across launcher assets, raster fallbacks, landing-page branding, favicon metadata, and repository presentation assets.

## Historical upstream release notes

Release notes from the upstream **mpvRx** project (its 1.3.x – 2.6.0 line) are no
longer mixed into this file — QuantumMPV keeps its own version sequence starting
at 1.0.0. The full upstream history that used to live here is preserved in:

- [git history](https://github.com/Fahimofficial/QuantumMPV/blob/44b6dca878c626366b8ca78a7f63fbe066d61b37/CHANGELOG.md) — the complete previous version of this file, and
- the upstream repository: [Riteshp2001/mpvRx CHANGELOG](https://github.com/Riteshp2001/mpvRx/blob/master/CHANGELOG.md).
