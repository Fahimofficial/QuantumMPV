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
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvMedia3StateMapperTest {
  private fun snapshot(
    phase: PlaybackPhase,
    paused: Boolean = false,
    endOfFileReached: Boolean = false,
    positionSeconds: Double? = 10.0,
    durationSeconds: Double? = 100.0,
    speed: Double? = 1.0,
    hasQueue: Boolean = true,
    hasNextItem: Boolean = false,
    hasPreviousItem: Boolean = false,
  ) = MpvMedia3StateMapper.snapshot(
    phase = phase,
    paused = paused,
    endOfFileReached = endOfFileReached,
    positionSeconds = positionSeconds,
    durationSeconds = durationSeconds,
    speed = speed,
    hasQueue = hasQueue,
    hasNextItem = hasNextItem,
    hasPreviousItem = hasPreviousItem,
  )

  @Test
  fun anUninitializedSessionIsIdle() {
    val state = snapshot(PlaybackPhase.UNINITIALIZED)

    assertEquals(MpvMedia3PlaybackState.IDLE, state.playbackState)
    assertFalse(state.isLoading)
  }

  @Test
  fun loadingPhasesReportBuffering() {
    listOf(PlaybackPhase.INITIALIZING, PlaybackPhase.LOADING).forEach { phase ->
      val state = snapshot(phase)

      assertEquals(MpvMedia3PlaybackState.BUFFERING, state.playbackState, "phase $phase")
      assertTrue(state.isLoading, "phase $phase")
    }
  }

  @Test
  fun readyAndUnpausedReportsPlaying() {
    val state = snapshot(PlaybackPhase.READY, paused = false)

    assertEquals(MpvMedia3PlaybackState.READY, state.playbackState)
    assertTrue(state.playWhenReady)
    assertFalse(state.isLoading)
  }

  @Test
  fun readyAndPausedReportsPaused() {
    val state = snapshot(PlaybackPhase.READY, paused = true)

    assertEquals(MpvMedia3PlaybackState.READY, state.playbackState)
    assertFalse(state.playWhenReady)
  }

  @Test
  fun backgroundPlaybackKeepsTheSameReportingAsForeground() {
    val state = snapshot(PlaybackPhase.BACKGROUND, paused = false)

    assertEquals(MpvMedia3PlaybackState.READY, state.playbackState)
    assertTrue(state.playWhenReady)
  }

  @Test
  fun idleAndStoppingPhasesReportIdle() {
    listOf(PlaybackPhase.IDLE, PlaybackPhase.STOPPING).forEach { phase ->
      assertEquals(MpvMedia3PlaybackState.IDLE, snapshot(phase).playbackState, "phase $phase")
    }
  }

  @Test
  fun reachingTheEndOfTheFileReportsEnded() {
    assertEquals(
      MpvMedia3PlaybackState.ENDED,
      snapshot(PlaybackPhase.READY, endOfFileReached = true).playbackState,
    )
    assertEquals(
      MpvMedia3PlaybackState.ENDED,
      snapshot(PlaybackPhase.IDLE, endOfFileReached = true).playbackState,
    )
  }

  @Test
  fun anErrorIsReportedAsError() {
    assertEquals(MpvMedia3PlaybackState.ERROR, snapshot(PlaybackPhase.ERROR).playbackState)
  }

  @Test
  fun unsetOrInvalidNumbersAreSanitized() {
    val state =
      snapshot(
        phase = PlaybackPhase.READY,
        positionSeconds = null,
        durationSeconds = null,
        speed = Double.NaN,
      )

    assertEquals(0L, state.positionMs)
    assertEquals(0L, state.durationMs)
    assertEquals(MpvMedia3StateMapper.DEFAULT_SPEED, state.speed)
    assertFalse(state.capabilities.canSeek)
  }

  @Test
  fun nonPositiveNumbersAreTreatedAsUnset() {
    assertEquals(0L, MpvMedia3StateMapper.secondsToMillis(-5.0))
    assertEquals(0L, MpvMedia3StateMapper.secondsToMillis(0.0))
    assertEquals(0L, MpvMedia3StateMapper.secondsToMillis(Double.NaN))
    assertEquals(12_500L, MpvMedia3StateMapper.secondsToMillis(12.5))
    assertEquals(MpvMedia3StateMapper.DEFAULT_SPEED, MpvMedia3StateMapper.playbackSpeed(0.0))
    assertEquals(MpvMedia3StateMapper.DEFAULT_SPEED, MpvMedia3StateMapper.playbackSpeed(-2.0))
    assertEquals(2.0f, MpvMedia3StateMapper.playbackSpeed(2.0))
  }

  @Test
  fun positionNeverExceedsTheReportedDuration() {
    assertEquals(9_000L, MpvMedia3StateMapper.positionMs(9.0, 9_000L))
    assertEquals(9_000L, MpvMedia3StateMapper.positionMs(20.0, 9_000L))
    assertEquals(20_000L, MpvMedia3StateMapper.positionMs(20.0, 0L))
  }

  @Test
  fun capabilitiesFollowThePhaseDurationAndQueue() {
    val capabilities = snapshot(PlaybackPhase.READY, hasNextItem = true).capabilities

    assertTrue(capabilities.isPlayable)
    assertTrue(capabilities.canSeek)
    assertTrue(capabilities.hasNextItem)
    assertFalse(capabilities.hasPreviousItem)
    assertTrue(capabilities.hasQueue)

    val uninitialized = snapshot(PlaybackPhase.UNINITIALIZED, durationSeconds = null, hasQueue = false).capabilities
    assertFalse(uninitialized.isPlayable)
    assertFalse(uninitialized.canSeek)
    assertFalse(uninitialized.hasQueue)
  }
}
