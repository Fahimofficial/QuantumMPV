/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.MpvConfigControlledFeatures
import com.quantummpv.app.preferences.MpvConfigOverride
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.player.Decoder
import com.quantummpv.app.ui.player.Panels
import com.quantummpv.app.ui.player.Sheets
import com.quantummpv.app.ui.player.TrackNode
import com.quantummpv.app.ui.player.controls.components.MpvConfigOwnedSheet
import com.quantummpv.app.ui.player.controls.components.sheets.AmbientSheet
import com.quantummpv.app.ui.player.controls.components.sheets.AspectRatioSheet
import com.quantummpv.app.ui.player.controls.components.sheets.AudioTracksSheet
import com.quantummpv.app.ui.player.controls.components.sheets.ChaptersSheet
import com.quantummpv.app.ui.player.controls.components.sheets.DecodersSheet
import com.quantummpv.app.ui.player.controls.components.sheets.FrameNavigationSheet
import com.quantummpv.app.ui.player.controls.components.sheets.MoreSheet
import com.quantummpv.app.ui.player.controls.components.sheets.OnlineSubtitleSearchSheet
import com.quantummpv.app.ui.player.controls.components.sheets.PlaybackSpeedSheet
import com.quantummpv.app.ui.player.controls.components.sheets.PlaylistSheet
import com.quantummpv.app.ui.player.controls.components.sheets.ScopesSheet
import com.quantummpv.app.ui.player.controls.components.sheets.SubtitlesSheet
import com.quantummpv.app.ui.player.controls.components.sheets.VideoZoomSheet
import com.quantummpv.app.ui.player.controls.components.sheets.VideoQualitySheet
import com.quantummpv.app.ui.player.controls.components.sheets.VisualizerStyleSheet
import com.quantummpv.app.ui.player.setTrackSelectionId
import com.quantummpv.app.utils.device.DeviceFormFactor
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState as composeCollectAsState

@Composable
fun PlayerSheets(
  sheetShown: Sheets,
  viewModel: com.quantummpv.app.ui.player.PlayerViewModel,
  // subtitles sheet
  subtitles: ImmutableList<TrackNode>,
  onAddSubtitle: (Uri) -> Unit,
  onToggleSubtitle: (Int) -> Unit,
  isSubtitleSelected: (Int) -> Boolean,
  subtitleSelectionIndicator: (Int) -> String?,
  onRemoveSubtitle: (Int) -> Unit,
  // audio sheet
  audioTracks: ImmutableList<TrackNode>,
  onAddAudio: (Uri) -> Unit,
  onSelectAudio: (TrackNode) -> Unit,
  // chapters sheet
  chapter: Segment?,
  chapters: ImmutableList<Segment>,
  onSeekToChapter: (Int) -> Unit,
  // Decoders sheet
  decoder: Decoder,
  onUpdateDecoder: (Decoder) -> Unit,
  // Speed sheet
  speed: Float,
  speedPresets: List<Float>,
  onSpeedChange: (Float) -> Unit,
  onAddSpeedPreset: (Float) -> Unit,
  onRemoveSpeedPreset: (Float) -> Unit,
  onResetSpeedPresets: () -> Unit,
  onMakeDefaultSpeed: (Float) -> Unit,
  onResetDefaultSpeed: () -> Unit,
  // More sheet
  sleepTimerTimeRemaining: Int,
  onStartSleepTimer: (Int) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  onShowSheet: (Sheets) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val isTelevision = DeviceFormFactor.isTelevision(LocalContext.current)
  val advancedPreferences = koinInject<AdvancedPreferences>()
  val storedConfigOverrides by advancedPreferences.mpvConfOverrides.collectAsState()
  val configOwnedOptions =
    remember(storedConfigOverrides) { MpvConfigOverride.resolveOptionNames(storedConfigOverrides) }
  val fullyOwnedOptions =
    when (sheetShown) {
      Sheets.Decoders -> MpvConfigControlledFeatures.HARDWARE_DECODER
      Sheets.AmbientConfig -> MpvConfigControlledFeatures.AMBIENT
      Sheets.Equalizer -> setOf("af")
      Sheets.AspectRatios -> MpvConfigControlledFeatures.VIDEO_ASPECT
      else -> null
    }
  if (fullyOwnedOptions?.any(configOwnedOptions::contains) == true) {
    MpvConfigOwnedSheet(onDismissRequest)
    return
  }

  when (sheetShown) {
    Sheets.None -> {}
    Sheets.SubtitleTracks -> {
      val subtitlesPicker =
        rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          onAddSubtitle(it)
        }

      val subtitlesPreferences = koinInject<com.quantummpv.app.preferences.SubtitlesPreferences>()
      val savedPickerPath = subtitlesPreferences.pickerPath.get()

      val currentMediaTitle = viewModel.currentMediaTitle
      val matchToName =
        if (currentMediaTitle.isNotBlank()) {
          // Remove extension if present to improve matching
          currentMediaTitle.substringBeforeLast(".")
        } else {
          null
        }

      var showFilePicker by remember { mutableStateOf(false) }

      if (showFilePicker) {
        com.quantummpv.app.ui.browser.dialogs.FilePickerDialog(
          isOpen = true,
          currentPath = savedPickerPath,
          onDismiss = { showFilePicker = false },
          onPathChanged = { path ->
            if (path != null) {
              subtitlesPreferences.pickerPath.set(path)
            }
          },
          onFileSelected = { path ->
            showFilePicker = false
            onAddSubtitle(Uri.parse("file://$path"))
          },
          onSystemPickerRequest = {
            showFilePicker = false
            subtitlesPicker.launch(
              arrayOf(
                "text/plain",
                "text/srt",
                "text/vtt",
                "application/x-subrip",
                "application/x-subtitle",
                "text/x-ssa",
                "*/*",
              ),
            )
          },
          matchToName = matchToName,
        )
      }

      val isTranslating by viewModel.isTranslatingSub.composeCollectAsState()
      val translationProgress by viewModel.translationProgress.composeCollectAsState()
      val translationStatus by viewModel.translationStatus.composeCollectAsState()
      val translatingTrackId by viewModel.translatingTrackId.composeCollectAsState()
      val translatingTrackName by viewModel.translatingTrackName.composeCollectAsState()
      val isGeneratingSubtitles by viewModel.isGeneratingSubtitles.composeCollectAsState()
      val subtitleGenerationProgress by viewModel.subtitleGenerationProgress.composeCollectAsState()
      val subtitleGenerationStatus by viewModel.subtitleGenerationStatus.composeCollectAsState()
      val isRealtimeSubsActive by viewModel.isRealtimeSubsActive.composeCollectAsState()
      val realtimeSubsProgress by viewModel.realtimeSubsProgress.composeCollectAsState()
      val realtimeSubsStatus by viewModel.realtimeSubsStatus.composeCollectAsState()
      val aiPreferences = koinInject<com.quantummpv.app.preferences.AiPreferences>()
      val aiEnabled by aiPreferences.enabled.collectAsState()
      val realtimeSubsEnabled by aiPreferences.realtimeSubsEnabled.collectAsState()
      val translationEnabled by aiPreferences.subtitleTranslationEnabled.collectAsState()
      val autoTranslateLanguages by aiPreferences.autoTranslateLanguages.collectAsState()

      val subtitlesOff = subtitles.none { isSubtitleSelected(it.id) }

      SubtitlesSheet(
        tracks = subtitles.toImmutableList(),
        onToggleSubtitle = { id ->
          if (isTelevision) {
            viewModel.selectPrimarySubtitle(id)
            onDismissRequest()
          } else {
            onToggleSubtitle(id)
          }
        },
        isSubtitleSelected = isSubtitleSelected,
        subtitleSelectionIndicator = subtitleSelectionIndicator,
        onAddSubtitle = { showFilePicker = true },
        onRemoveSubtitle = onRemoveSubtitle,
        onOpenSubtitleSettings = { onOpenPanel(Panels.SubtitleSettings) },
        onOpenSubtitleDelay = { onOpenPanel(Panels.SubtitleDelay) },
        delayControlEnabled = setOf("sub-delay", "sub-speed").any { it !in configOwnedOptions },
        onOpenOnlineSearch = { onShowSheet(Sheets.OnlineSubtitleSearch) },
        onDismissRequest = onDismissRequest,
        onTranslateSubtitle = { track, lang -> viewModel.translateSubtitle(track, lang) },
        onGenerateSubtitle = { viewModel.generateSubtitles("", "") },
        onStartRealtimeSubtitle = viewModel::startRealtimeSubtitles,
        onStopRealtimeSubtitle = { viewModel.stopRealtimeSubtitles() },
        onCancelTranslation = { viewModel.cancelTranslation() },
        isTranslating = isTranslating,
        translationProgress = translationProgress,
        translationStatus = translationStatus,
        realtimeSubsStatus = realtimeSubsStatus,
        translationEnabled = aiEnabled && translationEnabled,
        isGeneratingSubtitles = isGeneratingSubtitles,
        isRealtimeSubsActive = isRealtimeSubsActive,
        realtimeSubsProgress = realtimeSubsProgress,
        subtitleGenerationProgress = subtitleGenerationProgress,
        subtitleGenerationStatus = subtitleGenerationStatus,
        translatingTrackId = translatingTrackId,
        translatingTrackName = translatingTrackName,
        autoTranslateLanguages = autoTranslateLanguages,
        aiEnabled = aiEnabled,
        realtimeSubsEnabled = realtimeSubsEnabled,
        subtitlesOff = subtitlesOff,
        onDisableSubtitles = {
          setTrackSelectionId("sid", null)
          setTrackSelectionId("secondary-sid", null)
          subtitlesPreferences.autoEnableSubtitles.set(false)
          if (isTelevision) onDismissRequest()
        },
      )
    }

    Sheets.OnlineSubtitleSearch -> {
      val isSearching by viewModel.isSearchingSub.composeCollectAsState()
      val isDownloading by viewModel.isDownloadingSub.composeCollectAsState()
      val results by viewModel.onlineSubtitleSearchResults.composeCollectAsState()
      val isOnlineSectionExpanded by viewModel.isOnlineSectionExpanded.composeCollectAsState()
      val subtitlesPreferences = koinInject<com.quantummpv.app.preferences.SubtitlesPreferences>()
      val subtitleSearchMode by subtitlesPreferences.onlineSubtitleSearchMode.collectAsState()

      // Media Search / Autocomplete
      val mediaResults by viewModel.mediaSearchResults.composeCollectAsState()
      val isSearchingMedia by viewModel.isSearchingMedia.composeCollectAsState()

      // TV Show / Seasons / Episodes
      val selectedTvShow by viewModel.selectedTvShow.composeCollectAsState()
      val isFetchingTvDetails by viewModel.isFetchingTvDetails.composeCollectAsState()
      val selectedSeason by viewModel.selectedSeason.composeCollectAsState()
      val seasonEpisodes by viewModel.seasonEpisodes.composeCollectAsState()
      val isFetchingEpisodes by viewModel.isFetchingEpisodes.composeCollectAsState()
      val selectedEpisode by viewModel.selectedEpisode.composeCollectAsState()

      OnlineSubtitleSearchSheet(
        onDismissRequest = onDismissRequest,
        onDownloadOnline = { viewModel.downloadSubtitle(it) },
        isSearching = isSearching,
        isDownloading = isDownloading,
        searchResults = results.toImmutableList(),
        isOnlineSectionExpanded = isOnlineSectionExpanded,
        onToggleOnlineSection = { viewModel.toggleOnlineSection() },
        mediaTitle = viewModel.currentMediaTitle,
        showWyzieSelection = subtitleSearchMode != com.quantummpv.app.repository.subtitle.OnlineSubtitleSearchMode.SUBHUB,
        // Autocomplete & Series Selection
        mediaSearchResults = mediaResults.toImmutableList(),
        isSearchingMedia = isSearchingMedia,
        onSearchMedia = viewModel::searchOnlineSubtitles,
        onSelectMedia = { viewModel.selectMedia(it) },
        selectedTvShow = selectedTvShow,
        isFetchingTvDetails = isFetchingTvDetails,
        selectedSeason = selectedSeason,
        onSelectSeason = { viewModel.selectSeason(it) },
        seasonEpisodes = seasonEpisodes.toImmutableList(),
        isFetchingEpisodes = isFetchingEpisodes,
        selectedEpisode = selectedEpisode,
        onSelectEpisode = { viewModel.selectEpisode(it) },
        onClearMediaSelection = { viewModel.clearMediaSelection() },
      )
    }

    Sheets.AudioTracks -> {
      val audioPicker =
        rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          onAddAudio(it)
        }

      val audioPreferences = koinInject<com.quantummpv.app.preferences.AudioPreferences>()
      val savedPickerPath = audioPreferences.pickerPath.get()

      val currentMediaTitle = viewModel.currentMediaTitle
      val matchToName =
        if (currentMediaTitle.isNotBlank()) {
          currentMediaTitle.substringBeforeLast(".")
        } else {
          null
        }

      var showAudioFilePicker by remember { mutableStateOf(false) }

      val audioAllowedExtensions =
        remember {
          listOf(
            // Common compressed audio
            "mp3", "m4a", "aac", "ogg", "oga", "opus", "wma",
            // Lossless audio
            "flac", "alac", "wav", "wave", "ape", "tta", "tak", "aif", "aiff", "aifc",
            // Multichannel & surround / cinema formats
            "ac3", "eac3", "dts", "dtshd", "dts-hd", "thd", "truehd", "mlp",
            // Audio containers & video files with audio
            "mka", "mkv", "mp4", "webm", "caf", "weba",
            // Voice / telephony
            "amr", "awb", "spx", "3ga",
            // High-resolution DSD
            "dsf", "dff",
            // Legacy / Tracker / Misc
            "au", "snd", "ra", "mp1", "mp2", "mpa", "mpc", "mid", "midi",
          )
        }

      if (showAudioFilePicker) {
        com.quantummpv.app.ui.browser.dialogs.FilePickerDialog(
          isOpen = true,
          currentPath = savedPickerPath,
          onDismiss = { showAudioFilePicker = false },
          onPathChanged = { path ->
            if (path != null) {
              audioPreferences.pickerPath.set(path)
            }
          },
          onFileSelected = { path ->
            showAudioFilePicker = false
            onAddAudio(Uri.parse("file://$path"))
          },
          onSystemPickerRequest = {
            showAudioFilePicker = false
            audioPicker.launch(
              arrayOf(
                "audio/*",
                "application/ogg",
                "application/x-flac",
                "video/x-matroska",
                "video/*",
                "*/*",
              ),
            )
          },
          matchToName = matchToName,
          allowedExtensions = audioAllowedExtensions,
        )
      }

      AudioTracksSheet(
        tracks = audioTracks,
        onSelect = { track ->
          onSelectAudio(track)
          if (isTelevision) onDismissRequest()
        },
        onAddAudioTrack = { showAudioFilePicker = true },
        onOpenDelayPanel = { onOpenPanel(Panels.AudioDelay) },
        onOpenEqualizerSheet = { onShowSheet(Sheets.Equalizer) },
        delayControlEnabled = "audio-delay" !in configOwnedOptions,
        equalizerControlEnabled = "af" !in configOwnedOptions,
        audioChannelsEnabled = "audio-channels" !in configOwnedOptions,
        reverseStereoEnabled = "af" !in configOwnedOptions,
        audioEffectsEnabled = "af" !in configOwnedOptions,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.VideoQuality -> {
      val videoQualityTracks by viewModel.videoQualityTracks.collectAsState()
      VideoQualitySheet(
        tracks = videoQualityTracks,
        onSelect = viewModel::selectVideoQuality,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.Chapters -> {
      ChaptersSheet(
        chapters,
        currentChapter = chapter,
        onClick = { onSeekToChapter(chapters.indexOf(it)) },
        onDismissRequest,
      )
    }

    Sheets.Decoders -> {
      DecodersSheet(
        selectedDecoder = decoder,
        onSelect = onUpdateDecoder,
        onDismissRequest,
      )
    }

    Sheets.More -> {
      val anime4KUiState by viewModel.anime4KUiState.composeCollectAsState()
      MoreSheet(
        remainingTime = sleepTimerTimeRemaining,
        onStartTimer = onStartSleepTimer,
        onDismissRequest = onDismissRequest,
        onEnterFiltersPanel = { onOpenPanel(Panels.VideoFilters) },
        onEnterLuaScriptsPanel = { onOpenPanel(Panels.LuaScripts) },
        onEnterEqualizerSheet = { onShowSheet(Sheets.Equalizer) },
        anime4KUiState = anime4KUiState,
        onAnime4KModeSelected = viewModel::selectAnime4KMode,
        filtersEnabled = MpvConfigOverride.VIDEO_FILTERS.optionNames.any { it !in configOwnedOptions },
        equalizerEnabled = "af" !in configOwnedOptions,
        anime4KEnabled = MpvConfigControlledFeatures.ANIME4K.none(configOwnedOptions::contains),
      )
    }

    Sheets.PlaybackSpeed -> {
      PlaybackSpeedSheet(
        speed,
        onSpeedChange = onSpeedChange,
        speedPresets = speedPresets,
        onAddSpeedPreset = onAddSpeedPreset,
        onRemoveSpeedPreset = onRemoveSpeedPreset,
        onResetPresets = onResetSpeedPresets,
        onMakeDefault = onMakeDefaultSpeed,
        onResetDefault = onResetDefaultSpeed,
        onDismissRequest = onDismissRequest,
        speedControlEnabled = "speed" !in configOwnedOptions,
        pitchCorrectionEnabled = "audio-pitch-correction" !in configOwnedOptions,
      )
    }

    Sheets.VideoZoom -> {
      val videoZoom by viewModel.videoZoom.composeCollectAsState()
      VideoZoomSheet(
        videoZoom = videoZoom,
        onSetVideoZoom = viewModel::setVideoZoom,
        onResetVideoPan = viewModel::resetVideoPan,
        onDismissRequest = onDismissRequest,
        zoomControlEnabled = "video-zoom" !in configOwnedOptions,
        panControlEnabled = setOf("video-pan-x", "video-pan-y").any { it !in configOwnedOptions },
      )
    }

    Sheets.AspectRatios -> {
      val playerPreferences = koinInject<com.quantummpv.app.preferences.PlayerPreferences>()
      val customRatiosSet by playerPreferences.customAspectRatios.collectAsState()
      val autoCropEnabled by playerPreferences.autoCropBlackBars.collectAsState()
      val currentRatio by viewModel.currentAspectRatio.composeCollectAsState()
      val autoCropState by viewModel.autoCropState.composeCollectAsState()
      val customRatios =
        customRatiosSet.mapNotNull { str ->
          val parts = str.split("|")
          if (parts.size == 2) {
            com.quantummpv.app.ui.player.controls.components.sheets.AspectRatio(
              label = parts[0],
              ratio = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
              isCustom = true,
            )
          } else {
            null
          }
        }

      AspectRatioSheet(
        currentRatio = currentRatio,
        customRatios = customRatios,
        autoCropEnabled = autoCropEnabled,
        autoCropState = autoCropState,
        autoCropControlEnabled = MpvConfigControlledFeatures.AUTO_CROP.none(configOwnedOptions::contains),
        onAutoCropChanged = viewModel::setAutoCropBlackBars,
        onSelectRatio = { ratio ->
          if (ratio < 0) {
            // Default selected - apply Fit mode
            viewModel.changeVideoAspect(com.quantummpv.app.ui.player.VideoAspect.Fit)
          } else {
            // Custom ratio selected
            viewModel.setCustomAspectRatio(ratio)
          }
        },
        onAddCustomRatio = { label, ratio ->
          playerPreferences.customAspectRatios.set(customRatiosSet + "$label|$ratio")
          viewModel.setCustomAspectRatio(ratio)
        },
        onDeleteCustomRatio = { ratio ->
          val toRemove = "${ratio.label}|${ratio.ratio}"
          playerPreferences.customAspectRatios.set(customRatiosSet - toRemove)
          // If the deleted ratio is currently active, reset to default (Fit)
          if (kotlin.math.abs(currentRatio - ratio.ratio) < 0.01) {
            viewModel.changeVideoAspect(com.quantummpv.app.ui.player.VideoAspect.Fit)
          }
        },
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.FrameNavigation -> {
      LaunchedEffect(Unit) {
        viewModel.hideControls()
        viewModel.panelShown.value = Panels.None
      }
      val currentFrame by viewModel.currentFrame.composeCollectAsState()
      val totalFrames by viewModel.totalFrames.composeCollectAsState()
      FrameNavigationSheet(
        currentFrame = currentFrame,
        totalFrames = totalFrames,
        onUpdateFrameInfo = viewModel::updateFrameInfo,
        onPause = viewModel::pause,
        onUnpause = viewModel::unpause,
        onPauseUnpause = viewModel::pauseUnpause,
        onSeekToFrame = { targetFrame, finished ->
          viewModel.seekToFrame(targetFrame, totalFrames, finished)
        },
        onCancelFrameSeek = viewModel::cancelFrameSeek,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.Playlist -> {
      // Observe playlist updates
      val playlist by viewModel.playlistItems.collectAsState()
      val isAudioOnly by viewModel.isAudioOnly.collectAsState()
      val playerPreferences = koinInject<com.quantummpv.app.preferences.PlayerPreferences>()
      val isPlaylistSwipeActive by viewModel.isPlaylistSwipeActive.collectAsState()
      val playlistSwipeOffset by viewModel.playlistSwipeOffset.collectAsState()

      val playlistImmutable = remember(playlist) { playlist.toImmutableList() }

      if (playlistImmutable.isNotEmpty()) {
        val totalCount = playlistImmutable.size
        val isM3U = viewModel.isPlaylistM3U()
        PlaylistSheet(
          playlist = playlistImmutable,
          onDismissRequest = onDismissRequest,
          onItemClick = { item ->
            viewModel.playPlaylistItem(item.index)
          },
          onReorder = { from, to ->
            viewModel.reorderPlaylistItem(from, to)
          },
          totalCount = totalCount,
          isM3UPlaylist = isM3U,
          playerPreferences = playerPreferences,
          isSwipeActive = isPlaylistSwipeActive,
          swipeOffset = playlistSwipeOffset,
          isAudioOnly = isAudioOnly,
        )
      }
    }

    Sheets.AmbientConfig -> {
      AmbientSheet(
        viewModel = viewModel,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.Equalizer -> {
      val equalizerState by viewModel.equalizerState.collectAsState()
      com.quantummpv.app.ui.player.controls.components.sheets.EqualizerSheet(
        state = equalizerState,
        onEnabledChanged = viewModel::setEqualizerEnabled,
        onPresetSelected = viewModel::applyEqualizerPreset,
        onBandChanged = viewModel::setEqualizerBandGain,
        onVolumeBoostChanged = viewModel::setEqualizerVolumeBoost,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.AudioProperties -> {
      val properties = remember { viewModel.getAudioPropertiesData() }
      com.quantummpv.app.ui.player.controls.components.sheets.AudioPropertiesSheet(
        properties = properties,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.VisualizerStyle -> {
      val audioPreferences = koinInject<com.quantummpv.app.preferences.AudioPreferences>()
      val audioVisualizerStyle by audioPreferences.audioVisualizerStyle.collectAsState()
      VisualizerStyleSheet(
        selectedStyle = audioVisualizerStyle,
        onSelectStyle = { audioPreferences.audioVisualizerStyle.set(it) },
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.Lyrics -> {
      com.quantummpv.app.ui.player.controls.components.sheets.LyricsSheet(
        viewModel = viewModel,
        onDismiss = onDismissRequest,
      )
    }

    Sheets.Scopes -> {
      ScopesSheet(
        viewModel = viewModel,
        audioTracks = audioTracks,
        onSelectAudio = onSelectAudio,
        onDismissRequest = onDismissRequest,
      )
    }
  }
}
