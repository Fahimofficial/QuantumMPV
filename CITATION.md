# Credits and citation information

QuantumMPV is an independent continuation and rebrand of the mpvRx codebase, maintained by **FAHIM**. This page preserves QuantumMPV's acknowledgments and links to the people and projects being credited. The QuantumMPV name and logo identify this repository; they do not replace or claim ownership of upstream projects.

## Maintainer

FAHIM

## Citing QuantumMPV

When mentioning, reviewing, or building on QuantumMPV, link to the [project repository](https://github.com/Fahimofficial/QuantumMPV). Include the release tag or commit you used so others can identify the same version.

Suggested plain-text reference:

```text
QuantumMPV contributors. QuantumMPV — Android video and music player.
https://github.com/Fahimofficial/QuantumMPV
Version: <release tag or commit hash>
```

## Inspiration and application credits

QuantumMPV is developed independently using ideas and patterns learned from the wider open-source media-player ecosystem. The following projects are inspirations and references, not claims of ownership or endorsement:

| Project | Role in the inspiration set |
|---|---|
| [MpvRx](https://github.com/Riteshp2001/mpvRx) | Upstream Android libmpv player codebase, playback features, and customization ideas |
| [Mpv-infinity](https://github.com/ZHINFINITY/Mpv-infinity) | Android mpv player UI and feature ideas |
| [VLC for Android](https://code.videolan.org/videolan/vlc-android) | Media-library, playback UX, and Android player conventions |

Third-party libraries and assets retain their own copyrights and licenses. See the sections below and the accompanying license notices for details.

## Credits & Third-Party Licenses

The groups below organize the existing credits by project type; they are not an exhaustive dependency inventory or a claim that every project is bundled in the app.

### Playback & player projects

| Project | Source |
|---|---|
| mpv-android | [mpv-android repository](https://github.com/mpv-android/mpv-android) |
| MpvEx | [marlboro-advance/mpvEx repository](https://github.com/marlboro-advance/mpvEx) |
| mpvKt | [abdallahmehiz/mpvKt](https://github.com/abdallahmehiz/mpvKt) |
| MpvRex | [sfsakhawat999/mpvRex](https://github.com/sfsakhawat999/mpvRex) |
| Next Player | [anilbeesetti/nextplayer](https://github.com/anilbeesetti/nextplayer) |

### Music & media apps

| Project | Source |
|---|---|
| PixelPlayer | [theovilardo/PixelPlayer](https://github.com/theovilardo/PixelPlayer) |
| Gramophone | [FoedusProgramme/Gramophone](https://github.com/FoedusProgramme/Gramophone) |
| AFinity | [MakD/AFinity](https://github.com/MakD/AFinity) |
| Chora | [CraftWorksMC/Chora](https://github.com/CraftWorksMC/Chora) |

### Android and application foundations

| Project | Role | Source |
|---|---|---|
| Android Jetpack / AndroidX | Android lifecycle, activity, storage, Media3, Room, biometric, profile installer, and core platform integrations | [AndroidX](https://developer.android.com/jetpack/androidx) |
| Jetpack Compose | Declarative UI and Material 3 interface components | [Compose](https://developer.android.com/jetpack/compose) |
| Kotlin Coroutines | Structured concurrency and asynchronous work | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| Koin | Dependency injection | [Koin](https://insert-koin.io/) |
| Coil | Image loading and caching | [Coil](https://github.com/coil-kt/coil) |
| Accompanist | Supplementary Compose integrations | [Accompanist](https://github.com/google/accompanist) |

### Media, network, and utility libraries

| Project | Role | Source |
|---|---|---|
| mpv / libmpv | Playback engine | [mpv-player/mpv](https://github.com/mpv-player/mpv) |
| mpvlibAndroid | Prebuilt libmpv AARs (`standard`, `noVulkan` and `fongmi` builds) pinned in `native-artifacts.json` | [Riteshp2001/mpvlibAndroid](https://github.com/Riteshp2001/mpvlibAndroid) |
| libplacebo | GPU rendering used by mpv's `gpu-next` output | [haasn/libplacebo](https://github.com/haasn/libplacebo) |
| FFmpeg | Demuxing and decoding inside libmpv | [FFmpeg](https://ffmpeg.org/) |
| yt-dlp | Website media extraction and download support. Downloaded at runtime from its GitHub releases, not bundled. | [yt-dlp](https://github.com/yt-dlp/yt-dlp) |
| CPython | Bundled Python 3.13 runtime that runs yt-dlp | [python/cpython](https://github.com/python/cpython) |
| QuickJS-NG | Vendored JavaScript engine (`app/src/main/cpp/third_party/quickjs`) used by yt-dlp | [quickjs-ng/quickjs](https://github.com/quickjs-ng/quickjs) |
| AndroidX Media3 | Media session and notifications, plus Transformer for clip export and video compression. Playback stays on libmpv. | [Media3](https://developer.android.com/media/media3) |
| Room | Local database | [Room](https://developer.android.com/training/data-storage/room) |
| Sora Editor | Built-in code editor with TextMate highlighting | [Rosemoe/sora-editor](https://github.com/Rosemoe/sora-editor) |
| Haze | Blur and glass effects in Compose | [chrisbanes/haze](https://github.com/chrisbanes/haze) |
| smbj | SMB client | [hierynomus/smbj](https://github.com/hierynomus/smbj) |
| Apache Commons Net | FTP client | [apache/commons-net](https://github.com/apache/commons-net) |
| sardine-android | WebDAV client | [thegrizzlylabs/sardine-android](https://github.com/thegrizzlylabs/sardine-android) |
| NanoHTTPD | Local HTTP server for casting local files | [NanoHttpd/nanohttpd](https://github.com/NanoHttpd/nanohttpd) |
| libtorrent4j | Torrent and magnet streaming, built on libtorrent | [aldenml/libtorrent4j](https://github.com/aldenml/libtorrent4j), [arvidn/libtorrent](https://github.com/arvidn/libtorrent) |
| libarchive-android | Archive handling | [zhanghai/libarchive-android](https://github.com/zhanghai/libarchive-android) |
| ComposePreference | Settings screens | [zhanghai/ComposePreference](https://github.com/zhanghai/ComposePreference) |
| jsoup | HTML parsing | [jhy/jsoup](https://github.com/jhy/jsoup) |
| Seeker | Compose seek bar | [abdallahmehiz/seeker](https://github.com/abdallahmehiz/seeker) |
| Multiplatform Markdown Renderer | Markdown rendering in Compose | [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) |
| Reorderable | Drag-to-reorder lists | [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) |
| LazyColumnScrollbar | List scrollbars | [nanihadesuka/LazyColumnScrollbar](https://github.com/nanihadesuka/LazyColumnScrollbar) |
| FSAF | Storage Access Framework helper | [K1rakishou/Fuck-Storage-Access-Framework](https://github.com/K1rakishou/Fuck-Storage-Access-Framework) |
| CrashX | On-device crash screen; reports are not uploaded | [TutorialsAndroid/crashx](https://github.com/TutorialsAndroid/crashx) |
| MediaInfo | Detailed container, video, audio, subtitle, and image metadata | [MediaInfo](https://mediaarea.net/MediaInfo) |
| OkHttp | HTTP networking and streaming support | [OkHttp](https://github.com/square/okhttp) |
| JSch | SFTP connectivity | [mwiede/jsch](https://github.com/mwiede/jsch) |
| Google Cast | Cast discovery and remote playback controls | [Cast SDK](https://developers.google.com/cast) |
| Material Symbols | Interface iconography | [Material Symbols](https://github.com/google/material-design-icons) |

### Video processing & streaming

| Project | Use | Source |
|---|---|---|
| hdr-toys | HDR tone-mapping, gamut-mapping and transfer-function shaders bundled in `assets/shaders/hdr-toys` | [natural-harmonia-gropius/hdr-toys](https://github.com/natural-harmonia-gropius/hdr-toys) |
| Anime4K | Upscaling and restoration shaders bundled in `assets/shaders/anime4k` | [bloc97/Anime4K](https://github.com/bloc97/Anime4K) |
| ArtCNN | Architecture of the `Ani4Kv2_ArtCNN` shader; weights trained by Sirosky | [Artoriuz/ArtCNN](https://github.com/Artoriuz/ArtCNN) |

### Online services

The app can query these services when you use the related feature. They are not affiliated with QuantumMPV.

| Service | Use |
|---|---|
| [Wyzie](https://sub.wyzie.io) | Subtitle search and TMDB metadata |
| [LRCLIB](https://lrclib.net) | Online lyrics |
| [TheIntroDB](https://theintrodb.org), IntroDB, [AniSkip](https://aniskip.com), [Anime Skip](https://anime-skip.com) | Intro, recap and credits timestamps |
| [Jellyfin](https://jellyfin.org), [Navidrome](https://www.navidrome.org) / [Subsonic API](https://www.subsonic.org/pages/api.jsp), [Audiobookshelf](https://www.audiobookshelf.org), [Seerr](https://github.com/seerr-team/seerr) | Self-hosted media servers you connect to |
| [Syncplay](https://github.com/Syncplay/syncplay) | Synchronized playback rooms (protocol compatibility) |

### Licenses of bundled components

These components ship inside the app and keep their own licenses. Their notices remain in the files listed.

| Component | License | Notice location |
|---|---|---|
| QuickJS-NG | MIT. © Fabrice Bellard, Charlie Gordon, Ben Noordhuis | `app/src/main/cpp/third_party/quickjs/LICENSE` |
| Anime4K shaders | MIT. © 2019-2021 bloc97 | Header of each `Anime4K_*.glsl` |
| `Anime4K_AutoDownscalePre_x2.glsl` | Unlicense (public domain dedication) | File header |
| `Ani4Kv2_ArtCNN_C4F32_i2_CMP.glsl` | Weights: CC BY-NC 4.0 (trained by Sirosky). Architecture: MIT, © 2024 Joao Chrisostomo | File header |
| Go Noto Current (`GoNotoCurrent-Regular.ttf`), from [Go Noto Universal](https://github.com/satbyy/go-noto-universal) | SIL Open Font License 1.1. © 2022 The Noto Project Authors | `app/src/main/assets/subtitle-fonts/OFL.txt` |
| TextMate grammars | The JavaScript grammar is derived from [microsoft/TypeScript-TmLanguage](https://github.com/microsoft/TypeScript-TmLanguage) and the Lua grammar from [sumneko/lua.tmbundle](https://github.com/sumneko/lua.tmbundle), as stated in the grammar files | `app/src/main/assets/textmate/` |
| hdr-toys shaders | See the [upstream repository](https://github.com/natural-harmonia-gropius/hdr-toys). The bundled copies carry no license header. | `app/src/main/assets/shaders/hdr-toys/` |
| libmpv, FFmpeg, libplacebo | See [mpvlibAndroid](https://github.com/Riteshp2001/mpvlibAndroid) and the upstream projects for the licenses of the prebuilt AARs | Release AARs pinned in `native-artifacts.json` |

QuantumMPV's own source is AGPL-3.0-or-later, as declared by the SPDX header in each Kotlin file and in [LICENSE](LICENSE).

### Individual contributions

| Contributor | Acknowledgment |
|---|---|
| [SunnyVishnu3](https://github.com/SunnyVishnu3) | `yt-dlp` native integration and SDK 29+ bypass logic |

Thank you also to everyone contributing code, translations, documentation, bug reports, and testing. The [GitHub contributor history](https://github.com/Fahimofficial/QuantumMPV/graphs/contributors) records repository contributions.

## Keeping credits accurate

Preserve upstream copyright and license notices when adapting code or assets. These acknowledgments supplement, rather than replace, the [project license](LICENSE) and notices accompanying third-party work.

If a credit is missing or incorrect, [open an issue](https://github.com/Fahimofficial/QuantumMPV/issues/new) or a pull request with the relevant source link and the attribution to add or correct.

[Back to the README](README.md#acknowledgements)
