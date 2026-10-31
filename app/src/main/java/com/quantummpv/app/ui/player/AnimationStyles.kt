/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.theme.AppMotion
import kotlinx.coroutines.delay

/** Which style to use when player controls appear/disappear. */
enum class ControlsAnimationStyle(
  val displayName: String,
) {
  Default("Default"),
  Cinematic("Cinematic Scale"),
  FluidExpand("Fluid Expand"),
  SpringBounce("Gentle Bounce"),
  Minimal("Minimal Fade"),
  None("None"),
}

/** Animation style when the video first opens. */
enum class VideoOpenAnimation(
  val displayName: String,
) {
  Default("Default"),
  FadeDark("Fade from Black"),
  ZoomBurst("Zoom Burst"),
  SlideUp("Slide Up"),
  CinemaBars("Cinema Bars"),
  None("None"),
}

/** Tracks whether the selected open animation should still cover the player while media loads. */
data class VideoOpenAnimationState(
  val loadToken: Long = 0L,
  val isWaitingForVideo: Boolean = true,
)

/** Animation style for tab / screen navigation. */
enum class NavigationAnimStyle(
  val displayName: String,
) {
  Default("Slide"),
  Depth("Depth Zoom"),
  FlipFade("Fade through"),
  Minimal("Crossfade"),
  None("None"),
}

// ────────────────────────────────────────────────────────────────────────────
// Controls enter / exit helpers
// ────────────────────────────────────────────────────────────────────────────

/**
 * Build an [EnterTransition] for controls that have a *horizontal* natural direction.
 *
 * @param offsetX  lambda that returns the initial horizontal pixel offset (same convention as
 *                 [slideInHorizontally]).  Ignored for styles that don't slide horizontally.
 */
fun buildControlsEnterH(
  style: ControlsAnimationStyle,
  reduceMotion: Boolean,
  enterMs: Int,
  offsetX: (fullWidth: Int) -> Int,
): EnterTransition =
  when {
    style == ControlsAnimationStyle.None -> EnterTransition.None

    // Reduced motion wins over every expressive style: a plain fade, no scale or travel.
    style == ControlsAnimationStyle.Minimal || reduceMotion ->
      fadeIn(tween(durationMillis = enterMs, easing = LinearOutSlowInEasing))

    style == ControlsAnimationStyle.Cinematic ->
      scaleIn(
        spring(
          dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        initialScale = 0.94f,
      ) +
        fadeIn(
          spring(
            dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
            stiffness = AppMotion.Spatial.Expressive.stiffness,
          ),
        )

    style == ControlsAnimationStyle.FluidExpand ->
      scaleIn(
        spring(
          dampingRatio = Spring.DampingRatioLowBouncy,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        initialScale = 0.88f,
      ) + fadeIn(spring(stiffness = AppMotion.Spatial.Expressive.stiffness))

    style == ControlsAnimationStyle.SpringBounce ->
      slideInHorizontally(
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 380f),
        offsetX,
      ) + fadeIn(spring(stiffness = AppMotion.Spatial.Snappy.stiffness))

    else ->
      slideInHorizontally(
        tween(durationMillis = enterMs, easing = LinearOutSlowInEasing),
        offsetX,
      ) +
        fadeIn(
          tween(durationMillis = enterMs, easing = LinearOutSlowInEasing),
        )
  }

fun buildControlsExitH(
  style: ControlsAnimationStyle,
  reduceMotion: Boolean,
  exitMs: Int,
  offsetX: (fullWidth: Int) -> Int,
): ExitTransition =
  when {
    style == ControlsAnimationStyle.None -> ExitTransition.None

    // Reduced motion wins over every expressive style: a plain fade, no scale or travel.
    style == ControlsAnimationStyle.Minimal || reduceMotion ->
      fadeOut(tween(durationMillis = exitMs, easing = FastOutSlowInEasing))

    style == ControlsAnimationStyle.Cinematic ->
      scaleOut(
        spring(
          dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        targetScale = 0.94f,
      ) +
        fadeOut(
          spring(
            dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
            stiffness = AppMotion.Spatial.Expressive.stiffness,
          ),
        )

    style == ControlsAnimationStyle.FluidExpand ->
      scaleOut(
        spring(
          dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        targetScale = 0.88f,
      ) + fadeOut(spring(stiffness = AppMotion.Spatial.Expressive.stiffness))

    style == ControlsAnimationStyle.SpringBounce ->
      slideOutHorizontally(
        spring(
          dampingRatio = AppMotion.Spatial.Standard.dampingRatio,
          stiffness = AppMotion.Spatial.Standard.stiffness,
        ),
        offsetX,
      ) +
        fadeOut(
          spring(
            dampingRatio = AppMotion.Spatial.Standard.dampingRatio,
            stiffness = AppMotion.Spatial.Standard.stiffness,
          ),
        )

    else ->
      slideOutHorizontally(
        tween(durationMillis = exitMs, easing = FastOutSlowInEasing),
        offsetX,
      ) +
        fadeOut(
          tween(durationMillis = exitMs, easing = FastOutSlowInEasing),
        )
  }

/**
 * Build an [EnterTransition] for controls that have a *vertical* natural direction.
 *
 * @param offsetY  lambda returning the initial vertical pixel offset.
 */
fun buildControlsEnterV(
  style: ControlsAnimationStyle,
  reduceMotion: Boolean,
  enterMs: Int,
  offsetY: (fullHeight: Int) -> Int,
): EnterTransition =
  when {
    style == ControlsAnimationStyle.None -> EnterTransition.None

    // Reduced motion wins over every expressive style: a plain fade, no scale or travel.
    style == ControlsAnimationStyle.Minimal || reduceMotion ->
      fadeIn(tween(durationMillis = enterMs, easing = LinearOutSlowInEasing))

    style == ControlsAnimationStyle.Cinematic ->
      scaleIn(
        spring(
          dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        initialScale = 0.94f,
      ) +
        fadeIn(
          spring(
            dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
            stiffness = AppMotion.Spatial.Expressive.stiffness,
          ),
        )

    style == ControlsAnimationStyle.FluidExpand ->
      scaleIn(
        spring(
          dampingRatio = Spring.DampingRatioLowBouncy,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        initialScale = 0.88f,
      ) + fadeIn(spring(stiffness = AppMotion.Spatial.Expressive.stiffness))

    style == ControlsAnimationStyle.SpringBounce ->
      slideInVertically(
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 380f),
        offsetY,
      ) + fadeIn(spring(stiffness = AppMotion.Spatial.Snappy.stiffness))

    else ->
      slideInVertically(
        tween(durationMillis = enterMs, easing = LinearOutSlowInEasing),
        offsetY,
      ) +
        fadeIn(
          tween(durationMillis = enterMs, easing = LinearOutSlowInEasing),
        )
  }

fun buildControlsExitV(
  style: ControlsAnimationStyle,
  reduceMotion: Boolean,
  exitMs: Int,
  offsetY: (fullHeight: Int) -> Int,
): ExitTransition =
  when {
    style == ControlsAnimationStyle.None -> ExitTransition.None

    // Reduced motion wins over every expressive style: a plain fade, no scale or travel.
    style == ControlsAnimationStyle.Minimal || reduceMotion ->
      fadeOut(tween(durationMillis = exitMs, easing = FastOutSlowInEasing))

    style == ControlsAnimationStyle.Cinematic ->
      scaleOut(
        spring(
          dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        targetScale = 0.94f,
      ) +
        fadeOut(
          spring(
            dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
            stiffness = AppMotion.Spatial.Expressive.stiffness,
          ),
        )

    style == ControlsAnimationStyle.FluidExpand ->
      scaleOut(
        spring(
          dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
          stiffness = AppMotion.Spatial.Expressive.stiffness,
        ),
        targetScale = 0.88f,
      ) + fadeOut(spring(stiffness = AppMotion.Spatial.Expressive.stiffness))

    style == ControlsAnimationStyle.SpringBounce ->
      slideOutVertically(
        spring(
          dampingRatio = AppMotion.Spatial.Standard.dampingRatio,
          stiffness = AppMotion.Spatial.Standard.stiffness,
        ),
        offsetY,
      ) +
        fadeOut(
          spring(
            dampingRatio = AppMotion.Spatial.Standard.dampingRatio,
            stiffness = AppMotion.Spatial.Standard.stiffness,
          ),
        )

    else ->
      slideOutVertically(
        tween(durationMillis = exitMs, easing = FastOutSlowInEasing),
        offsetY,
      ) +
        fadeOut(
          tween(durationMillis = exitMs, easing = FastOutSlowInEasing),
        )
  }

// ────────────────────────────────────────────────────────────────────────────
// Video-open animation overlay
// ────────────────────────────────────────────────────────────────────────────

/**
 * Draws a full-screen overlay that stays in place while media is loading, then plays the selected
 * [VideoOpenAnimation] once the video is ready. No-op when [style] is
 * [VideoOpenAnimation.Default], [VideoOpenAnimation.None], or when animations are disabled.
 */
@Composable
fun VideoOpenAnimationOverlay(
  style: VideoOpenAnimation,
  speedMultiplier: Float,
  animationState: VideoOpenAnimationState,
  animationsDisabled: Boolean,
  reduceMotion: Boolean = false,
) {
  if (animationsDisabled || style == VideoOpenAnimation.Default || style == VideoOpenAnimation.None) return
  // Zooms, slides and bars become the plain fade from black when the user asked for less motion.
  val effectiveStyle = if (reduceMotion) VideoOpenAnimation.FadeDark else style

  val durationMs = (AppMotion.Duration.MEDIUM4 * speedMultiplier).toInt().coerceAtLeast(100)
  val holdMs = (120 * speedMultiplier).toInt().coerceAtLeast(50)

  key(animationState.loadToken) {
    var overlayVisible by remember { mutableStateOf(true) }

    LaunchedEffect(animationState.isWaitingForVideo) {
      if (animationState.isWaitingForVideo) {
        overlayVisible = true
        return@LaunchedEffect
      }

      delay(holdMs.toLong())
      overlayVisible = false
    }

    when (effectiveStyle) {
      // Filtered out by the early return above.
      VideoOpenAnimation.Default, VideoOpenAnimation.None -> Unit

      VideoOpenAnimation.FadeDark -> {
        AnimatedVisibility(
          visible = overlayVisible,
          enter = EnterTransition.None,
          exit =
            fadeOut(
              spring(dampingRatio = AppMotion.Effect.Alpha.dampingRatio, stiffness = AppMotion.Effect.Alpha.stiffness),
            ),
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Black),
          )
        }
      }

      VideoOpenAnimation.ZoomBurst -> {
        AnimatedVisibility(
          visible = overlayVisible,
          enter = EnterTransition.None,
          exit =
            scaleOut(
              spring(
                dampingRatio = AppMotion.Spatial.Expressive.dampingRatio,
                stiffness = AppMotion.Spatial.Expressive.stiffness,
              ),
              targetScale = 1.18f,
            ) +
              fadeOut(
                spring(
                  dampingRatio = AppMotion.Effect.Alpha.dampingRatio,
                  stiffness = AppMotion.Effect.Alpha.stiffness,
                ),
              ),
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Black),
          )
        }
      }

      VideoOpenAnimation.SlideUp -> {
        AnimatedVisibility(
          visible = overlayVisible,
          enter = EnterTransition.None,
          exit =
            slideOutVertically(
              spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 200f),
            ) { -it } +
              fadeOut(
                spring(
                  dampingRatio = AppMotion.Effect.Alpha.dampingRatio,
                  stiffness = AppMotion.Effect.Alpha.stiffness,
                ),
              ),
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Black),
          )
        }
      }

      VideoOpenAnimation.CinemaBars -> {
        // Two black bars that hold while loading, then shrink away once the video is ready.
        // Bars keep a fixed 110 dp layout and shrink with a layer scale toward their outer edge, so the
        // spring runs in the draw phase instead of re-measuring both bars on every frame.
        val barFraction by animateFloatAsState(
          targetValue = if (overlayVisible) 1f else 0f,
          animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 180f),
          label = "cinema_bars",
        )
        Box(Modifier.fillMaxSize()) {
          Box(
            Modifier
              .fillMaxWidth()
              .height(110.dp)
              .align(Alignment.TopCenter)
              .graphicsLayer {
                scaleY = barFraction.coerceAtLeast(0f)
                transformOrigin = TransformOrigin(0.5f, 0f)
              }.background(Color.Black),
          )
          Box(
            Modifier
              .fillMaxWidth()
              .height(110.dp)
              .align(Alignment.BottomCenter)
              .graphicsLayer {
                scaleY = barFraction.coerceAtLeast(0f)
                transformOrigin = TransformOrigin(0.5f, 1f)
              }.background(Color.Black),
          )
        }
      }
    }
  }
}
