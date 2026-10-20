/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.utils

import androidx.activity.BackEventCompat
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.quantummpv.app.preferences.PlayerPreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.player.NavigationAnimStyle
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** The Appearance slider is a duration multiplier: smaller values finish sooner. */
internal fun navigationDurationMillis(speed: Float, baseMillis: Int = 300): Int =
  (baseMillis * (speed.takeIf { it.isFinite() } ?: 1f).coerceIn(0.25f, 2.5f)).roundToInt()

/**
 * One host for full screens and nested panes, including system/predictive Back and saved state.
 * Pass [sharedTransitionScope] (the root host does) to let entries share elements across
 * destinations; each entry reads its own scope from `LocalNavAnimatedContentScope`.
 */
@Composable
internal fun ScreenNavDisplay(
  backStack: NavBackStack<Screen>,
  modifier: Modifier = Modifier,
  sharedTransitionScope: SharedTransitionScope? = null,
  onBack: () -> Unit = { backStack.popSafely() },
  content: @Composable (Screen) -> Unit = { it.Content() },
) {
  val preferences = koinInject<PlayerPreferences>()
  val selectedStyle by preferences.appNavStyle.collectAsState()
  val speed by preferences.animationSpeed.collectAsState()
  // Off motion removes transitions; Reduced motion (system "Remove animations" or the app setting)
  // swaps slides, scales and depth for a plain cross-fade so screens never travel across the display.
  val disableMotion = com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations()
  val reduceMotion = com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion()
  val style =
    when {
      disableMotion -> NavigationAnimStyle.None
      reduceMotion && selectedStyle != NavigationAnimStyle.None -> NavigationAnimStyle.Minimal
      else -> selectedStyle
    }
  val layoutDirection = LocalLayoutDirection.current
  val direction = if (layoutDirection == LayoutDirection.Ltr) 1 else -1

  NavDisplay(
    backStack = backStack,
    modifier = modifier.clipToBounds().background(MaterialTheme.colorScheme.background),
    onBack = onBack,
    sharedTransitionScope = sharedTransitionScope,
    sizeTransform = null,
    transitionSpec = { screenNavTransition(true, style, speed, direction) },
    popTransitionSpec = { screenNavTransition(false, style, speed, direction) },
    predictivePopTransitionSpec = { edge: Int ->
      screenNavTransition(false, style, speed, if (edge == BackEventCompat.EDGE_RIGHT) -1 else 1)
    },
    entryProvider = { route ->
      NavEntry(route) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          content(route)
        }
      }
    },
  )
}

private fun screenNavTransition(
  forward: Boolean,
  style: NavigationAnimStyle,
  speed: Float,
  direction: Int,
): ContentTransform {
  val duration = navigationDurationMillis(speed)

  return when (style) {
    NavigationAnimStyle.None -> EnterTransition.None togetherWith ExitTransition.None
    NavigationAnimStyle.Minimal ->
      fadeIn(tween(duration, easing = FastOutSlowInEasing)) togetherWith
        fadeOut(tween(duration, easing = FastOutSlowInEasing))
    NavigationAnimStyle.FlipFade -> {
      val exitDuration = (duration * 0.35f).roundToInt()
      (fadeIn(tween(duration - exitDuration, delayMillis = exitDuration)) +
        scaleIn(
          tween(duration, easing = FastOutSlowInEasing),
          initialScale = if (forward) 0.96f else 1.04f,
        )) togetherWith fadeOut(tween(exitDuration))
    }
    NavigationAnimStyle.Depth ->
      if (forward) {
        (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction } +
          fadeIn(tween(duration))) togetherWith
          (scaleOut(tween(duration, easing = FastOutSlowInEasing), targetScale = 0.96f) +
            fadeOut(tween(duration), targetAlpha = 0.7f))
      } else {
        (scaleIn(tween(duration, easing = FastOutSlowInEasing), initialScale = 0.96f) +
          fadeIn(tween(duration), initialAlpha = 0.7f)) togetherWith
          (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction } +
            fadeOut(tween(duration)))
      }
    NavigationAnimStyle.Default ->
      if (forward) {
        slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction } togetherWith
          (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it * direction / 4 } +
            fadeOut(tween(duration, easing = FastOutSlowInEasing), targetAlpha = 0.88f))
      } else {
        (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it * direction / 4 } +
          fadeIn(tween(duration, easing = FastOutSlowInEasing), initialAlpha = 0.88f)) togetherWith
          slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction }
      }
  }
}
