/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.utils

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/**
 * Thumbnail -> player launch animation. PlayerActivity is a separate Activity drawing mpv into a
 * SurfaceView, so a Compose shared element can't cross into it and the surface must not be animated.
 * Instead the player window clip-reveals from the tapped thumbnail's on-screen bounds (a window
 * animation: no extra frames are rendered by the app and playback start isn't delayed).
 */
object PlayerLaunchOrigin {
  private const val MAX_AGE_MS = 1_000L
  private var bounds: Rect? = null
  private var recordedAt = 0L

  /** Called by the tapped thumbnail right before its click handler starts playback. */
  fun record(boundsInWindow: Rect) {
    bounds = boundsInWindow
    recordedAt = SystemClock.uptimeMillis()
  }

  /** Options for the next player launch, or null (normal window animation) when nothing fresh was recorded. */
  fun takeOptions(context: Context): Bundle? {
    val rect = bounds ?: return null
    bounds = null
    if (SystemClock.uptimeMillis() - recordedAt > MAX_AGE_MS || rect.isEmpty) return null
    if (!ValueAnimator.areAnimatorsEnabled()) return null
    val activity = context.findActivity() ?: return null
    return ActivityOptions
      .makeClipRevealAnimation(
        activity.window.decorView,
        rect.left.roundToInt(),
        rect.top.roundToInt(),
        rect.width.roundToInt(),
        rect.height.roundToInt(),
      ).toBundle()
  }

  private tailrec fun Context.findActivity(): Activity? =
    when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
    }
}
