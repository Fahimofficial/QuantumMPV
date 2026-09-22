/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.media3

import com.quantummpv.app.ui.player.PlaybackPhase

/** Playback state as libmpv reports it, expressed in Media3 terms. */
enum class MpvMedia3PlaybackState {
  IDLE,
  BUFFERING,
  READY,
  ENDED,
  ERROR,
}

/**
 * A single read of libmpv's playback state, translated for the Android media integration layer.
 *
 * [MpvMedia3Player] re-reads this on every Media3 state invalidation, so libmpv stays the single
 * source of truth and no playback state is mirrored anywhere else.
 */
data class MpvMedia3PlaybackSnapshot(
  val playbackState: MpvMedia3PlaybackState,
  val playWhenReady: Boolean,
  val isLoading: Boolean,
  val positionMs: Long,
  val durationMs: Long,
  val speed: Float,
  val capabilities: MpvMedia3Capabilities,
)

/**
 * Translates libmpv playback state into the values Media3 expects.
 *
 * Kept free of Media3 and Android types so the mapping is unit-testable and so the mapping rules
 * are reviewable in one place.
 */
object MpvMedia3StateMapper {
  const val DEFAULT_SPEED = 1.0f
  private const val MILLIS_PER_SECOND = 1000

  /** Phases in which libmpv holds media and external transport commands are meaningful. */
  private val PLAYABLE_PHASES =
    setOf(
      PlaybackPhase.INITIALIZING,
      PlaybackPhase.LOADING,
      PlaybackPhase.READY,
      PlaybackPhase.BACKGROUND,
    )

  fun playbackState(
    phase: PlaybackPhase,
    endOfFileReached: Boolean,
  ): MpvMedia3PlaybackState =
    when (phase) {
      PlaybackPhase.UNINITIALIZED -> MpvMedia3PlaybackState.IDLE
      PlaybackPhase.INITIALIZING, PlaybackPhase.LOADING -> MpvMedia3PlaybackState.BUFFERING
      PlaybackPhase.IDLE, PlaybackPhase.STOPPING ->
        if (endOfFileReached) MpvMedia3PlaybackState.ENDED else MpvMedia3PlaybackState.IDLE
      PlaybackPhase.READY, PlaybackPhase.BACKGROUND ->
        if (endOfFileReached) MpvMedia3PlaybackState.ENDED else MpvMedia3PlaybackState.READY
      PlaybackPhase.ERROR -> MpvMedia3PlaybackState.ERROR
    }

  /**
   * Maps the Media3 notion of "should be playing" onto libmpv's `pause` property.
   *
   * `playWhenReady` is reported independently of [MpvMedia3PlaybackState], exactly like libmpv
   * tracks "paused" independently of whether a file is loaded.
   */
  fun playWhenReady(paused: Boolean): Boolean = !paused

  fun snapshot(
    phase: PlaybackPhase,
    paused: Boolean,
    endOfFileReached: Boolean,
    positionSeconds: Double?,
    durationSeconds: Double?,
    speed: Double?,
    hasQueue: Boolean,
    hasNextItem: Boolean,
    hasPreviousItem: Boolean,
  ): MpvMedia3PlaybackSnapshot {
    val playbackState = playbackState(phase, endOfFileReached)
    val durationMs = secondsToMillis(durationSeconds)
    return MpvMedia3PlaybackSnapshot(
      playbackState = playbackState,
      playWhenReady = playWhenReady(paused),
      isLoading = playbackState == MpvMedia3PlaybackState.BUFFERING,
      positionMs = positionMs(positionSeconds, durationMs),
      durationMs = durationMs,
      speed = playbackSpeed(speed),
      capabilities =
        capabilities(
          phase = phase,
          durationMs = durationMs,
          hasQueue = hasQueue,
          hasNextItem = hasNextItem,
          hasPreviousItem = hasPreviousItem,
        ),
    )
  }

  fun capabilities(
    phase: PlaybackPhase,
    durationMs: Long,
    hasQueue: Boolean,
    hasNextItem: Boolean,
    hasPreviousItem: Boolean,
  ): MpvMedia3Capabilities =
    MpvMedia3Capabilities(
      isPlayable = phase in PLAYABLE_PHASES,
      canSeek = durationMs > 0L,
      hasNextItem = hasNextItem,
      hasPreviousItem = hasPreviousItem,
      hasQueue = hasQueue,
    )

  /** libmpv reports seconds as a possibly-unset double; Media3 works in milliseconds. */
  fun secondsToMillis(seconds: Double?): Long {
    val value = seconds?.takeIf { it.isFinite() && it > 0.0 } ?: return 0L
    return (value * MILLIS_PER_SECOND).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
  }

  fun positionMs(
    positionSeconds: Double?,
    durationMs: Long,
  ): Long {
    val seconds = positionSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return 0L
    val millis = (seconds * MILLIS_PER_SECOND).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
    return if (durationMs > 0L) millis.coerceIn(0L, durationMs) else millis.coerceAtLeast(0L)
  }

  /** libmpv rejects non-positive or non-finite speeds, so they never reach the adapter. */
  fun playbackSpeed(speed: Double?): Float {
    val value = speed?.takeIf { it.isFinite() && it > 0.0 } ?: return DEFAULT_SPEED
    return value.toFloat().takeIf { it.isFinite() && it > 0f } ?: DEFAULT_SPEED
  }
}
