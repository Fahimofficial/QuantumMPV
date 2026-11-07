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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.LocalReduceMotion

/**
 * The [SharedTransitionScope] around the app's root NavDisplay. Null outside it (previews, nested
 * hosts), in which case shared elements are simply skipped.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Matches the dock's circular Search button with the Search screen's top bar. */
const val SEARCH_MORPH_KEY = "search_morph_key"

/**
 * Key for a main tab's inline search bar. Scoped per tab because the pager keeps neighbouring pages
 * composed, so two tabs' bars must never be matched with each other or with MediaSearchScreen.
 */
fun inlineSearchMorphKey(tab: String): String = "$SEARCH_MORPH_KEY:$tab"

/**
 * Morph modifier for the InlineSearchBar of a dock-hosted tab, provided by DockSearchTopBarSwitcher
 * and appended by InlineSearchBar after its caller's modifier. Empty everywhere else.
 */
val LocalInlineSearchMorphModifier = staticCompositionLocalOf<Modifier> { Modifier }

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
  key: String = SEARCH_MORPH_KEY,
): Modifier {
  if (sharedTransitionScope == null || animatedVisibilityScope == null || AppMotion.shouldDisableAnimations()) {
    return Modifier
  }
  return with(sharedTransitionScope) {
    Modifier.sharedElement(
      sharedContentState = rememberSharedContentState(key = key),
      animatedVisibilityScope = animatedVisibilityScope,
      boundsTransform = SearchMorphBoundsTransform,
    )
  }
}

/**
 * Container transforms between screens. Only the single tapped item shares: the tap sets
 * [activeKey] right before navigating, both ends tag themselves only while their key is active, and
 * the destination clears it when it leaves composition.
 */
object SharedNavigation {
  var activeKey by mutableStateOf<String?>(null)

  fun folderKey(bucketId: String) = "folder-$bucketId"

  fun folderTitleKey(bucketId: String) = "folder-title-$bucketId"

  /** Settings rows and other plain destinations share by destination type. */
  fun screenKey(screen: Any) = "screen-${screen::class.java.name}"
}

/**
 * Shares this layout's bounds as one end of a container transform when [activeWhen] is the active
 * key. A no-op otherwise, under reduced motion, or outside the root shared-transition host.
 */
@Composable
fun Modifier.sharedNavBounds(
  key: String,
  shape: Shape,
  activeWhen: String = key,
  scaleContent: Boolean = false,
): Modifier {
  if (SharedNavigation.activeKey != activeWhen || LocalReduceMotion.current) return this
  val sharedScope = LocalSharedTransitionScope.current ?: return this
  val visibilityScope = LocalNavAnimatedContentScope.current
  val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
  val boundsTransform = remember(spec) { BoundsTransform { _, _ -> spec } }
  return with(sharedScope) {
    this@sharedNavBounds.sharedBounds(
      sharedContentState = rememberSharedContentState(key),
      animatedVisibilityScope = visibilityScope,
      boundsTransform = boundsTransform,
      resizeMode =
        if (scaleContent) {
          SharedTransitionScope.ResizeMode.scaleToBounds()
        } else {
          SharedTransitionScope.ResizeMode.RemeasureToBounds
        },
      clipInOverlayDuringTransition = OverlayClip(shape),
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
