/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.utils.media

import android.util.Log
import com.quantummpv.app.database.MpvRxDatabase
import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import com.quantummpv.app.ui.browser.videolist.videoPlaybackIdentifiers
import com.quantummpv.app.ui.player.PlaybackIdentity
import com.quantummpv.app.utils.history.RecentlyPlayedOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.io.File

/**
 * Utility for managing playback state when files are renamed or deleted.
 */
object PlaybackStateOps {
  private const val TAG = "PlaybackStateOps"
  private val repository: PlaybackStateRepository by inject(PlaybackStateRepository::class.java)
  private val database: MpvRxDatabase by inject(MpvRxDatabase::class.java)

  suspend fun setWatched(video: Video, watched: Boolean) = setWatched(listOf(video), watched)

  suspend fun setWatched(videos: List<Video>, watched: Boolean) = withContext(Dispatchers.IO) {
    try {
      videos.forEach { video ->
        currentCoroutineContext().ensureActive()
        persistWatched(video, watched)
      }
    } finally {
      PlaybackStateEvents.notifyChanged(videos.singleOrNull()?.let { PlaybackIdentity.forLocalPath(it.path) }.orEmpty())
    }
  }

  suspend fun areAllWatched(videos: List<Video>, threshold: Int): Boolean {
    val watched = watchedPaths(videos, threshold)
    return videos.isNotEmpty() && videos.all { it.path in watched }
  }

  suspend fun watchedPaths(videos: List<Video>, threshold: Int): Set<String> = withContext(Dispatchers.IO) {
    val states = repository.getAllPlaybackStates().associateBy { it.mediaTitle }
    videos.filter { video ->
      val state = videoPlaybackIdentifiers(video).firstNotNullOfOrNull(states::get)
      val duration = video.duration / 1000.0
      state != null && (state.hasBeenWatched ||
        (threshold > 0 && duration > 0 && (duration - state.timeRemaining) / duration >= threshold / 100.0))
    }.mapTo(mutableSetOf()) { it.path }
  }

  suspend fun resetWatchHistory(videos: List<Video>, markAsNew: Boolean) = withContext(Dispatchers.IO) {
    try {
      videos.forEach { video ->
        currentCoroutineContext().ensureActive()
        persistWatched(video, watched = false, newLabelOverride = markAsNew)
        val identifier = PlaybackIdentity.forLocalPath(video.path)
        videoPlaybackIdentifiers(video).filterNot { it == identifier }.forEach { repository.deleteByTitle(it) }
        RecentlyPlayedOps.removeVideoHistory(video)
      }
    } finally {
      PlaybackStateEvents.notifyChanged("")
    }
  }

  suspend fun markLastPlayed(videos: List<Video>): Boolean = withContext(Dispatchers.IO) {
    if (!RecentlyPlayedOps.markLastPlayed(videos)) return@withContext false
    try {
      videos.forEach { video ->
        currentCoroutineContext().ensureActive()
        val existing = videoPlaybackIdentifiers(video).firstNotNullOfOrNull { repository.getVideoDataByTitle(it) }
        if (existing == null) {
          persistWatched(video, watched = false, newLabelOverride = false)
        } else {
          repository.upsert(existing.copy(newLabelOverride = false))
        }
      }
    } finally {
      PlaybackStateEvents.notifyChanged("")
    }
    true
  }

  private suspend fun persistWatched(video: Video, watched: Boolean, newLabelOverride: Boolean? = null) {
    val identifier = PlaybackIdentity.forLocalPath(video.path)
    val existing = videoPlaybackIdentifiers(video).firstNotNullOfOrNull { repository.getVideoDataByTitle(it) }
    val durationSeconds = (video.duration / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val state = existing ?: PlaybackStateEntity(
      mediaTitle = identifier,
      lastPosition = 0,
      playbackSpeed = 1.0,
      sid = -1,
      secondarySid = -1,
      subDelay = 0,
      subSpeed = 1.0,
      aid = -1,
      audioDelay = 0,
    )
    repository.upsert(
      state.copy(
        mediaTitle = identifier,
        lastPosition = 0,
        timeRemaining = if (watched) 0 else durationSeconds,
        hasBeenWatched = watched,
        newLabelOverride = newLabelOverride,
      ),
    )
  }

  /**
   * Called when a video file is renamed
   * Updates the playback state entry to use the new filename
   *
   * @param oldPath The original file path
   * @param newPath The new file path after renaming
   */
  suspend fun onVideoRenamed(
    oldPath: String,
    newPath: String,
  ) {
    if (oldPath.isBlank() || newPath.isBlank()) return

    try {
      val oldFileName = File(oldPath).name
      val newFileName = File(newPath).name

      // Only update if the filename actually changed
      if (oldFileName != newFileName) {
        repository.updateMediaTitle(oldFileName, newFileName)
        Log.d(TAG, "✓ Updated playback state: $oldFileName -> $newFileName")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to update playback state: ${e.message}")
    }
  }

  /**
   * Called when a video file is deleted
   * Removes its playback state entry
   *
   * @param filePath The path of the deleted file
   */
  suspend fun onVideoDeleted(filePath: String) {
    if (filePath.isBlank()) return

    try {
      val fileName = File(filePath).name
      repository.deleteByTitle(fileName)
      Log.d(TAG, "✓ Deleted playback state for: $fileName")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete playback state: ${e.message}")
    }
  }
}
