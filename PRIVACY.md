# Privacy Policy

**QuantumMPV does not collect, transmit, or sell any personal data.**

Last updated: 2026-10-31

## No analytics, no tracking, no ads

QuantumMPV contains no analytics SDKs, no advertising SDKs, no crash-reporting
service, and no telemetry. Crash details are shown on the device and stored
locally; they are not uploaded. Nothing about your usage, your library, or your
device is sent to the developer.

## Data stored on your device only

The app stores the following locally, and only on your device:

- your settings and playback preferences
- playback history and resume positions
- playlists, folder shortcuts, and cached media metadata (titles, durations,
  thumbnails, chapter and track information)

Uninstalling the app, or clearing its storage, removes this data.

## Saved credentials

Passwords, tokens and API keys you enter are stored in the app's private
storage. Passwords for network shares, Jellyfin, Navidrome and Audiobookshelf
credentials, and API keys for AI and subtitle providers are encrypted with an
Android Keystore key (AES-GCM). One exception: the Syncplay room password is
currently stored unencrypted in the app's private preferences.

## Network access

QuantumMPV makes network requests when you use a feature that needs them:

- opening a network stream, URL, playlist, torrent or magnet link you provide
- browsing network shares (SMB, FTP, SFTP, WebDAV) or media servers (Jellyfin,
  Navidrome/Subsonic, Audiobookshelf, Seerr) that you add
- casting to a device you select on your local network
- searching for subtitles (Wyzie, SubHub), lyrics (LRCLIB), or skip segments
  (IntroDB, TheIntroDB, AniSkip, Anime Skip)
- AI features (subtitle translation, speech-to-subtitles, renaming), which send
  the relevant subtitle text, audio or file names to the provider you configured
  with your own API key
- installing or updating yt-dlp, which is downloaded from the yt-dlp GitHub
  releases and checked against its published SHA-256 sums
- checking for or downloading app updates from this project's GitHub releases.
  Automatic update checks are off by default.

The app does not contact any developer-operated server in the background. When
you use a third-party service, that service's own privacy policy applies.

## Permissions

| Permission | Purpose |
| --- | --- |
| Media and storage (`READ_MEDIA_*`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, optional `MANAGE_EXTERNAL_STORAGE`) | Listing and playing your media, and file operations you request |
| `INTERNET` | The network features listed above |
| `RECORD_AUDIO` | Audio visualizers. Android's visualizer API requires this permission; the app does not record or send audio from the microphone. |
| Notifications and foreground services | Playback controls, background playback and downloads |
| `REQUEST_INSTALL_PACKAGES` | Installing an update you chose to download |
| `USE_BIOMETRIC` | Optional biometric unlock for the Secure Folder |
| `MODIFY_AUDIO_SETTINGS` | Audio focus handling during playback |

No permission is used for data collection.

## Third parties

QuantumMPV is built on open-source components (mpv/libmpv, FFmpeg, and the
mpv-android/mpvKt lineage). These components do not add tracking. If you stream
from a third-party service or cast to a third-party device, that provider's own
privacy policy applies to that connection.

## Children

The app is not directed at children and collects no data from anyone.

## Changes

Any change to this policy will be published in this file and noted in
`CHANGELOG.md`.

## Contact

Questions about privacy: see [SUPPORT.md](SUPPORT.md).
