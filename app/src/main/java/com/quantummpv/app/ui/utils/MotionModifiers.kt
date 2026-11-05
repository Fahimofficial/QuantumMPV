/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.Dimens
import com.quantummpv.app.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Shrinks to [pressedScale] and optionally lifts by [pressedLift] while [interactionSource] is
 * pressed. The animated value is read only inside graphicsLayer, so pressing never recomposes or
 * relayouts the card. Skipped under reduced motion; the ripple still gives feedback.
 */
@Suppress("ModifierFactoryExtensionFunction")
@Composable
fun Modifier.pressScale(
  interactionSource: InteractionSource,
  shape: Shape = RectangleShape,
  pressedScale: Float = Dimens.PressedScale,
  pressedLift: Dp = 0.dp,
): Modifier {
  if (LocalReduceMotion.current) return this
  val progress = remember { Animatable(0f) }
  val spec = AppMotion.fastSpatial<Float>()
  LaunchedEffect(interactionSource, spec) {
    interactionSource.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
      progress.animateTo(if (interaction is PressInteraction.Press) 1f else 0f, spec)
    }
  }
  return graphicsLayer {
    val p = progress.value
    val scale = 1f + (pressedScale - 1f) * p
    scaleX = scale
    scaleY = scale
    if (pressedLift > 0.dp) {
      this.shape = shape
      shadowElevation = pressedLift.toPx() * p
    }
  }
}

/**
 * Decides once per screen whether the first-open stagger runs. It is active only for the first
 * composition after [ready] turns true (data loaded), never after back navigation, rotation, re-sort
 * or scrolling, because the "played" flag is saveable and the window closes after the stagger ends.
 */
@Stable
class EntranceStagger internal constructor(active: Boolean, val stepMillis: Int, val maxItems: Int) {
  var isActive by mutableStateOf(active)
    internal set
}

@Composable
fun rememberEntranceStagger(
  ready: Boolean,
  stepMillis: Int = AppMotion.Timing.HOME_STAGGER_STEP,
  maxItems: Int = AppMotion.Timing.HOME_STAGGER_ITEMS,
): EntranceStagger {
  var played by rememberSaveable { mutableStateOf(false) }
  val reduceMotion = LocalReduceMotion.current
  val stagger = remember { EntranceStagger(!played && !reduceMotion, stepMillis, maxItems) }
  LaunchedEffect(ready) {
    if (!ready || !stagger.isActive) return@LaunchedEffect
    // Enough for the last staggered row to start and settle; later rows appear without motion.
    delay(stepMillis.toLong() * maxItems + STAGGER_SETTLE_MILLIS)
    stagger.isActive = false
    played = true
  }
  return stagger
}

private const val STAGGER_SETTLE_MILLIS = 450L

/** Fades and slides row [index] up as part of [stagger]; a no-op once the stagger window closed. */
@Suppress("ModifierFactoryExtensionFunction")
@Composable
fun Modifier.staggeredEntrance(
  index: Int,
  stagger: EntranceStagger,
): Modifier {
  // Captured at first composition so a row never switches mode halfway through its animation.
  val animate = remember { stagger.isActive && index < stagger.maxItems }
  if (!animate) return this
  val progress = remember { Animatable(0f) }
  val spec = AppMotion.defaultSpatial<Float>()
  val offsetPx = with(LocalDensity.current) { Dimens.StaggerOffset.toPx() }
  LaunchedEffect(Unit) {
    delay(stagger.stepMillis.toLong() * index)
    progress.animateTo(1f, spec)
  }
  return graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    translationY = (1f - p) * offsetPx
  }
}
