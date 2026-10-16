/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.GlassComponents

private const val PRESSED_SCALE = 0.92f

/** Matches the navigation pill height so the circle sits on the same baseline and centre line. */
internal val FloatingSearchButtonSize = 52.dp
private val SearchIconSize = 24.dp

/**
 * The dedicated circular Search action beside the main navigation pill. It uses exactly the same
 * container as the pill — solid and bordered in Classic, backdrop glass in glass mode — so the
 * two read as one floating dock. Its caller owns visibility so they animate together.
 */
@Composable
fun FloatingSearchButton(
  onSearchClick: () -> Unit,
  modifier: Modifier = Modifier,
  glass: Boolean = false,
  // True while the launch transition's travelling copy of this icon is on screen.
  hideIcon: Boolean = false,
  onIconPositioned: (Offset) -> Unit = {},
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val reduceMotion = AppMotion.shouldReduceMotion()
  val scale by animateFloatAsState(
    targetValue = if (isPressed && !reduceMotion) PRESSED_SCALE else 1f,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.Snappy, AppMotion.ReducedAlpha),
    label = "floating_search_press",
  )

  NavigationDockSurface(
    glass = glass,
    shape = CircleShape,
    modifier = modifier.size(FloatingSearchButtonSize),
  ) {
    IconButton(
      onClick = onSearchClick,
      interactionSource = interactionSource,
      modifier =
        Modifier
          .size(FloatingSearchButtonSize)
          .tvFocusHighlight(CircleShape, focusedScale = 1.06f)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          },
    ) {
      Icon(
        Icons.RoundedFilled.Search,
        contentDescription = stringResource(R.string.settings_search_title),
        modifier =
          Modifier
            .size(SearchIconSize)
            .onGloballyPositioned { onIconPositioned(it.boundsInRoot().center) }
            .graphicsLayer { alpha = if (hideIcon) 0f else 1f },
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/**
 * The container shared by every floating-dock element. Glass mode uses the lightweight navigation
 * glass; Classic uses the solid tonal surface with a hairline border and soft shadow.
 */
@Composable
internal fun NavigationDockSurface(
  glass: Boolean,
  shape: androidx.compose.ui.graphics.Shape,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  if (glass) {
    GlassComponents.GlassNavigationPill(
      modifier = modifier,
      shape = shape,
      forceGlass = true,
      content = content,
    )
  } else {
    Surface(
      modifier = modifier,
      shape = shape,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
      border = GlassComponents.navigationDockBorder(),
      content = content,
    )
  }
}
