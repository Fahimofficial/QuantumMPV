/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.utils

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.quantummpv.app.ui.theme.AppMotion

/**
 * The [SharedTransitionScope] around the app's root NavDisplay. Null outside it (previews, nested
 * hosts), in which case shared elements are simply skipped.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Matches the dock's circular Search button with the Search screen's top bar. */
const val SEARCH_MORPH_KEY = "search_morph_key"

/** Bounds spring for the Search morph: no overshoot so the bar never dips past its final edge. */
val SearchMorphBoundsTransform =
  BoundsTransform { _, _ ->
    spring<Rect>(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow,
    )
  }

/**
 * Tags a layout as one end of the Search morph. Returns an empty modifier when either scope is
 * missing or animations are disabled, so callers can apply it unconditionally.
 */
@Composable
fun rememberSearchMorphModifier(
  sharedTransitionScope: SharedTransitionScope?,
  animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
  if (sharedTransitionScope == null || animatedVisibilityScope == null || AppMotion.shouldDisableAnimations()) {
    return Modifier
  }
  return with(sharedTransitionScope) {
    Modifier.sharedElement(
      sharedContentState = rememberSharedContentState(key = SEARCH_MORPH_KEY),
      animatedVisibilityScope = animatedVisibilityScope,
      boundsTransform = SearchMorphBoundsTransform,
    )
  }
}

/**
 * A rounded shape whose corner radius follows the destination's enter/exit transition: [visible]
 * while shown, [hidden] while entering or exiting. Pairing the Search button (circle) with the
 * Search bar (24 dp) this way interpolates the outline as the shared bounds animate.
 */
@Composable
fun AnimatedVisibilityScope?.rememberMorphingCornerShape(
  visible: Dp,
  hidden: Dp,
): Shape {
  if (this == null) return RoundedCornerShape(visible)
  val corner by transition.animateDp(
    transitionSpec = { spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) },
    label = "search_morph_corner",
  ) { state -> if (state == EnterExitState.Visible) visible else hidden }
  return RoundedCornerShape(corner)
}
