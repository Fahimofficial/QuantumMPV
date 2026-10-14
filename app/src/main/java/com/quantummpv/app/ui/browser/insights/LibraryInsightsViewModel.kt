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
import com.quantummpv.app.domain.recentlyplayed.repository.RecentlyPlayedRepository
import com.quantummpv.app.preferences.AdvancedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.io.File

/**
 * ViewModel for Library Insights screen.
 * Calculates viewing statistics from existing playback history.
 */
class LibraryInsightsViewModel(application: Application) : AndroidViewModel(application) {
  private val repository: RecentlyPlayedRepository by inject(RecentlyPlayedRepository::class.java)
  private val preferences: AdvancedPreferences by inject(AdvancedPreferences::class.java)

  private val _insightsState = MutableStateFlow(LibraryInsightsState())
  val insightsState: StateFlow<LibraryInsightsState> = _insightsState.asStateFlow()

  init {
    loadInsights()
  }

  private fun loadInsights() {
    viewModelScope.launch {
      _insightsState.value = _insightsState.value.copy(isLoading = true)

      val insights = withContext(Dispatchers.IO) {
        calculateInsights()
      }

      _insightsState.value = insights
    }
  }

  private suspend fun calculateInsights(): LibraryInsightsState {
    if (!preferences.enableRecentlyPlayed.get()) {
      return LibraryInsightsState(isLoading = false, isEmpty = true)
    }

    val allHistory = repository.getRecentlyPlayed(limit = 1000)
    if (allHistory.isEmpty()) {
      return LibraryInsightsState(isLoading = false, isEmpty = true)
    }

    // Filter out deleted files and group by file path
    val validHistory = allHistory.filter { entity ->
      val path = entity.filePath
      isValidPath(path)
    }

    if (validHistory.isEmpty()) {
      return LibraryInsightsState(isLoading = false, isEmpty = true)
    }

    // Group by file path to get unique videos and their play counts
    val videoGroups = validHistory.groupBy { it.filePath }

    // Calculate total watch time (sum of durations for unique videos)
    val totalWatchTimeMs = videoGroups.values.sumOf { sessions ->
      // Use the most recent session's duration for each video
      sessions.maxByOrNull { it.lastPlayedTimestamp }?.duration ?: 0L
    }

    // Count unique videos
    val uniqueVideosWatched = videoGroups.size

    // Count completed videos (assume >= 90% progress means completed)
    // Since we don't track progress in the current schema, we'll count videos
    // that have been played multiple times as "completed"
    val completedVideos = videoGroups.count { (_, sessions) -> sessions.size >= 2 }

    // Recently watched (last 10 unique videos, most recent first)
    val recentlyWatched = videoGroups.values
      .map { sessions ->
        val latest = sessions.maxByOrNull { it.lastPlayedTimestamp }!!
        InsightVideo(
          filePath = latest.filePath,
          displayName = latest.videoTitle?.takeIf { it.isNotBlank() }
            ?: latest.fileName.takeIf { it.isNotBlank() }
            ?: File(latest.filePath).name,
          lastPlayedTimestamp = latest.lastPlayedTimestamp,
          playCount = sessions.size,
          duration = latest.duration,
        )
      }
      .sortedByDescending { it.lastPlayedTimestamp }
      .take(10)

    // Most watched (by play count, top 10)
    val mostWatched = videoGroups.values
      .map { sessions ->
        val representative = sessions.first()
        InsightVideo(
          filePath = representative.filePath,
          displayName = representative.videoTitle?.takeIf { it.isNotBlank() }
            ?: representative.fileName.takeIf { it.isNotBlank() }
            ?: File(representative.filePath).name,
          lastPlayedTimestamp = sessions.maxOf { it.lastPlayedTimestamp },
          playCount = sessions.size,
          duration = representative.duration,
        )
      }
      .sortedByDescending { it.playCount }
      .filter { it.playCount > 1 } // Only show videos watched more than once
      .take(10)

    return LibraryInsightsState(
      isLoading = false,
      isEmpty = false,
      totalWatchTimeMs = totalWatchTimeMs,
      uniqueVideosWatched = uniqueVideosWatched,
      completedVideos = completedVideos,
      totalHistoryItems = validHistory.size,
      recentlyWatched = recentlyWatched,
      mostWatched = mostWatched,
    )
  }

  private fun isValidPath(path: String): Boolean {
    return try {
      // For file paths, don't use Uri.parse as it treats # as fragment separator
      if (path.startsWith("/") || path.startsWith("file://")) {
        val filePath = if (path.startsWith("file://")) {
          path.removePrefix("file://")
        } else {
          path
        }
        File(filePath).exists()
      } else {
        // Network URIs are considered valid if they have a non-file scheme
        val uri = Uri.parse(path)
        val scheme = uri.scheme
        scheme != null && !scheme.equals("file", ignoreCase = true)
      }
    } catch (e: Exception) {
      false
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory {
      return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return LibraryInsightsViewModel(application) as T
        }
      }
    }
  }
}

/**
 * State holder for Library Insights screen.
 */
data class LibraryInsightsState(
  val isLoading: Boolean = true,
  val isEmpty: Boolean = false,
  val totalWatchTimeMs: Long = 0L,
  val uniqueVideosWatched: Int = 0,
  val completedVideos: Int = 0,
  val totalHistoryItems: Int = 0,
  val recentlyWatched: List<InsightVideo> = emptyList(),
  val mostWatched: List<InsightVideo> = emptyList(),
)

/**
 * Represents a video in the insights display.
 */
data class InsightVideo(
  val filePath: String,
  val displayName: String,
  val lastPlayedTimestamp: Long,
  val playCount: Int,
  val duration: Long = 0L,
)
