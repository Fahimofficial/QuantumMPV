/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import com.quantummpv.app.database.entities.PlaybackStateEntity

internal const val PLAYBACK_STATE_MILLISECONDS_TO_SECONDS = 1000
internal const val DEFAULT_PLAYBACK_STATE_SPEED = 1.0
internal const val DEFAULT_PLAYBACK_STATE_SUB_SPEED = 1.0

internal data class PlaybackStateSnapshot(
  val mediaIdentifier: String,
  val mediaTitle: String,
  val currentPosition: Int,
  val duration: Int,
  val playbackSpeed: Double,
  val videoZoom: Float,
  val sid: Int,
  val secondarySid: Int,
  val subDelayMs: Int,
  val subSpeed: Double,
  val aid: Int,
  val audioDelayMs: Int,
  /** '|'-joined external subtitle URIs; null when unknown to the writer (keep the saved list). */
  val externalSubtitles: String?,
  val isPositionRestorePending: Boolean = false,
  /** Current `sub-visibility` as chosen by the user; null when unknown (keep the saved value). */
  val subVisibility: Boolean? = null,
  /** Stable track keys ([TrackKeys]); null keeps the saved key when the raw id is unchanged. */
  val sidKey: String? = null,
  val secondarySidKey: String? = null,
  val aidKey: String? = null,
  val mediaLocation: String? = null,
)

internal object PlaybackStatePersistence {
  fun buildEntity(
    oldState: PlaybackStateEntity?,
    snapshot: PlaybackStateSnapshot,
    savePositionOnQuit: Boolean,
    watchedThreshold: Int,
  ): PlaybackStateEntity {
    val lastPosition =
      calculateSavePosition(
        oldState = oldState,
        currentPosition = snapshot.currentPosition,
        duration = snapshot.duration,
        savePositionOnQuit = savePositionOnQuit,
        isPositionRestorePending = snapshot.isPositionRestorePending,
      )
    val duration = snapshot.duration
    val timeRemaining = if (duration > lastPosition) duration - lastPosition else 0

    return PlaybackStateEntity(
      mediaTitle = snapshot.mediaIdentifier,
      lastPosition = lastPosition,
      playbackSpeed = snapshot.playbackSpeed,
      videoZoom = snapshot.videoZoom,
      sid = snapshot.sid,
      secondarySid = snapshot.secondarySid,
      subDelay = snapshot.subDelayMs,
      subSpeed = snapshot.subSpeed,
      aid = snapshot.aid,
      audioDelay = snapshot.audioDelayMs,
      timeRemaining = timeRemaining,
      externalSubtitles = snapshot.externalSubtitles ?: oldState?.externalSubtitles.orEmpty(),
      subVisibility = snapshot.subVisibility ?: oldState?.subVisibility,
      sidKey = snapshot.sidKey ?: oldState?.sidKey?.takeIf { oldState.sid == snapshot.sid },
      secondarySidKey =
        snapshot.secondarySidKey ?: oldState?.secondarySidKey?.takeIf { oldState.secondarySid == snapshot.secondarySid },
      aidKey = snapshot.aidKey ?: oldState?.aidKey?.takeIf { oldState.aid == snapshot.aid },
      mediaLocation = snapshot.mediaLocation ?: oldState?.mediaLocation,
      hasBeenWatched =
        isWatched(
          oldState = oldState,
          currentPosition = snapshot.currentPosition,
          lastPosition = lastPosition,
          duration = duration,
          watchedThreshold = watchedThreshold,
        ),
    )
  }

  fun calculateSavePosition(
    oldState: PlaybackStateEntity?,
    currentPosition: Int,
    duration: Int,
    savePositionOnQuit: Boolean,
    isPositionRestorePending: Boolean = false,
  ): Int {
    if (!savePositionOnQuit) {
      return oldState?.lastPosition ?: 0
    }
    if (isPositionRestorePending) {
      return oldState?.lastPosition ?: currentPosition.coerceAtLeast(0)
    }

    if (duration <= 0) return currentPosition.takeIf { it > 0 } ?: oldState?.lastPosition ?: 0

    return if (currentPosition < duration - 1) currentPosition.coerceAtLeast(0) else 0
  }

  private fun isWatched(
    oldState: PlaybackStateEntity?,
    currentPosition: Int,
    lastPosition: Int,
    duration: Int,
    watchedThreshold: Int,
  ): Boolean {
    // Watched threshold 0 means "Infinitely": the video is never auto-marked as
    // watched based on progress (only an explicit flag keeps it marked).
    if (watchedThreshold == 0) return oldState?.hasBeenWatched == true

    val durationSeconds = duration.toFloat()
    if (durationSeconds <= 0f) return oldState?.hasBeenWatched == true

    val isFinished = currentPosition >= durationSeconds - 1
    val currentProgress = currentPosition.toFloat() / durationSeconds
    val savedProgress = lastPosition.toFloat() / durationSeconds
    val threshold = watchedThreshold / 100f

    return currentProgress >= threshold ||
      isFinished ||
      savedProgress >= threshold ||
      (oldState?.hasBeenWatched == true)
  }
}
