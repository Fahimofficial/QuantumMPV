/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.insights

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quantummpv.app.database.entities.RecentlyPlayedEntity
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import com.quantummpv.app.domain.recentlyplayed.repository.RecentlyPlayedRepository
import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.ui.player.PlaybackIdentity
import com.quantummpv.app.ui.player.resolveLocalPath
import com.quantummpv.app.utils.storage.FileTypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileNotFoundException

/** What the Library Insights screen should currently show. */
sealed interface LibraryInsightsUiState {
  /** The first calculation has not finished yet. */
  data object Loading : LibraryInsightsUiState

  /** The user turned off Recently Played, so no history is read or shown. */
  data object HistoryDisabled : LibraryInsightsUiState

  /** History is on but contains no playable, still-existing video. */
  data object Empty : LibraryInsightsUiState

  /** At least one video was played; [insights] holds the figures. */
  data class Content(
    val insights: LibraryInsights,
  ) : LibraryInsightsUiState
}

/**
 * Exposes [LibraryInsightsUiState] derived from the existing Recently Played history and saved
 * playback positions. No data is copied or stored: the state follows the live history table and the
 * live Recently Played preference, so clearing or disabling history is reflected immediately.
 */
class LibraryInsightsViewModel(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject()
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val browserPreferences: BrowserPreferences by inject()

  private val refreshTick = MutableStateFlow(0)

  /**
   * The replay cache is dropped as soon as collection stops, so figures from an earlier visit are
   * never shown again after history was cleared or disabled in the meantime.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<LibraryInsightsUiState> =
    combine(
      advancedPreferences.enableRecentlyPlayed.changes(),
      recentlyPlayedRepository.observeRecentlyPlayed(limit = Int.MAX_VALUE),
      refreshTick,
    ) { enabled, history, _ -> enabled to history }
      .mapLatest { (enabled, history) ->
        if (enabled) calculate(history) else LibraryInsightsUiState.HistoryDisabled
      }.flowOn(Dispatchers.IO)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
        initialValue = LibraryInsightsUiState.Loading,
      )

  /** Recomputes now; used when returning to the screen because saved positions are not observed. */
  fun refresh() {
    refreshTick.update { it + 1 }
  }

  /** Runs on an IO dispatcher: reads saved positions and checks that each file still exists. */
  private suspend fun calculate(history: List<RecentlyPlayedEntity>): LibraryInsightsUiState {
    val savedStates = playbackStateRepository.getAllPlaybackStates().associateBy { it.mediaTitle }
    val insights =
      LibraryInsightsCalculator.calculate(
        history = history.filterNot(::isAudio),
        watchedThresholdPercent = browserPreferences.watchedThreshold.get(),
        nowMillis = System.currentTimeMillis(),
        fileExists = ::stillExists,
        playbackStateFor = { path -> playbackIdentities(path).firstNotNullOfOrNull { savedStates[it] } },
      )
    return if (insights.videosPlayed == 0) {
      LibraryInsightsUiState.Empty
    } else {
      LibraryInsightsUiState.Content(insights)
    }
  }

  /** Audio is left out because this screen is about video viewing. */
  private fun isAudio(entity: RecentlyPlayedEntity): Boolean =
    FileTypeUtils.isAudioFile(File(entity.fileName.ifBlank { entity.filePath }))

  /**
   * Returns false only when the file is known to be gone. Local paths are checked on disk and
   * document-provider URIs through the content resolver. Remote streams cannot be verified without a
   * network call and a document whose existence cannot be determined is kept rather than dropped.
   */
  private fun stillExists(path: String): Boolean =
    when {
      path.startsWith("/") -> File(path).exists()
      path.startsWith("file://") -> File(path.removePrefix("file://")).exists()
      path.startsWith("content://") -> contentUriExists(path)
      else -> true
    }

  /** A missing document throws [FileNotFoundException]; other failures (e.g. revoked access) are inconclusive. */
  private fun contentUriExists(uri: String): Boolean =
    try {
      getApplication<Application>().contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.close()
      true
    } catch (e: FileNotFoundException) {
      false
    } catch (e: RuntimeException) {
      true
    }

  /**
   * Candidate playback-state keys for a history path, using the same identities the player and file
   * browser write. A `content://` document is resolved to its local path exactly as the player does
   * ([resolveLocalPath]), because the player saves its state under that path's identity. Network
   * items are keyed by connection and cannot be derived from a history path, so they simply have no
   * known progress.
   */
  private fun playbackIdentities(path: String): List<String> {
    val localPath =
      when {
        path.startsWith("/") -> path
        path.startsWith("file://") -> path.removePrefix("file://")
        path.startsWith("content://") -> resolveDocumentPath(path)
        else -> null
      } ?: return listOf(PlaybackIdentity.forUri(path))
    return listOf(
      PlaybackIdentity.forLocalPath(localPath),
      PlaybackIdentity.forUri(path),
      PlaybackIdentity.forUri(localPath),
      PlaybackIdentity.forUri("file://$localPath"),
    )
  }

  /** The local path behind a document URI as the player resolves it, or null when it cannot be resolved. */
  private fun resolveDocumentPath(uri: String): String? =
    runCatching { Uri.parse(uri).resolveLocalPath(getApplication<Application>()) }.getOrNull()

  companion object {
    private const val STOP_TIMEOUT_MILLIS = 5_000L

    /** Creates the view model with the application context it needs. */
    fun factory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryInsightsViewModel(application) as T
      }
  }
}
