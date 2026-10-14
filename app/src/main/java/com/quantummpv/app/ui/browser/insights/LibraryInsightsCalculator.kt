/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.insights

import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.database.entities.RecentlyPlayedEntity

/**
 * One distinct video from the play history.
 *
 * @property lastPlayedAt Epoch millis of the most recent time playback of this file started.
 * @property progress Fraction (0..1) of the video reached according to its saved playback position,
 *   or null when it cannot be determined reliably (unknown duration, or no saved position).
 * @property isCompleted True when the app already considers the video watched.
 */
data class InsightVideo(
  val filePath: String,
  val displayName: String,
  val lastPlayedAt: Long,
  val progress: Float?,
  val isCompleted: Boolean,
)

/**
 * Viewing figures derived only from data the app really persists: the play history (one row per
 * file) and each file's saved playback position.
 *
 * Not included on purpose: total watch time, per-video play counts and day-by-day summaries. The
 * history keeps a single row per file and a saved position is only the furthest point reached, so
 * those numbers cannot be computed correctly without new tracking.
 */
data class LibraryInsights(
  val videosPlayed: Int,
  val completed: Int,
  val inProgress: Int,
  val playedLast7Days: Int,
  val continueWatching: List<InsightVideo>,
  val recentlyPlayed: List<InsightVideo>,
)

/** Pure, Android-free calculation of [LibraryInsights] so every edge case can be unit tested. */
object LibraryInsightsCalculator {
  /** Maximum number of entries in each list on the screen. */
  const val LIST_LIMIT = 10

  private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
  private const val MILLIS_PER_SECOND = 1000L

  // Same window the file browser uses to show a video as "in progress".
  private const val MIN_IN_PROGRESS = 0.01f
  private const val MAX_IN_PROGRESS = 0.99f

  /**
   * Builds insights from [history].
   *
   * Duplicate rows for one file are collapsed to the newest. Files for which [fileExists] is false
   * (deleted or no longer accessible) are ignored. Progress mirrors the file browser's rule so both
   * screens agree: it comes from the saved position and the stored duration, and is left unknown
   * when the duration is unknown or no position was saved. A video is completed when it was marked
   * watched or reached [watchedThresholdPercent] (0 means never auto-complete).
   *
   * @param nowMillis Current time, injected so the 7 day window is deterministic.
   * @param playbackStateFor Looks up the saved playback state for a history path, or null.
   */
  fun calculate(
    history: List<RecentlyPlayedEntity>,
    watchedThresholdPercent: Int,
    nowMillis: Long,
    fileExists: (String) -> Boolean,
    playbackStateFor: (String) -> PlaybackStateEntity?,
  ): LibraryInsights {
    val videos =
      history
        .sortedByDescending { it.timestamp }
        .distinctBy { it.filePath }
        .filter { fileExists(it.filePath) }
        .map { toInsightVideo(it, watchedThresholdPercent, playbackStateFor(it.filePath)) }

    val windowStart = nowMillis - WEEK_MILLIS
    val unfinished = videos.filter { isInProgress(it) }

    return LibraryInsights(
      videosPlayed = videos.size,
      completed = videos.count { it.isCompleted },
      inProgress = unfinished.size,
      playedLast7Days = videos.count { it.lastPlayedAt >= windowStart },
      continueWatching = unfinished.take(LIST_LIMIT),
      recentlyPlayed = videos.take(LIST_LIMIT),
    )
  }

  /** Converts a history row and its optional saved state into a displayable [InsightVideo]. */
  private fun toInsightVideo(
    entity: RecentlyPlayedEntity,
    watchedThresholdPercent: Int,
    state: PlaybackStateEntity?,
  ): InsightVideo {
    val progress = progressOf(entity.duration, state)
    val reachedThreshold =
      watchedThresholdPercent > 0 && progress != null && progress >= watchedThresholdPercent / 100f
    return InsightVideo(
      filePath = entity.filePath,
      displayName = displayNameOf(entity),
      lastPlayedAt = entity.timestamp,
      progress = progress,
      isCompleted = state?.hasBeenWatched == true || reachedThreshold,
    )
  }

  /** Returns the fraction reached, or null when [durationMillis] or [state] do not allow a reliable value. */
  private fun progressOf(
    durationMillis: Long,
    state: PlaybackStateEntity?,
  ): Float? {
    val durationSeconds = durationMillis / MILLIS_PER_SECOND
    if (state == null || durationSeconds <= 0) return null
    val watchedSeconds = durationSeconds - state.timeRemaining.toLong()
    return (watchedSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
  }

  /** True for a started, not yet completed video whose progress is within the in-progress window. */
  private fun isInProgress(video: InsightVideo): Boolean {
    val progress = video.progress ?: return false
    return !video.isCompleted && progress in MIN_IN_PROGRESS..MAX_IN_PROGRESS
  }

  /** Prefers the title, then the file name, then the last path segment. */
  private fun displayNameOf(entity: RecentlyPlayedEntity): String =
    entity.videoTitle?.takeIf { it.isNotBlank() }
      ?: entity.fileName.takeIf { it.isNotBlank() }
      ?: entity.filePath.substringAfterLast('/')
}
