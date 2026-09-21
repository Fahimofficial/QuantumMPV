/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.media3

import android.content.Intent

/**
 * The QuantumMPV side of the Media3 boundary.
 *
 * The adapter never reaches into the playback service. Everything that depends on service
 * ownership, audio focus, user gesture preferences, or playback persistence is asked of the
 * service through this interface, which keeps Media3 a thin Android integration layer instead of a
 * second playback controller.
 *
 * libmpv remains the playback engine: implementations of this interface translate an Android media
 * command into the same `PlaybackSession` operation the in-app player UI already uses.
 */
interface MpvMedia3SessionHost {
  /**
   * True while transport commands may be applied to the shared libmpv session.
   *
   * This mirrors the service's existing ownership rules (foreground handoff, released native
   * access) so external controllers never act on a playback session the service does not own.
   */
  fun canApplyTransportCommands(): Boolean

  /** Seek step advertised to Media3 for its seek back/forward commands, in milliseconds. */
  fun seekIncrementMillis(): Long

  /** Artwork of the current item as encoded image bytes, or null when none is available. */
  fun currentArtworkBytes(): ByteArray?

  fun onPlayRequested()

  fun onPauseRequested()

  fun onStopRequested()

  fun onSkipToNextRequested()

  fun onSkipToPreviousRequested()

  /** A controller selected an entry of the published queue. */
  fun onSeekToItemRequested(index: Int)

  /** A controller asked for an absolute position inside the current item. */
  fun onSeekToPositionRequested(positionMs: Long)

  /** A controller asked to jump back ([MpvMedia3SeekDirection.BACK]) or forward. */
  fun onIncrementalSeekRequested(direction: Int)

  fun onSetPlaybackSpeedRequested(speed: Float)

  fun onSetShuffleRequested(enabled: Boolean)

  /**
   * Custom session actions this host can actually serve.
   *
   * Controllers only ever see the actions listed here, so a host with no favorite or close surface
   * (the foreground Activity owns the notification actions) advertises none instead of offering a
   * command it would silently drop.
   */
  fun availableCustomActions(): List<String> =
    listOf(MpvMedia3SessionManager.CUSTOM_ACTION_FAVORITE, MpvMedia3SessionManager.CUSTOM_ACTION_CLOSE)

  /** A custom session command, e.g. the notification favorite action. */
  fun onCustomSessionAction(action: String)

  /**
   * A hardware/Bluetooth media button intent, routed by Media3 or by the manifest media button
   * receiver. Returns true when the event was consumed.
   */
  fun onMediaButtonIntent(intent: Intent): Boolean
}

/** Direction values passed to [MpvMedia3SessionHost.onIncrementalSeekRequested]. */
object MpvMedia3SeekDirection {
  const val BACK = -1
  const val FORWARD = 1
}
