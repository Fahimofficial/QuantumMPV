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
import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import com.quantummpv.app.ui.browser.videolist.videoPlaybackIdentifiers
import com.quantummpv.app.ui.player.PlaybackIdentity
import com.quantummpv.app.utils.history.RecentlyPlayedOps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

/**
 * Utility for managing playback state when files are renamed or deleted.
 */
object PlaybackStateOps {
  private const val TAG = "PlaybackStateOps"
  private val repository: PlaybackStateRepository by inject(PlaybackStateRepository::class.java)
  private val reconciler by lazy { PlaybackStateReconciler(repository) }

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
   * Called after the app renamed or moved a media file. Carries the saved resume position, watched
   * flag and track choices over to the new location's playback identity.
   *
   * @param oldPath The original file path (or URI)
   * @param newPath The new file path (or URI) after the rename/move
   * @param oldContentUri The MediaStore/SAF URI the file had before the operation, when known
   */
  suspend fun onVideoRenamed(
    oldPath: String,
    newPath: String,
    oldContentUri: String? = null,
  ) {
    if (oldPath.isBlank() || newPath.isBlank()) return
    try {
      if (reconciler.onRenamedOrMoved(oldPath, newPath, oldContentUri)) {
        PlaybackStateEvents.notifyChanged(PlaybackStateReconciler.primaryIdentifier(newPath))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Failed to move playback state: ${e.message}")
    }
  }

  /**
   * Called after the app deleted a media file. Removes every playback-state row that belongs to it.
   *
   * @param filePath The path (or URI) of the deleted file
   * @param contentUri The MediaStore/SAF URI the file had, when known
   */
  suspend fun onVideoDeleted(
    filePath: String,
    contentUri: String? = null,
  ) {
    if (filePath.isBlank()) return
    try {
      reconciler.onDeleted(filePath, contentUri)
      PlaybackStateEvents.notifyChanged("")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete playback state: ${e.message}")
    }
  }
}
