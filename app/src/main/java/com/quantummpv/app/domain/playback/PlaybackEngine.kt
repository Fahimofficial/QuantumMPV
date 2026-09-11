/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.domain.playback

import android.view.View
import android.view.ViewGroup

/**
 * Common lifecycle and transport surface for QuantumMPV playback engines.
 *
 * The existing MPV player remains the production engine for all current playback. Media3 can
 * implement this contract incrementally without leaking engine-specific state into the UI.
 */
interface PlaybackEngine {
  val type: PlaybackEngineType
  val isInitialized: Boolean

  fun attach(container: ViewGroup)

  fun prepare(request: PlaybackRequest)

  fun play()

  fun pause()

  fun seekTo(positionMs: Long)

  fun setPlaybackSpeed(speed: Float)

  fun currentPositionMs(): Long

  fun durationMs(): Long

  fun release()

  fun asView(): View?
}

enum class PlaybackEngineType {
  MPV,
  MEDIA3,
}

data class PlaybackRequest(
  val uri: String,
  val title: String? = null,
  val headers: Map<String, String> = emptyMap(),
  val mimeType: String? = null,
  val startPositionMs: Long = 0L,
  val playWhenReady: Boolean = true,
)
