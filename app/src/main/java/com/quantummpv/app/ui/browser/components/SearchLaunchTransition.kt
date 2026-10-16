/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.components.SearchFieldIconAnchor
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Phases of the Search button → search field transition. */
internal enum class SearchFlightPhase {
  /** Nothing in flight; every magnifier is shown normally. */
  Idle,

  /** The icon travels from the Search button to the centre of the screen. */
  ToCenter,

  /** The tab's search field is open; the icon settles into the field's leading slot. */
  ToField,

  /** Search was closed; the icon travels from the field back into the Search button. */
  Returning,
}

/**
 * Runs the floating Search button's launch transition. Exactly one magnifier is visible at any
 * moment: the button's icon is hidden for the whole flight, the travelling icon is drawn by
 * [SearchFlightOverlay], and the field's own icon is hidden until the travelling one lands on it.
 *
 * [K] identifies the tab whose search field is the target (MainScreen.MainTab).
 */
@Stable
internal class SearchLaunchTransition<K : Any> {
  var phase by mutableStateOf(SearchFlightPhase.Idle)
    private set

  /** Tab whose search field the current flight targets or left from. */
  var activeKey by mutableStateOf<K?>(null)
    private set

  // Separate x/y animations with different easings give the flight a gentle arc.
  internal val x = Animatable(0f)
  internal val y = Animatable(0f)
  internal val scale = Animatable(1f)
  internal val fieldReveal = Animatable(1f)

  private val fieldIcons = mutableStateMapOf<K, Offset>()
  private val anchors = HashMap<K, SearchFieldIconAnchor>()
  private var flightJob: Job? = null
  private var flightGeneration = 0L

  /** True while a flight runs; a second Search tap during it is ignored. */
  val isRunning: Boolean get() = phase != SearchFlightPhase.Idle

  /** The Search button hides its own icon while another copy of it is travelling. */
  val hidesButtonIcon: Boolean get() = isRunning

  /** Stable per-tab anchor handed to that tab's InlineSearchBar. */
  fun anchorFor(key: K): SearchFieldIconAnchor =
    anchors.getOrPut(key) {
      object : SearchFieldIconAnchor {
        override val hidesLeadingIcon: Boolean
          get() = activeKey == key && (phase == SearchFlightPhase.ToCenter || phase == SearchFlightPhase.ToField)

        override fun fieldAlpha(): Float =
          if (activeKey == key && phase == SearchFlightPhase.ToField) fieldReveal.value else 1f

        override fun onLeadingIconPositioned(centerInRoot: Offset) {
          fieldIcons[key] = centerInRoot
        }
      }
    }

  /**
   * Flies the icon from [from] to [center], opens the tab's search via [openSearch] on arrival
   * (which focuses the field and raises the keyboard), then settles the icon into the field.
   * Returns false, without doing anything, if a flight is already running.
   */
  fun launch(
    scope: CoroutineScope,
    key: K,
    from: Offset,
    center: Offset,
    openSearch: () -> Unit,
  ): Boolean {
    if (isRunning) return false
    activeKey = key
    phase = SearchFlightPhase.ToCenter
    fieldIcons.remove(key)
    val generation = ++flightGeneration
    flightJob =
      scope.launch {
        try {
          x.snapTo(from.x)
          y.snapTo(from.y)
          scale.snapTo(1f)
          fieldReveal.snapTo(0f)
          flyTo(center, CENTER_SCALE, TO_CENTER_MS)

          openSearch()
          phase = SearchFlightPhase.ToField
          val target =
            withTimeoutOrNull(FIELD_WAIT_MS) {
              snapshotFlow { fieldIcons[key] }.filterNotNull().first()
            }
          if (target == null) {
            // The tab opened a search UI without an InlineSearchBar: just reveal it in place.
            fieldReveal.snapTo(1f)
          } else {
            coroutineScope {
              launch { fieldReveal.animateTo(1f, tween(TO_FIELD_MS, easing = LinearOutSlowInEasing)) }
              flyTo(target, 1f, TO_FIELD_MS)
            }
          }
        } finally {
          finish(generation)
        }
      }
    return true
  }

  /** Flies the icon from the closed field (last known position) back into the button at [to]. */
  fun returnTo(
    scope: CoroutineScope,
    key: K,
    to: Offset,
  ): Boolean {
    val fieldPosition = fieldIcons[key] ?: return false
    val reversingFieldFlight = phase == SearchFlightPhase.ToField && activeKey == key
    if (isRunning && !reversingFieldFlight) return false
    val from = if (reversingFieldFlight) Offset(x.value, y.value) else fieldPosition
    val startScale = if (reversingFieldFlight) scale.value else 1f
    flightJob?.cancel()
    val generation = ++flightGeneration
    activeKey = key
    phase = SearchFlightPhase.Returning
    flightJob =
      scope.launch {
        try {
          x.snapTo(from.x)
          y.snapTo(from.y)
          scale.snapTo(startScale)
          flyTo(to, 1f, RETURN_MS)
        } finally {
          finish(generation)
        }
      }
    return true
  }

  /** Stops any flight immediately and restores every icon (tab switch, Back, leaving the screen). */
  fun cancel() {
    flightGeneration++
    flightJob?.cancel()
    flightJob = null
    phase = SearchFlightPhase.Idle
  }

  private suspend fun flyTo(
    target: Offset,
    targetScale: Float,
    durationMs: Int,
  ) = coroutineScope {
    launch { x.animateTo(target.x, tween(durationMs, easing = LinearOutSlowInEasing)) }
    launch { y.animateTo(target.y, tween(durationMs, easing = EmphasizedDecelerate)) }
    launch { scale.animateTo(targetScale, tween(durationMs, easing = FastOutSlowInEasing)) }
  }

  private fun finish(generation: Long) {
    if (generation != flightGeneration) return
    flightJob = null
    phase = SearchFlightPhase.Idle
  }

  internal companion object {
    const val TO_CENTER_MS = 300
    const val TO_FIELD_MS = 280
    const val RETURN_MS = 260
    const val FIELD_WAIT_MS = 400L
    const val CENTER_SCALE = 1.8f
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
  }
}

/**
 * Draws the travelling magnifier above everything else in MainScreen. All animated values are read
 * in layout/draw lambdas, so a flight costs no recomposition after it starts.
 */
@Composable
internal fun <K : Any> SearchFlightOverlay(
  transition: SearchLaunchTransition<K>,
  modifier: Modifier = Modifier,
) {
  if (!transition.isRunning) return
  var origin by androidx.compose.runtime.remember { mutableStateOf(Offset.Zero) }
  val halfIcon = with(LocalDensity.current) { (FlightIconSize / 2).toPx() }
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .onGloballyPositioned { origin = it.positionInRoot() },
  ) {
    Icon(
      Icons.RoundedFilled.Search,
      // The real button and field carry the accessible labels; this copy is decoration only.
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier =
        Modifier
          .offset {
            IntOffset(
              (transition.x.value - origin.x - halfIcon).roundToInt(),
              (transition.y.value - origin.y - halfIcon).roundToInt(),
            )
          }.size(FlightIconSize)
          .graphicsLayer {
            scaleX = transition.scale.value
            scaleY = transition.scale.value
          },
    )
  }
}

/** Same size as the Search button's icon and the field's leading icon, so hand-offs match. */
private val FlightIconSize = 24.dp
