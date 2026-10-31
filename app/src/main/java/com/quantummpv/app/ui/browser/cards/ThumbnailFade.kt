/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.browser.cards

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.quantummpv.app.ui.theme.AppMotion

/** Bitmaps delivered faster than this are treated as cache hits and shown without a fade. */
private const val THUMBNAIL_INSTANT_WINDOW_MS = 48L
private const val THUMBNAIL_FADE_MS = 180

/**
 * Opacity for a card thumbnail. Thumbnails that arrive within a couple of frames (memory cache) show
 * at once so fast scrolling stays crisp; ones that had to be decoded or generated fade in over
 * [THUMBNAIL_FADE_MS] instead of popping over the placeholder. Read the value in a graphics layer.
 */
@Composable
internal fun rememberThumbnailFadeAlpha(
  requestKey: Any,
  hasBitmap: Boolean,
): Animatable<Float, AnimationVector1D> {
  val requestedAt = remember(requestKey) { SystemClock.uptimeMillis() }
  val alpha = remember(requestKey) { Animatable(0f) }
  val disableFade = AppMotion.shouldDisableAnimations()
  LaunchedEffect(alpha, hasBitmap) {
    if (!hasBitmap) {
      alpha.snapTo(0f)
      return@LaunchedEffect
    }
    val arrivedLate = SystemClock.uptimeMillis() - requestedAt > THUMBNAIL_INSTANT_WINDOW_MS
    if (arrivedLate && !disableFade && alpha.value < 1f) {
      alpha.animateTo(1f, tween(THUMBNAIL_FADE_MS))
    } else {
      alpha.snapTo(1f)
    }
  }
  return alpha
}
