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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
  val systemReduceMotion = rememberSystemAnimationsDisabled()
  val motionStyle by preferences.motionStyle.collectAsState()
  val performanceMode by preferences.performanceMode.collectAsState()

  return MotionPolicy(
    reduceMotion = systemReduceMotion,
    motionStyle = motionStyle,
    performanceMode = performanceMode,
  )
}

/**
 * Observes whether the system has disabled animators: the "Animator duration scale" / "Remove
 * animations" setting, and Battery Saver, which also turns animators off without changing that
 * setting. Changes take effect immediately instead of on next launch.
 */
@Composable
fun rememberSystemAnimationsDisabled(): Boolean {
  val context = LocalContext.current
  var disabled by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
  DisposableEffect(context) {
    val handler = Handler(Looper.getMainLooper())
    val resolver = context.contentResolver
    // The framework updates ValueAnimator's scale shortly after the setting/broadcast arrives,
    // so re-read it on the next main-loop turn.
    val refresh = Runnable { disabled = !ValueAnimator.areAnimatorsEnabled() }
    val observer =
      object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
          handler.post(refresh)
        }
      }
    val powerSaveReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          receiverContext: Context?,
          intent: Intent?,
        ) {
          handler.post(refresh)
        }
      }
    resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
    ContextCompat.registerReceiver(
      context,
      powerSaveReceiver,
      IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    refresh.run()
    onDispose {
      resolver.unregisterContentObserver(observer)
      context.unregisterReceiver(powerSaveReceiver)
      handler.removeCallbacks(refresh)
    }
  }
  return disabled
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

  /** Duration tokens for the few tween-based transitions (springs remain the default). */
  object Duration {
    const val MICRO = 120
    const val SHORT = 200
    const val MEDIUM = 300
    const val LONG = 400
  }

  /** A tween that snaps when motion is Off and shortens to [Duration.MICRO] when motion is reduced. */
  @Composable
  fun <T> tween(
    durationMillis: Int,
    easing: Easing = FastOutSlowInEasing,
  ): FiniteAnimationSpec<T> {
    val motionPolicy = policy()
    return when {
      motionPolicy.shouldDisableAnimations -> snap()
      motionPolicy.shouldReduceAnimations ->
        androidx.compose.animation.core.tween(minOf(durationMillis, Duration.MICRO), easing = easing)
      else -> androidx.compose.animation.core.tween(durationMillis, easing = easing)
    }
  }

  /**
   * A gentle breathing alpha for idle states. Returns a constant [maxAlpha] under reduced or disabled
   * motion so no infinite animation runs for users who asked for less movement.
   */
  @Composable
  fun pulseAlpha(
    label: String,
    minAlpha: Float = 0.6f,
    maxAlpha: Float = 1f,
    durationMillis: Int = 2500,
  ): Float {
    if (shouldReduceMotion()) return maxAlpha
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
      initialValue = minAlpha,
      targetValue = maxAlpha,
      animationSpec =
        infiniteRepeatable(
          animation = androidx.compose.animation.core.tween(durationMillis, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = label,
    )
    return alpha
  }

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
