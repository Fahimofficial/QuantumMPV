# Changelog
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
