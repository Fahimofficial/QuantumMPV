package com.quantummpv.app.ui.utils

import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.quantummpv.app.preferences.GesturePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import kotlin.math.abs

@Composable
internal fun ProvideAppHaptics(content: @Composable () -> Unit) {
  val preferences = koinInject<GesturePreferences>()
  val enabled by preferences.hapticFeedbackEnabled.collectAsState()
  val currentEnabled by rememberUpdatedState(enabled)
  val platformFeedback = LocalHapticFeedback.current
  val feedback =
    remember(platformFeedback) {
      object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
          if (currentEnabled) platformFeedback.performHapticFeedback(hapticFeedbackType)
        }
      }
    }
  CompositionLocalProvider(LocalHapticFeedback provides feedback, content = content)
}

@Stable
internal class AppHaptics(private val feedback: HapticFeedback) {
  private var lastTickAt = Long.MIN_VALUE

  fun selection(selected: Boolean) {
    feedback.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
  }

  fun pickup() {
    feedback.performHapticFeedback(HapticFeedbackType.LongPress)
  }

  fun confirm() {
    feedback.performHapticFeedback(HapticFeedbackType.Confirm)
  }

  fun tick() {
    val now = SystemClock.uptimeMillis()
    if (lastTickAt != Long.MIN_VALUE && now - lastTickAt < 70L) return
    lastTickAt = now
    feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
  }
}

@Composable
internal fun rememberAppHaptics(): AppHaptics {
  val feedback = LocalHapticFeedback.current
  return remember(feedback) { AppHaptics(feedback) }
}

internal fun View.performAppHapticFeedback(feedback: Int): Boolean {
  val enabled =
    runCatching { GlobalContext.get().get<GesturePreferences>().hapticFeedbackEnabled.get() }.getOrDefault(false)
  return enabled && performHapticFeedback(feedback)
}

internal class AdjustmentHaptics(
  private val haptics: AppHaptics,
  private val markers: List<Float>,
  private val hysteresis: Float,
) {
  private var latchedMarker: Float? = null

  fun move(previous: Float, value: Float) {
    if (!previous.isFinite() || !value.isFinite() || previous == value) return
    if (latchedMarker?.let { abs(value - it) > hysteresis } == true) latchedMarker = null
    val crossed =
      markers.filter { marker ->
        (previous < marker && value >= marker) || (previous > marker && value <= marker)
      }.minByOrNull { abs(value - it) }
    if (crossed != null && crossed != latchedMarker) {
      latchedMarker = crossed
      haptics.tick()
    }
  }
}

@Composable
internal fun rememberAdjustmentHaptics(
  min: Float,
  max: Float,
  steps: Int = 0,
  landmarks: List<Float> = emptyList(),
): AdjustmentHaptics {
  val haptics = rememberAppHaptics()
  return remember(haptics, min, max, steps, landmarks) {
    val markers =
      buildList {
        add(min)
        add(max)
        if (min < 0f && max > 0f) add(0f)
        addAll(landmarks.filter { it in min..max })
        if (steps in 1..20) {
          repeat(steps) { index -> add(min + (max - min) * (index + 1) / (steps + 1)) }
        }
      }.distinct()
    AdjustmentHaptics(haptics, markers, (max - min) * 0.02f)
  }
}