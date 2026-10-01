# mpvRx upstream release audit: v2.7.0 and v2.7.1

**Repository audited:** `/home/ubuntu/QuantumMPV`  
**Audit branch/HEAD:** `integrate/upstream-v2.7.1` / `00be3d8dac6505c3bba1f36ec8b9b40566aa2203`  
**Release tags:** `v2.6.0` peeled at `89237c4c66e367cf3ac460208d77d3cac3d5e57c`, `v2.7.0` peeled at `6c14eba0a9a26eb3a15c7f99f71745927019d325`, and `v2.7.1` peeled at `d8dea2caac0aedb97fbf9ef1884fb242247237c4`.  
**Method:** read-only inspection of Git revision ranges, commit contents/statistics, current QuantumMPV paths, and the discovery inventory. No source files, refs, merges, or pushes were changed; the working tree was clean at discovery.

## Executive result

The discovery inventory accounts for **all 154 commits exactly once**:

- **151 commits** in `v2.6.0..v2.7.0`.
- **3 commits** in `v2.7.0..v2.7.1`.
- **154 total** assigned to **UP-01 through UP-08**, with no duplicate or unassigned commit.

This is **not a cherry-pick/merge recommendation**. QuantumMPV has a package migration (`app.gyrolet.mpvrx` → `com.quantummpv.app`), rewritten playback/session lifecycle, Liquid Glass navigation, subtitle-font fallback and configuration hardening, and other fork-specific behavior. The safe strategy is semantic, feature-sliced porting with tests and explicit Room/config migration review.

### Priority order

1. **P0 correctness and build/release safety:** port/validate native 16-KB alignment, mpv backend and dependency updates; preserve current playback lifecycle, subtitle fallback, config ownership, and offline-first behavior. Add no broad R8 rules without device evidence.
2. **P1 user-visible correctness:** Jellyfin logo/hero metadata, audiobook artwork/edit/search, cancellable download progress, TV focus/D-pad seek, ZIP/network-image navigation, lyric provider/cache correctness, secondary subtitles, and wallpaper/startup correctness.
3. **P1 new capabilities:** snapshot capture/library and home-screen media widget, after a QuantumMPV-native Room migration and Media3/service integration.
4. **P2 optional polish/admin:** Liquid Glass and visual refinements, Hall of Fame, watch statistics, responsive pickers, provider breadth, and release-note/CI presentation changes.

## Topic decision matrix

| Unit | Commits | Decision | Port scope and disposition |
|---|---:|---|---|
| **UP-01 Server integrations and remote-media metadata** | 10 | **Selective portable port** | Port Jellyfin logo/parent-logo parsing and logo fallback, responsive hero treatment, post-auth Jellyfin-tab enablement, safe RemoteImage alignment, audiobook online artwork/search/edit/update APIs, and live cancellable download progress. Adapt all paths to `com.quantummpv.app`; retain current OkHttp cache/backoff/dimension cap, Liquid Glass chrome, local audiobook playback, sidecar subtitles, and current download manager. Remote metadata is optional and must never block import/playback. |
| **UP-02 Player runtime, controls, seeking, and TV playback** | 34 | **Conditional; much already present** | Current QuantumMPV already covers generation-safe playback/session attachment, coalesced seeking/EOF guards, orientation, playlist behavior, OSD-font hardening, artwork transitions and background cleanup. Port only verified gaps: UI scale, TV sheet initial focus, D-pad seekbar scrubbing, guarded SMB original-path publication, and selected chapter/short-video behavior. Do not wholesale replace the player or add broad `dontoptimize`/Samsung rules. |
| **UP-03 Browser, media library, playlists, ZIP archives, and file navigation** | 38 | **Conditional, with several portable feature slices** | Manual grids, core M3U/Xtream playlists, network reconnect identity, recently-played/resolution metadata, and core navigation are already present or stronger. Port network-file long-press-to-playlist, optional local IPTV discovery, ZIP browsing/read-only playlists, network-image viewer, optimistic deletion with rollback, and responsive picker behavior. GuessIt is an opt-in fallback only. ZIP requires a QuantumMPV Room migration and SAF/path-traversal tests; do not replace current deletion/security policies. |
| **UP-04 Music, audio presentation, lyrics, and subtitle features** | 17 | **Conditional; preserve incompatible audio stack** | Retain current audio routing, generation guards, artwork/checkpoints, lyric strip/translation and subtitle-font hardening. Add provider registry/timeouts, synced-first selection, disk/provider cache, RTL handling, secondary-subtitle scale/position, album-track ordering/fast scroll, and small tablet fixes. Equalizer is an explicit compatibility decision: upstream MPV dynamic/manual seven-band filtering conflicts with current Android `Equalizer`/`LoudnessEnhancer`; add a separate guarded mode or retain current implementation, never silently overwrite config-owned `af`. Workflow hunks inside `6f303654` are release-only and must be reviewed separately. |
| **UP-05 Appearance, wallpaper, Liquid Glass, and visual interaction polish** | 26 | **Conditional selective port; detailed sub-audit had a schema failure** | Direct Git inspection confirms wallpaper editor/presets/previews/color extraction, startup-flash fixes, Liquid Glass surfaces/navigation blur, mini-player clipping/readability, expressive-component cleanup, switch motion and downloadable fonts. Port only deltas absent from current QuantumMPV after visual comparison. Preserve QuantumMPV Liquid Glass navigation, wallpaper/theme APIs, status-bar behavior, TV focus, localization and custom settings; test light/dark, portrait/landscape, loading and no-wallpaper states. Do not replace whole shared UI files. |
| **UP-06 Snapshots and home-screen media widgets** | 7 | **New portable feature, high effort** | Add QuantumMPV-native frame-capture/snapshot entities, DAOs, repositories, lossless capture integration and UI; add a Room migration from current version 25 rather than copying schemas 29–31. Adapt widget controls to the current Media3/session/service command path and bounded artwork cache. Preserve existing `ScreenshotSaver`, Liquid Glass navigation, wallpaper/theme behavior and `PlaybackSession`; do not use old direct service constants or detached `fd://`/memory sources. |
| **UP-07 Settings, onboarding, contributor surfaces, and app administration** | 8 | **Conditional selective port** | Network summary/manual settings export are already present. Port Hall of Fame, optional GitHub data/cache, watch statistics, automatic backup rooted in Configuration, SAF child-directory persistence, onboarding CONFIGURATION restore, and configurable byte-bounded logs. Keep current settings/search/Liquid Glass/TV focus/subtitle-font fallback. Backup must use the selected tree and safe seven-file retention; stats must be session-generation aware and exclude pauses/cache stalls; remote contributor data must never block About. |
| **UP-08 Build, dependency, CI, preview, and release preparation** | 14 | **Port validated build changes; separate release-only metadata** | Prioritize 16-KB ELF alignment, Gradle/Kotlin/npm/image-size/dependency updates, Compose Material3 bump, mpv backend 1.0.10, compiler heap, and CI distribution parallelization after a clean debug/release build and dependency/security review. Preview identity/commit-title and `2.7.0`/`2.7.1` changelog/version commits are release-only. Merge/dependabot wrapper commits have no independent feature delta. Never let release workflow changes alter QuantumMPV signing/flavor/artifact policy without review. |

## Detailed findings and port boundaries

### UP-01 — Server integrations and remote-media metadata

**Already present or stronger in QuantumMPV:** package-rebranded Audiobookshelf/Jellyfin/Seerr integrations; injected OkHttp RemoteImage with memory/disk caching, failure backoff and a 1024-pixel cap; local/embedded audiobook art; server cover URLs; offline-first local playback and sidecar-subtitle paths; a download manager and active episode state.

**Genuine gaps:** Jellyfin `Logo`/`ParentLogo` fields and URL builder/rendering; responsive hero sizing/transition; setting `showJellyfinTab` only after successful authentication/save; drawable-backed header icon support compatible with the current RTL-aware `AppIcon`; iTunes/Open Library audiobook search/cache/edit/update flow; optional Audiobookshelf cover-url/quick-match endpoints; progress-aware and cancellable episode UI.

**Boundaries:** bound image/network response sizes, close responses, propagate cancellation, respect provider rate limits/privacy, and treat server-version-dependent quick-match/POST endpoints as error-tolerant. Unknown/zero progress is not completion. Add tests for logo precedence/fallback, cache behavior, auth failure, cover fallback, and queued/partial/completed/cancelled downloads.

### UP-02 — Player runtime, controls, seeking, and TV playback

**Already present:** `PlaybackSession` process/lifecycle safety; keyframe/exact seek coalescing and EOF/Syncplay guards; demuxer cache/read-ahead handling; launch orientation retention; service terminal cleanup; playlist drag/reorder/network metadata; artwork transitions; bundled OSD font hash/size verification and mpv.conf ownership checks.

**Port candidates:** apply UI-scale overrides in both activities; add TV-gated sheet content focus; make the seekbar focusable for repeated LEFT/RIGHT scrubbing; publish SMB original paths only with safe URI fallback; audit chapter-pill/long-press and short-video precise seeking against existing frame preview and Syncplay logic.

**Do not port blindly:** player/session files, surface teardown, quality-generation logic, proxy/config ownership, custom OSD/font fallback, Liquid Glass controls, or broad R8 optimization. Validate rotation, PiP/background, Surface loss/re-attach, Cast destruction, local/HTTP/HLS/M3U/Xtream/YTDLP quality gating, and near-EOF seeks.

### UP-03 — Browser, libraries, playlists, ZIP and file navigation

**Already present:** independent portrait/landscape/manual grid preferences and LCM spans; local/M3U/Xtream playlist persistence; network M3U import; stable recreated-connection playback identity; duplicate-safe recent history with dimensions/duration; metadata resolution cache; custom file navigation and playlist-sheet layout.

**Port candidates:** wire the existing `NetworkVideoCard` long-click into `AddToPlaylistDialog`; add fingerprinted/cancellable local IPTV discovery around current `M3UParser`; add ZIP SAF-safe browsing, nested entry playback/thumbnails, read-only playlist marker/badge and explicit open-folder navigation; add network-image classification/cache/viewer; add optimistic folder removal with rollback/reload; adapt picker dialogs to responsive sheets.

**Higher-risk items:** ZIP persistence needs migration from database version 25 and protection against path traversal, source deletion and stale SAF grants. The upstream network-image viewer is a new stack. GuessIt bundles Python wheels/assets and may increase APK size/startup; retain deterministic Kotlin parsing as the safe fallback. Do not bypass secure-folder, audio-only or delete-all-content policies.

### UP-04 — Music, lyrics, equalizer and secondary subtitles

**Already present:** audio-only classification/routing, generation-safe playback, artwork/checkpoint state, lyric strip with synced/plain rendering, translation and sync offset, current LRCLIB/embedded search, custom subtitle font and MPV/config hardening.

**Port candidates:** provider registry with per-provider timeouts and cancellation; provider-aware disk cache keyed by canonical track/query/options; synced-first ranking; RTL/LTR rendering; secondary subtitle ID/scale/position controls; deterministic disc/track ordering and fast scroll; tablet alignment and lifecycle fixes.

**Compatibility boundary:** current `AudioEqualizerManager` applies Android effects, while upstream removes it for MPV `af` dynamic/manual filtering. Decide explicitly, preferably adding an opt-in MPV mode only when configuration does not own `af`, with Android-effects fallback. Test provider failures/stale generations, cache invalidation, subtitle persistence/font fallback, and all audio source classes.

### UP-05 — Appearance, wallpaper, Liquid Glass and fonts

The direct commit inventory shows a coherent visual line: wallpaper preview lock and editor, presets/home preview, wallpaper-derived colors, full-screen/adaptive scrims, startup-flash prevention, Liquid Glass components/progressive navigation blur, mini-player readability/clipping, floating-bar glass, LogFox-style switch motion, expressive-component cleanup, and downloadable Google/app fonts.

Because the dedicated workflow result failed JSON validation three times, no reliable topic-level current-state narrative was returned. The release inventory and direct Git subjects are nevertheless complete; final implementation decisions must be made by comparing the current `com.quantummpv.app` theme/wallpaper/mini-player files before editing. The safe audit decision is **selective conditional port**, not “missing wholesale.” Preserve current Liquid Glass and test theme startup, wallpaper loading, status bars, clipping, interaction feedback and localized fonts.

### UP-06 — Snapshots and widgets

This is the clearest large missing capability. Upstream introduces frame captures, snapshot folders, capture/detail/library screens, sorting/mosaic layout and an Android home-screen widget. QuantumMPV currently has a configurable `ScreenshotSaver` and `FrameNavigationSheet`, but no capture repository, snapshot tab, widget provider/layout/resources or manifest declaration.

Implement in phases: (1) entities/DAOs/repositories and a migration after current Room v25; (2) atomic lossless PNG capture/MediaStore persistence and recorded position/dimensions; (3) snapshot library/folders/mosaic UI; (4) widget RemoteViews/provider and refresh hooks. Use current Media3/service command ownership, bounded thumbnail caching and current artwork resolution. Test process restart, content/local/network sources, missing files, large libraries, widget sizing/themes and no-widget refresh suppression.

### UP-07 — Administration and onboarding

Manual settings export/import and network summary are present. The missing deltas are Hall of Fame/community data, watch-stat persistence/tracking, automatic configuration backup/restore, SAF child directory handling, onboarding configuration step and configurable debug-log budgets.

Use current `SettingsManager`, `AdvancedPreferencesScreen`, crash/log pipeline and navigation. Backup signatures must normalize export-date differences, retain seven files safely and use only the selected Configuration tree (with optional `Backup` child). Persist parent tree grant plus child name; never silently fall back to an incorrect parent. Watch stats must be session-generation aware and exclude paused/stalled time. Logs must enforce a UTF-8 byte budget after device-info overhead and preserve process/privacy filtering.

### UP-08 — Build, dependencies, CI and release

The build line contains genuinely useful compatibility work: 16-KB native page alignment, Gradle/Kotlin and npm updates, image-size update, compiler heap increase, mpv backend 1.0.10, Material3 update and parallelized distribution builds. These are safer than feature cherry-picks but still require dependency lockfile, ABI, min/target SDK, reproducible release and signing validation.

The preview identity/commit-title change and `release: prepare 2.7.0` / `release: prepare 2.7.1` commits are release-only. Dependabot/merge wrappers are accounted for but add no independent behavior. The mixed `UP-04` commit `6f303654` has both runtime lyrics-provider files and GitHub workflow changes; split review by hunk rather than accepting workflow changes as an app feature.

## Explicit commit ledger

Every commit below is assigned to one and only one unit. Merge/revert/dependabot/release-wrapper commits remain in their assigned topic for accounting; where they have no standalone feature delta, that is called out above.

### UP-01 (10)

`2f27763a2a3c8fbf6dcc37b5159f50789ac0009c`, `32f366d85c300ea24d551d7b49a8af3d40b9fd33`, `38f19c556780674dc388ae6d118cc3d5836ce789`, `934a6bff98e1c0296f76c3d839654a8a78699354`, `9a6b2687930116fabccf33995f1352b78dc77f1c`, `707c930ff4a2b82dc9b23c3dd2bc8311daed0a31`, `c5eb9c8379fc40f91a167d57b81a36df888b979f`, `432c713cf18765996a661ec5d1e98376af5c6d34`, `997f878913391495ce9cf3772f41dbbb09911b7b`, `f39bf4620943ea4f42a2a2a7b0242ced1404ccc0`.

### UP-02 (34)

`e3d2da2b1a0b9f0ac9740554a02b4bee5af957ea`, `1f2a5e16d29fad3b945793acdd1a38bc1517ac89`, `dd4b45dfd5ac3a23b59cdf118d0f810c3f856c4e`, `9654f93a87083ea22d895ae72a49d9c2aa62e638`, `610d20b66fd9a895b616c7a46f1a36708036c30e`, `c7710b1b378baab5e9b0322c01d7a90af9b6cfe6`, `42e66bd4ee9eab8f736ed5dd0186b90b3e08748f`, `72d32e0ad63d6f374718c551fc8b05e78f1c45aa`, `bece3540358a33d1fb1b9326bc16221ea5139df0`, `d27e8b509e47355e101123ce80c62a21657b2456`, `d61b51f6fff47f2cbcb092c97b0704e21724f20c`, `34399d74122a4cc11978157762f92c1c2a35f57a`, `a3e32880665c9841dfd6e12e8f2bc230951447bc`, `4dfad4572b1d5288993a77ba2c3e471fcc362b49`, `3df758c205a6878a4334fb37956e9afc46a08cde`, `9a7145da8888c5611fae70c0a2cfaf6b187bd1d3`, `d482899ee773eecf3dfbf8961f3fbfc26fd00a6c`, `3cba0366fc67469742cfa41c1d1e585438555e45`, `8b940367889246be4e46add6f5e8215b3a25fc62`, `63b4657dc35fb2f0b5c134ef48734810c4464010`, `9596d297df220295cb10a64a57465e627025d2ca`, `13db9a0877b2c47fda91c180626385c190df1bed`, `4719b87121605a141324bdc857169010f368b119`, `15f6c065bde1689294948854ae9dd578397fd66e`, `dd4918fa7aaa364d5d576014f284d0a05e71b6bc`, `8ede0046556cba57607fa4cdbae8fb9ce613fbbc`, `94c5effb8c2544dbbc092e087f0963c18fbd59e3`, `70843b34115ed1f2a1bafcb57345174085b6b3e5`, `0c0c49ba134c74ac160a55aa2adf1ee50a8126f6`, `8383d9d48b18516dc34c87431f4fcfa48fcda80d`, `9d56b8791b799d8424caa57c3d0d8961170d5e66`, `244e5cc4b9e7c3687e7fc33ac8b6299cdf545ad2`, `07a4ceef0d8481fa8188b700b3dc4445251c0689`, `4559738be174123181cc63c7e2683d5ed13eb67d`.

### UP-03 (38)

`c449d827a94ea45a35f8750e6c5c19068dba9112`, `4bbc204ea7c4d2db56f472fcadb0d0aac96273bb`, `396c19dad7cfec05a8cf82fef889c3499b18e714`, `b63534fd89a1952ba18e4e32d45f2fa9e4b6ad65`, `ea9d3443e3eb6de58d5b8c6febfac8620cd6fd8f`, `f811d75127c74ac4e90b01f1038cc90c17e20b1c`, `19ed36b7bd2fbafefe80cb5ceca59efe70820168`, `e133d3495070c1355b07e1d8da83f381c9c11c91`, `830f9b628074e23d62aa222bfe127cf145bbc198`, `eefed36502caca1eb951fe361c6bb98d08a47d3f`, `2ee616636f273ab0dab7fcaaf94a8ac2efb87489`, `e465d35bf0fd6ec767eca4b58d43a4e6c2148078`, `061b5bb605f6c05f06c121b9c6e3ca13215e379f`, `a48b2da1e9885986bb3eaa65f686bcae19f84c17`, `704197589c147cecd6e1e4f7c333132dfac68d9f`, `b5c6d451771cf23f58d2e1967d2cc69cdd5b105b`, `b380e51de851bd51940a55382714649b902b1c0e`, `c71087f1303e835cf30120286493057454d0090d`, `ca6287d05eabe0087f501b693b325c39cdda8079`, `0ad2b35d27082331c4ab52f05f8d4471ad59bdbe`, `ee0e3b38327986841ef8a7a21d4ec5e4b7faca0a`, `36a18192707c5de44dbef50c9dc1f4042f5253f3`, `304a0c55b6428c824f8f1995993fa9de8003aaab`, `ce7881c12a2bb2d409754b4511ae66f517abfdf4`, `59618b95742d9636d7241761b671365cd00a0289`, `8d2d11fd91b97d3ee377939b4d128f4a556775ed`, `3daa9f3c02aa6abab7d7a6534da6240f55ffaf0e`, `e44e681515ecadc670742cfa99aae0e72e65a65a`, `dea38560c0be3c4962fd1ae1d4b15fda125edb82`, `41b63a2699676dcb354005f5433c0fe6032f01ab`, `19a2915147169638ae7cfc0377e88aaefd5a9d39`, `5e59c3cc613d5561fe19c51eb255f92c52045fa3`, `1be3c834b1e49f90e1c7e1fe312ba23da642eb6a`, `6d89061e5898dad13e8bc2c976134d0868058902`, `24e1f2a3c0561594b29c7ddfe0d3c462955d0395`, `f8951ed181357c0a272019ccfc4c45535d732bb0`, `ac20525a8d9addb9cc7c7a3db29a792f3287b08f`, `3cede34be39a3403290ebd23215373f351c202bb`.

### UP-04 (17)

`697e159a957fda22c2999e77825af76c9edb9eac`, `b88231ab5840215fa293dce95fa906c9e3be2a8b`, `a6719ca5c18d9a520752ce85563c4d7f26b68594`, `d5ebd2af1c9c2b2504877e5c6d1629514e7f98ce`, `f1daba8c58ea1a4f9832b8f2eb0ec39f3da0e871`, `3ad047e66e640e186b8c633fb51dbc00b59c2682`, `65826f78a2c11e7cabe61e757b714cea7b4ee2c2`, `84c98b1f27da52bc604a463b9d6d83321a24f539`, `6b4a48edd62ac465207096da5b1ec68e589ba441`, `cdfa5903b66a5b5926fdda9ff8d592d8efd039a1`, `6f303654b438eabd8bdb4c18c61032db75e7dd4c`, `9d896becf789dbb30c3a71415fa4413d8629b9d8`, `7e3e109cf71d0f92f5b089dd4a617dd78390a463`, `72d24458e0a75e1815309349989fdb94ff7a6b93`, `371308399ec626c2b6937154fa534471885016cf`, `c0934b28e06e2632e4605bb09babefb9642ed7f8`, `7fd2e70247b7dc0a00d2131f348117437c1aca8f`.

### UP-05 (26)

`18da8bffdea971c32d659d3eefd46587a6d0358c`, `0bf846a9718c423850042e2a13cb866e69349497`, `6ae293c4a4e7d845308474714165842ba2727dd7`, `4c470d3e14b65468eabba1eb7bbf82f4c95beebe`, `4b9f3943e68d6d3f00811abc405f61aa8b47c9d6`, `af4f298836b55cb4e4c564de3ee79793e1740654`, `c6324b535302da7f4453110b58140c920953473a`, `8465c0e53c3ddc8bed36341bd6d6dd43ea294f4d`, `0be43937c0e67c06e781bc00a1d4a4c0d03881b9`, `d9747102cca975f547160af3feb0c33ff6f51026`, `ed1182685f1c1304aceb7a6c34bb57c93feec604`, `98c2c7b85df09b3676f77e3bc517a91330d373d9`, `37b9b43260197cb61fdd7d8c5637bfc1ee5849d9`, `437c4a292911942c4c378ef09ccebf6b3ac02da1`, `58a489de4e2779112b6a3025965ee3db39844ea2`, `9bc78912bc0c3e8b036b9a48a313278094732125`, `80120dafe8231d3b3a872991ef999101b8afa54b`, `44fd84c442f806624c373feeaa6cdcf8c06825a3`, `b97e4e986c79f0a3caa4a54694b457dc5bcc407d`, `84f942bdbc7d3090d81ac0a59817da97ef4b97f4`, `7cbc44ee6ee15d384832c0e1ad360e5c971df633`, `0efa72f3d1feb4c97f77f6dda39a2f19c16c3f19`, `c21194d8b0c2ece9ab8cdc1a71e41a46108fb344`, `98cce6cca8879b4e4cb91007692055cdcbe935b1`, `63f2a25510a24f4eecaa2f8f583b56ebc6d76c3d`, `19ef98b3382bd5711b8b41d2a508dfca8acf33aa`.

### UP-06 (7)

`553b46631fcb4884843e9c8311e286034956a1e4`, `3500e9b3173109103d7dd561a5816f20d8d1a7ac`, `b17b9b4792ec9034277d2c85def56c2c156cfe4d`, `7ba63982390eeed286b6675122f531dbc0fd1fd3`, `e3955eea34b59fd89f5d7a08fd4ddbbff58d0bf9`, `44f7668dea5be73c047fae630d0585355bbc578c`, `7a9d980b159cc136a9f595b03e685898be5bdc1e`.

### UP-07 (8)

`d8df0decca1b6b049ec1f6970d83228d70d85f5a`, `669510241d9ea0b0ee91614e1de141e07d7cb425`, `6df9613efa54b734836bde32a74c6da1d9054fd8`, `c4aaa37afe200283710c3b31efa90a8ad59c16ae`, `520da8ce8540485f85087336278468093046fc52`, `7998303e07c63f3283d59cf32b50cc78aa0f4255`, `99cdfef744408af2220a8cb189dc30e6b23dc72d`, `25aa283257700bb5095de5a146222142974c7994`.

### UP-08 (14)

`37de00e3440c545d89ae524057b1c974b1930aa5`, `5e47747179fd29262e2cb3eb4ba53bcdbf954fb7`, `06a71daad5b0ea380055cc91ba802ce4f91fe8b7`, `ffe0c062c707de2d8da5f09f19334266d5e073f5`, `d89ece5d6d7a2845ded6fb7a5f1a71ae2ef4c1c6`, `2faa50e5f7f59d630a06384df254b8860d33b7e6`, `a31d0e265946f0fd94d6eab5cc75749508252126`, `22a01df7c5501769ad4484b5689080b615180cbb`, `e7da5a07f8f4edc143621162ebadb6935d687c27`, `17849fb485c0fe11163406791a3e656c2196e1af`, `ffeb535c8b00b131f984760be7ab1f7e442c66f8`, `c3e44a70598cb359c4a1b368a4739d26f03fd886`, `6c14eba0a9a26eb3a15c7f99f71745927019d325`, `d8dea2caac0aedb97fbf9ef1884fb242247237c4`.

## Verification plan before any port

- Run `./gradlew :app:compileDebugKotlin`, lint and the repository's unit/instrumentation tests after each feature slice; run a release-like build for UP-08.
- Add/execute Room migration tests from QuantumMPV's current version 25 for ZIP, snapshots and any admin entities; never import upstream schema JSON directly.
- Exercise playback under rotation, PiP, background/notification stop, Surface loss/re-attach, offline/local/network sources, short/long/near-EOF seeks, subtitles and custom OSD fonts.
- Test Android TV focus/D-pad behavior separately from touch phones.
- Mock remote services for Jellyfin/Audiobookshelf, lyric providers, GitHub and image fetches; cover timeout, malformed/oversized responses, cancellation, cache isolation and offline fallback.
- Validate SAF grants, configuration/backup root ownership, secure-folder deletion and content URIs.
- Test 16-KB native alignment, ABI packaging, dependency licenses/vulnerabilities, signing inputs, artifact naming and CI flavor matrix.

## Audit gaps and caveats

1. **No commit coverage gap:** all 154 hashes are explicitly listed above and reconcile to the discovery total; merge/revert/dependabot/release commits were not silently dropped.
2. **UP-05 and UP-08 workflow failures:** their original subagents failed three times because their result was not valid JSON. UP-05 was recovered from direct Git subject/path inspection and the supplied inventory, but a full file-by-file current-state comparison and test run remains a follow-up. UP-08 was recovered from direct Git subjects plus the inventory; signing/flavor compatibility still requires a release dry run.
3. **No build or runtime tests were run as part of this read-only synthesis.** Recommendations are port decisions and verification gates, not claims that the proposed behavior is already implemented.
4. **No source, ref, merge or push was performed.** The report intentionally does not recommend wholesale upstream merge/cherry-pick because package, database, playback, navigation and configuration architectures diverge.


## Implementation follow-up: selected feature ports

**Source repository:** [Riteshp2001/mpvRx](https://github.com/Riteshp2001/mpvRx). The following commit-level changes were inspected directly against QuantumMPV:

- [`13db9a08` — Android TV D-pad seekbar](https://github.com/Riteshp2001/mpvRx/commit/13db9a0877b2c47fda91c180626385c190df1bed): ported as TV-gated LEFT/RIGHT scrubbing with repeat acceleration, commit-on-key-up, focus highlight, and non-focusable time labels.
- [`7e3e109c` — secondary subtitles and RTL bidi compatibility](https://github.com/Riteshp2001/mpvRx/commit/7e3e109cf71d0f92f5b089dd4a617dd78390a463): ported independent secondary scale/position controls and corrected the bidi option label to RTL compatibility. QuantumMPV-specific behavior is retained: secondary position defaults to automatic anti-overlap (`secondary_sub_pos = -1`) and becomes manual only when selected by the user.
- [`9596d297` — focus into player sheet on open](https://github.com/Riteshp2001/mpvRx/commit/9596d297df220295cb10a64a57465e627025d2ca): the inspected QuantumMPV player sheets already include TV focus handling in their track and equalizer flows; review any remaining sheet gaps separately rather than applying the upstream file wholesale.
- [`94c5effb` — retain app UI scale across rotation](https://github.com/Riteshp2001/mpvRx/commit/94c5effb8c2544dbbc092e087f0963c18fbd59e3): not ported because the current QuantumMPV source has no corresponding persisted app-UI-scale preference or runtime consumer.

Additional selected ports in the current integration branch: unconditional native 16-KB ELF alignment (with release-only optimizations preserved), automatically showing the Jellyfin tab after a newly added server authenticates, safe network-file long-press-to-playlist using credential-free `mpvrx-network` URIs, and a crisp mini-player progress boundary. The selected-port guard runs in Android CI as `tools/test_upstream_release_ports.py`.

The local Python/native/custom-feature regression checks pass. Local Kotlin/Android test execution is not possible in this Sandbox because the Android SDK/NDK are not installed; the Android CI run is the required compilation and device-test gate before merge.
