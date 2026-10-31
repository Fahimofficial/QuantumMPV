# Changelog
## Unreleased

### What's New

- **Undo**: Removing videos from a playlist or items from Recents shows an Undo action that puts them back exactly where they were. Removals that also delete files still ask first and can't be undone.
- **Reordering playlists**: Dragging a playlist item lifts it slightly and gives a haptic tick for each slot it crosses and a confirmation when dropped.
- **Pull to refresh**: The list follows your finger, resists progressively past the trigger point, and ticks once when releasing would refresh.
- **Lists**: Removing or re-sorting items in Recents and Playlists animates rows into place instead of jumping. Thumbnails that take time to load fade in.
- **Design system**: QuantumMPV now follows a documented visual language ("Quiet Geometry", see docs/design). Player panels share one header with a standard-size, labelled close button; player sheets show a drag handle for their swipe-to-dismiss; player buttons give the same press feedback as the dock.
- **Reduced motion**: Screen transitions now cross-fade under Reduced motion and are removed when motion is Off.

### Fixes

- **Player**: The top and bottom shading behind the player controls now fades out with them instead of staying over the video.
- **Haptic feedback setting**: Turning off Haptic feedback now silences every in-app haptic, including player gestures, tab taps and long-press.
- **Reduced motion in the player**: Every controls animation style, the side panels, the buffering indicator and the video-open animation fall back to a simple fade.
- **Player sheets**: The dimmed background follows the sheet as you drag it, and a quick upward flick opens it.
- **Swipe to seek**: Seeking starts at +0:00 instead of jumping by the distance the finger moved before the gesture engaged. Volume gestures give one tick at mute, 100% and the boost limit.
- **Text**: Recents and playlist remove dialogs, and the Recents "disabled" message, are now translatable.
- **Skip markers**: TheIntroDB lookups use its v4 API. v1 has been removed and v2/v3 are being shut down, so intro/credits markers from TheIntroDB would otherwise stop appearing.
- **Network playback**: WebDAV and other network files whose names use decomposed Unicode (common for names created on macOS) no longer fail with HTTP 404.
- **Ambient mode**: The vignette fade no longer relies on GLSL behaviour that differs between GPU drivers.
- **Subtitles**: Turning on a secondary subtitle no longer forces the primary ASS subtitle's styles to be overridden.
- **Player settings**: Option changes made after playback starts are applied as runtime properties, as libmpv requires, so they take effect reliably.
- **Navigation bar**: With three-button navigation, the system buttons get Android's contrast scrim again, so they stay visible over artwork and wallpapers.
- **Library and Tree**: The first load of Library and Tree shows the same scanning state as Folders instead of a bare spinner.
- **Touch targets**: The More sheet's info button and the mini-player close button are easier to hit.
- **Opening videos**: The player no longer reads the file's metadata on the main thread to pick the initial orientation, which could freeze or ANR when opening large files or files from cloud/SAF providers.
- **Saved subtitles**: Subtitles found in the subtitle save folder are now auto-selected reliably; the auto-select no longer races the tracks being added.
- **Privacy**: Release builds no longer write debug logs, and Navidrome/stream URL error logs no longer include login tokens or API keys.
- **Memory**: Large embedded and remote cover art is decoded at a reduced size instead of full resolution.
- **Accessibility**: Audio-player skip/seek buttons, player panel close buttons, speed steppers and the mini-player close button now have TalkBack labels.

## 1.4.0
### What's New
- **Two surface styles**: The surface picker now offers Classic and Clear Glass, and Classic is the default for new installs and for anyone who never chose a style. Saved legacy styles are mapped automatically: Soft, Frosted, Liquid and AMOLED Glass become Clear Glass, while Minimal and Cinema become Classic. Cinema keeps its no-tint/no-highlight behavior until it is migrated.
- **Library Insights**: A new screen, opened from the new floating menu, summarizes what you have played using only data already stored on this device: videos played, completed, unfinished, played in the last 7 days, plus "Continue watching" and "Recently played" lists. Videos that no longer exist are ignored, and the screen follows the Recently Played setting, showing nothing when it is off. Total watch time is not shown because it is not tracked yet.
- **Responsive floating dock**: The overflow control is pinned inside the navigation pill and the separate circular Search action sits immediately beside it. On narrow screens, tab labels collapse and the 48 dp tab targets scroll inside the pill instead of colliding with Search or hiding destinations.
- **Contextual Search**: The floating Search action now opens the active tab’s existing search and keyboard for Home, Music, Recents, Playlists, Network, and Jellyfin. The top-bar Search action is suppressed only while the floating dock owns it, avoiding duplicates; nested screens retain their normal action.
- **Glass menu**: The Settings and Library Insights panel now renders in-window through the shared lightweight Haze glass treatment, expands above its overflow trigger, dismisses on outside tap or Back, and follows Off/Reduced motion settings.
- **Library search screen**: Home (album view) search is now its own screen. The dock's Search circle morphs into the search bar, and the dock, mini player and quick-play FAB step aside so results and the keyboard get the full height. Recent searches are kept on the device and shown while the field is empty.
- **Morphing play/pause**: Player, audio player and mini-player play/pause buttons now morph between the triangle and the two bars.
- **Dock and menu motion**: The selected-tab pill slides to the new tab on a spring, including jumps across several tabs, and overflow menu tiles shrink slightly on press.

### Fixes
- **Search**: Searching videos finds them again: matching is case-insensitive on file name and path and reads the library directly instead of a media index that was never filled. An empty search box no longer shows "No results found".
- **Upgrade crash**: Upgrading from v1.0/v1.1 (database version 23) or a preview build (version 24) no longer fails to open the library database. The new migration creates every table and column added since, and is covered by upgrade-path tests.
- **Secure folder**: Other apps can no longer ask the player to open files in QuantumMPV's private storage, which previously allowed hidden secure-folder videos to play without the PIN.
- **Updates**: Update APKs download to a temporary file and are installed only after their size and published SHA-256 match. An interrupted download is no longer offered as ready to install.
- **Saved secrets**: Seerr, AI-provider and subtitle-provider API keys and Audiobookshelf tokens are now encrypted at rest; existing values are migrated automatically. Backups skip secure-folder media and regenerable thumbnails.
- **Resume position**: Playback progress is saved every 15 seconds while playing, so a crash or low-memory kill loses at most 15 seconds of progress.
- **Floating dock**: The Search circle now matches the navigation pill's height and surface, and the Settings / Library Insights menu uses a solid surface in Classic appearance instead of glass.
- **Surface opacity**: The glass "Surface opacity" slider now visibly changes how see-through glass surfaces are.
- **Library errors**: A failed library scan shows an error with a "Try again" button instead of looking like an empty library.
- **Sharing**: Sharing Media Info and Lua scripts works again (the shared files were outside the provider's allowed folders).
- **Accessibility and motion**: Selection-bar buttons keep a 48 dp touch target and have translated labels; cards announce their selected state; PiP controls are translated; idle pulses and shimmers stop under Reduced/Off motion, and the system "Remove animations" setting now applies immediately.
- **Themed icon**: The monochrome launcher and notification icon now show the QuantumMPV ring-and-play mark.

## 1.3.1-preview.3
This preview builds on v1.3.1-preview.2 with smoother navigation, floating app search, and refinements to glass surfaces and player controls.
### What's New
- **Surface style choices**: Keep four distinct styles—Classic, Frosted Glass, Clear Glass, and Liquid Glass—while mapping saved legacy styles to the closest retained option and preserving Cinema's no-tint/no-highlight behavior.
- **Smoother navigation**: Reduce jitter when switching between distant tabs, defer navigation geometry updates until transitions settle, and use lighter glass rendering.
- **Floating app search**: Open Settings Search from a quick-access floating button that hides when space is limited or selection mode is active.
- **Player and browser polish**: Improve HUD contrast while controls are visible, keep multi-column card titles consistent while honoring the full-name preference, and constrain audio/equalizer sheets to 460 dp in landscape.

## 1.3.1-preview.2
This preview builds on v1.3.1-preview.1 with the completed Glass & Surface Style integration.
### What's New
- **Consistent surface styling**: Apply the selected appearance across browser video cards, navigation, player controls, the audio mini-player, and audio/equalizer sheets.
- **Appearance choices**: Make Liquid Glass the default while retaining Classic and Minimal styles; navigation presentation now follows the selected surface style.
- **Style compatibility**: Preserve selected-card styling and legacy navigation behavior, and normalize imported tint settings for supported styles.

## 1.3.0
Preview release containing the compatible mpvRx 2.7.0, 2.7.1, and 2.7.2 improvements selected for QuantumMPV without replacing its custom playback, subtitle, configuration, or release architecture.
### What's New
- **Playback and playlist reliability**: Improved network playlist persistence and refresh behavior, media counting, subtitle layout handling, and Android TV D-pad seeking.
- **Subtitle improvements**: Added verified Unicode fallback-font support, independent secondary-subtitle scale and position controls, RTL compatibility, and safer subtitle option handling.
- **Player and appearance improvements**: Added settings-search coverage, OSD font preference handling across reused player cores, mini-player refinements, and compatible visual polish.
- **Build and compatibility updates**: Preserved 16-KB native page alignment, pinned native artifacts, and updated compatible build and dependency infrastructure.
### Fixes
- **D-pad seeking**: Avoid seeking to zero for unknown-duration media and ignore key-up events that did not start a scrub.
- **Network playlists**: Preserve persisted network references during playlist refreshes and include them in playlist totals.
- **Subtitle positioning**: Correct automatic secondary-subtitle compensation during zoom and pan.
- **Configuration ownership**: Continue honoring user-owned `mpv.conf` options while avoiding unsupported secondary subtitle writes.
- **Security and integrity**: Verify the bundled subtitle font using its expected size and SHA-256 checksum before installation.

## 1.2.0
The second stable QuantumMPV release, bringing the 1.2.0 preview cycle's media-server integrations, network settings redesign, secure-folder hardening, and playback reliability work together in one public build.
### What's New
- **Audiobookshelf support**: Media Server settings now include Audiobookshelf connection and full server management alongside Jellyfin, Seerr, and Navidrome.
- **Dedicated Network section**: Media Servers and Network destinations are grouped under a new Network section, with P2P streaming, HLS proxy, and the yt-dlp Manager moved out of Advanced.
- **Theme customizer and wallpapers**: Ported the custom theme customizer and wallpaper selection screens for deeper personalization.
- **Advanced gestures**: Restored configurable swipe action zones and nested tab gesture support.
- **Server music playback**: Navidrome and Subsonic server playback is fully integrated into the QuantumMPV media pipeline.
### Fixes
- **Folder discovery**: Videos now appear even when Android MediaStore has not indexed them, with a direct filesystem scan covering Movies, external storage, and USB/OTG volumes.
- **Stale index handling**: Folder contents are read directly and reconciled with MediaStore every time a folder opens, so folders are never visibly empty.
- **Video startup and surfaces**: Video tracks stay enabled while mpv initializes, and playback survives Activity, PiP, and background Surface transitions without opening to a blank screen.
- **Secure Folder protection**: Biometric unlock is now cryptographically bound to an Android Keystore-backed cipher, with device-credential fallback preserved.
- **Build reliability**: Pinned native artifacts, hardened dependency resolution, and corrected instrumentation-test APK handling across CI.
## 1.2.0-preview.8
This hotfix fixes the remaining case where folders were visible but their videos did not appear.
### Hotfix
- **Folder contents**: Read the selected folder directly instead of relying only on the asynchronous media index.
- **Stale MediaStore/index data**: Reconcile actual filesystem files with MediaStore every time a folder opens.
- **Folder discovery**: Keep filesystem discovery for Movies, external storage, and USB/OTG volumes.
## 1.2.0-preview.7
This hotfix makes videos appear in QuantumMPV folders even when Android MediaStore has not indexed them yet.
### Hotfix
- **Folder discovery**: Supplement MediaStore with a direct filesystem scan.
- **Movies and external storage**: Cover the standard Movies directory and external/OTG volumes.
- **Existing customizations**: Keep the current QuantumMPV browser and playback behavior intact.
## 1.2.0-preview.6
This hotfix restores video playback when the player Surface is created, recreated, or temporarily lost.
### Hotfix
- **Video loading**: Keep video tracks enabled while mpv initializes so videos do not open to a blank screen.
- **Surface transitions**: Preserve mpv demuxer buffering across Activity, PiP, and background Surface changes.
- **Renderer startup**: Defer only the renderer attachment until Android provides a valid Surface.
- **Orientation**: Retain the launch orientation until valid video aspect metadata is available.

## 1.2.0-preview.5
This preview republishes the completed Network settings and Audiobookshelf integration to the public preview channel.
### What's New
- **Network section**: Media Servers and Network are grouped under a dedicated Network section.
- **Audiobookshelf**: Media Server settings include Audiobookshelf connection and server management.
- **Network tools**: P2P streaming, HLS proxy, and yt-dlp Manager are grouped under Network instead of Advanced.

## 1.2.0-preview.4
This preview reorganizes network settings and completes Audiobookshelf server management.
### What's New
- **Network settings**: Added a dedicated Network section with separate Media Servers and Network destinations.
- **Audiobookshelf settings**: Added connect, edit, switch, delete, and add-another-server controls alongside Jellyfin, Seerr, and Navidrome.
- **Simpler navigation**: Moved P2P streaming, HLS proxy, and the yt-dlp manager out of Advanced into Network.

## 1.2.0-preview.3
This preview includes the secure-folder authentication hardening and Android CI reliability fixes.
### What's New
- **Secure Folder protection**: Biometric unlock is now cryptographically bound to an Android Keystore-backed cipher, while device-credential fallback remains available.
- **Android CI reliability**: Instrumentation-test APKs are no longer incorrectly treated as application APKs during native artifact verification.
- **Dependency and native hardening**: Pinned native artifacts and patched dependency resolution remain enforced across build and release workflows.


These notes are written in plain English and focus on changes that matter in everyday use.

## 1.2.0-preview.2

This preview brings the highly anticipated missing features from mpvRx 2.6.0 into QuantumMPV, resolving all compatibility barriers.

### What's New

- **Audiobooks & Servers**: Fully ported the Audiobookshelf integration and Navidrome/Subsonic server playback support into the new QuantumMPV media pipeline.
- **Themes & Wallpapers**: Ported the custom theme customizer and wallpaper selection screens.
- **Advanced Gestures**: Restored and integrated the configurable swipe action zones and nested tab gesture support.
- **Core Reliability**: Resolved 100+ architectural merge conflicts and compile errors introduced by the deep codebase rename and refactor, aligning the upstream features with the modern SQLite-based FTS library approach.


## 1.2.0-preview.1

This preview brings the latest media-library, download, playback, and build reliability work together for testing before the next stable release.

### What's New

- **Unified media library**: Added a Room-backed media index with SQLite full-text search and a WorkManager-powered filesystem scan.
- **More reliable playback**: Improved external playlist handling, network lifecycle cleanup, pause latency, and video-output safeguards.
- **Safer downloads**: Made yt-dlp output names collision-safe and kept temporary download files isolated during cleanup.
- **Build reliability**: Restored shared duration formatting, completed the media scanner dependencies, and verified the Android CI, release build, static-analysis, and preview pipelines.

### APK variants

Preview packages use the same variant layout as the stable release: Standard Universal and architecture-specific APKs, FongMi Universal, and Non-Vulkan Universal.

## 1.1.0

The first stable QuantumMPV 1.1.0 release, bringing the current playback, library, streaming, download, appearance, and branding work together in one public build.

### What's New

- **Liquid Glass appearance option**: Added an opt-in appearance switch for translucent glass treatments while keeping the solid, battery-friendly default experience.
- **Appearance polish**: Refined settings cards, bottom navigation, Home accents, motion behavior, and contrast across light and dark themes.
- **Share-to-Quick-Download**: Send supported web links to QuantumMPV from another Android app and choose Best available, up to 1080p, up to 720p, up to 480p, or Audio only.
- **Durable download queue**: yt-dlp jobs are persisted in Room, recover safely after process recreation, and use atomic claims to prevent duplicate execution.
- **Download quality persistence**: The selected quick-download format is stored with the queue job and survives app restarts.
- **Library Insights**: Added an on-device diagnostics view for history entries, unique items, recent activity, missing local files, and network items without uploading playback data.
- **Branding refresh**: Applied the QuantumMPV logo and stable app labels across the launcher, fallback assets, landing page, favicon metadata, and repository presentation assets.
- **Support and credits**: Updated developer support information and expanded project credits and links in the app and project documentation.
- **Release reliability**: Improved Room migration coverage, structured diagnostics, release APK validation, and CI artifact handling. Made both detekt and ktlint blocking while fixing existing formatting findings.

### APK variants

All variants contain the same QuantumMPV app version and features, with bundled native mpv backends. Choose the package that best matches your device:

- **Standard** (`QuantumMPV-<architecture>-<version>.apk`): Recommended for most devices. Available as Universal and architecture-specific APKs with the standard Vulkan-capable playback backend.
- **FongMi** (`QuantumMPV-fongmi-<version>.apk`): Universal package using the FongMi native profile for Vulkan, direct MediaCodec, and Dolby Vision playback.
- **Non-Vulkan** (`QuantumMPV-no-vulkan-<version>.apk`): Universal OpenGL-only package for devices without compatible Vulkan support or with unstable legacy GPU drivers.

## Initial release 1.0.0

The first public QuantumMPV release, bringing the core playback, library, streaming, download, customization, and branding work together in one stable build.

### Highlights

- Share supported links for quick yt-dlp downloads.
- Browse playback history and local media from the library.
- Use the supplied QuantumMPV branding across the app and project materials.
- Support the developer through the documented UPI destination `simplyfahim@axl`.

### Credits

QuantumMPV builds on open-source projects including mpvRx, MpvRex, MpvInfinity, Mpvex, and mpv-android. See [CITATION.md](CITATION.md) for the project links and full attribution details.
