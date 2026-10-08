/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.theme.DesignTokens.MotionStyle
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import org.koin.compose.koinInject

/**
 * mpvRx motion policy — respects system reduce-motion accessibility setting and user preferences.
 * When reduce-motion is true, all animations use non-bouncing standard specs.
 */
@Stable
data class MotionPolicy(
  val reduceMotion: Boolean = false,
  val motionStyle: MotionStyle = MotionStyle.STANDARD,
  val performanceMode: PerformanceMode = PerformanceMode.AUTOMATIC,
) {
  /**
   * Returns true if animations should be reduced or disabled.
   */
  val shouldReduceAnimations: Boolean
    get() = reduceMotion || motionStyle == MotionStyle.OFF || motionStyle == MotionStyle.REDUCED

  /**
   * Returns true if all animations should be completely disabled.
   */
  val shouldDisableAnimations: Boolean
    get() = motionStyle == MotionStyle.OFF

  /**
   * Returns the animation intensity multiplier based on motion style.
   */
  val intensityMultiplier: Float
    get() = when {
      motionStyle == MotionStyle.OFF -> 0f
      motionStyle == MotionStyle.REDUCED -> 0.5f
      motionStyle == MotionStyle.STANDARD -> 0.75f
      else -> 1f
    }

  /**
   * Returns true if dynamic effects should be enabled based on performance mode.
   */
  val shouldEnableDynamicEffects: Boolean
    get() = performanceMode.enableDynamicEffects && !reduceMotion && motionStyle != MotionStyle.OFF
}

val LocalMotionPolicy = staticCompositionLocalOf { MotionPolicy() }

/** Combines the system animator setting with observed motion and performance preferences. */
@Composable
fun rememberMotionPolicy(): MotionPolicy {
  val preferences = koinInject<AppearancePreferences>()
  val systemReduceMotion = !ValueAnimator.areAnimatorsEnabled()
  val motionStyle by preferences.motionStyle.collectAsState()
  val performanceMode by preferences.performanceMode.collectAsState()

  return MotionPolicy(
    reduceMotion = systemReduceMotion,
    motionStyle = motionStyle,
    performanceMode = performanceMode,
  )
}

/**
 * Centralized motion specification object with spring-based animations.
 * Spring animations feel more natural than duration-based tweens.
 */
object AppMotion {
  @Composable
  fun policy(): MotionPolicy = LocalMotionPolicy.current

  /** Selects an immediate snap for Off, [reduced] for reduced motion, or [spec] otherwise. */
  @Composable
  fun <T> spatial(
    spec: FiniteAnimationSpec<T>,
    reduced: FiniteAnimationSpec<T>,
  ): FiniteAnimationSpec<T> {
    val motionPolicy = policy()
    return when {
      motionPolicy.shouldDisableAnimations -> snap()
      motionPolicy.shouldReduceAnimations -> reduced
      else -> spec
    }
  }

  /** Returns whether system or user settings request reduced or disabled motion. */
  @Composable
  fun shouldReduceMotion(): Boolean = policy().shouldReduceAnimations

  /** Returns whether the user selected the Off motion style. */
  @Composable
  fun shouldDisableAnimations(): Boolean = policy().shouldDisableAnimations

  /** Returns the selected motion style's animation intensity, from 0 (off) to 1 (full). */
  @Composable
  fun intensityMultiplier(): Float = policy().intensityMultiplier

  /** Returns whether performance, system motion, and user motion settings allow dynamic effects. */
  @Composable
  fun shouldEnableDynamicEffects(): Boolean = policy().shouldEnableDynamicEffects

  fun <T> noBounce(stiffness: Float): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)

  val ReducedAlpha: SpringSpec<Float> = noBounce(stiffness = Spring.StiffnessMedium)
  val ReducedOffset: SpringSpec<IntOffset> = noBounce(stiffness = Spring.StiffnessMedium)
  val ReducedIntSize: SpringSpec<IntSize> = noBounce(stiffness = Spring.StiffnessMedium)
  val ReducedDp: SpringSpec<Dp> = noBounce(stiffness = Spring.StiffnessMedium)

  /** IntSize-specific spring for expandVertically/shrinkVertically animations. */
  val IntSizeSpring: SpringSpec<IntSize> =
    spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessMediumLow,
    )

  /** Spatial animations — can overshoot for expressive feel. */
  object Spatial {
    val ExpressiveDefault: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 700f)
    val ExpressiveFast: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    val ExpressiveSlow: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 300f)
    val StandardDefault: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 380f)
    val Expressive: SpringSpec<Float> = ExpressiveDefault
    val Standard: SpringSpec<Float> = StandardDefault
    val Snappy: SpringSpec<Float> = ExpressiveFast
    val SnappyDp: SpringSpec<Dp> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Dp-specific variants for animateDpAsState. */
    val ExpressiveDp: SpringSpec<Dp> = spring(dampingRatio = 0.9f, stiffness = 700f)
    val StandardDp: SpringSpec<Dp> = spring(dampingRatio = 1f, stiffness = 380f)
  }

  /** Effect animations — color and alpha transitions (no overshoot). */
  object Effect {
    val Color: SpringSpec<Color> =
      spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
      )
    val Alpha: SpringSpec<Float> =
      spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
      )
  }
}

/**
 * Elevation tokens — consistent shadow/tonal elevation levels.
 */
object ElevationTokens {
  val Level0 = 0.dp
  val Level1 = 1.dp
  val Level2 = 3.dp
  val Level3 = 6.dp
  val Level4 = 8.dp
  val Level5 = 12.dp
}
