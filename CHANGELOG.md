# Changelog

These notes are written in plain English and focus on changes that matter in everyday use.

## 1.1.0

The first stable QuantumMPV 1.1.0 release, bringing the current playback, library, streaming, download, appearance, and branding work together in one public build.

### What's Changed

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
