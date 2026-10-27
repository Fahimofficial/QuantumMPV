/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls

import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.player.SingleActionGesture

/** Horizontal tap zones of the player surface; the side zones' width is the double-tap seek area. */
internal enum class TapRegion { LEFT, CENTER, RIGHT }

/** Pure decisions behind the player gestures, kept apart from pointer handling so they can be tested. */
internal object TapClassifier {
  /**
   * How long a first tap waits for a second one. Android's own timeout (300 ms) would make the common case,
   * a single tap to show the controls, noticeably slower than the 250 ms players have always used here.
   */
  const val DOUBLE_TAP_TIMEOUT_MS = 250L

  /** Rapid taps that keep a double-tap seek going. */
  const val MULTI_TAP_CONTINUE_WINDOW_MS = 650L

  /** How far apart two taps may land and still count as a double tap. */
  val DoubleTapSlop = 48.dp

  /** Drags that start this close to a side edge belong to the system back gesture, not to seeking. */
  val SideEdgeDeadZone = 24.dp

  /** Swipe-up for the playlist only starts in this lower fraction of the surface. */
  const val SWIPE_UP_BAND_FRACTION = 0.4f

  /**
   * Density the horizontal-seek sensitivity was tuned at (seconds per pixel on a ~440 dpi phone). Scaling by
   * it keeps the stored preference meaning the same physical distance on every screen.
   */
  const val SEEK_REFERENCE_DENSITY = 2.75f

  fun region(
    x: Float,
    width: Float,
    seekAreaPercent: Int,
  ): TapRegion {
    val fraction = seekAreaPercent / 100f
    return when {
      x > width * (1f - fraction) -> TapRegion.RIGHT
      x < width * fraction -> TapRegion.LEFT
      else -> TapRegion.CENTER
    }
  }

  /**
   * Whether a single tap in [region] must wait to see if a second tap follows. Only a region whose double
   * tap does something needs to wait; everywhere else the tap acts immediately. Locked controls ignore
   * double taps entirely.
   */
  fun waitsForDoubleTap(
    region: TapRegion,
    locked: Boolean,
    left: SingleActionGesture,
    center: SingleActionGesture,
    right: SingleActionGesture,
  ): Boolean {
    if (locked) return false
    val action =
      when (region) {
        TapRegion.LEFT -> left
        TapRegion.CENTER -> center
        TapRegion.RIGHT -> right
      }
    // The centre zone has no seek action; the view model ignores Seek there.
    return action != SingleActionGesture.None && !(region == TapRegion.CENTER && action == SingleActionGesture.Seek)
  }

  fun isInSideEdgeDeadZone(
    x: Float,
    width: Float,
    leftEdgePx: Float,
    rightEdgePx: Float,
  ): Boolean = x < leftEdgePx || x > width - rightEdgePx

  fun isInSwipeUpBand(
    y: Float,
    height: Float,
  ): Boolean = y >= height * (1f - SWIPE_UP_BAND_FRACTION)

  /** Seconds of media per pixel of horizontal drag for the stored [sensitivity] (seconds per reference px). */
  fun seekSecondsPerPixel(
    sensitivity: Float,
    density: Float,
  ): Float = if (density <= 0f) sensitivity else sensitivity * SEEK_REFERENCE_DENSITY / density

  /** True when the pointer moved less than [slopPx] from where it went down. */
  fun withinSlop(
    dx: Float,
    dy: Float,
    slopPx: Float,
  ): Boolean = dx * dx + dy * dy < slopPx * slopPx
}
