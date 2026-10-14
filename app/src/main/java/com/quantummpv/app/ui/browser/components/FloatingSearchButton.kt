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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.GlassComponents

private const val PRESSED_SCALE = 0.92f
internal val FloatingSearchButtonSize = 48.dp

/**
 * The dedicated circular Search action beside the main navigation pill.
 * Its caller owns visibility so the pill and button animate as one dock.
 */
@Composable
fun FloatingSearchButton(
  onSearchClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val reduceMotion = AppMotion.shouldReduceMotion()
  val scale by animateFloatAsState(
    targetValue = if (isPressed && !reduceMotion) PRESSED_SCALE else 1f,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.Snappy, AppMotion.ReducedAlpha),
    label = "floating_search_press",
  )

  GlassComponents.GlassNavigationPill(
    forceGlass = true,
    shape = CircleShape,
    modifier = modifier,
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
        modifier = Modifier.size(26.dp),
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
