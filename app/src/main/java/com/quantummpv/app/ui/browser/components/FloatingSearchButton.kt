/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.GlassComponents

/**
 * A Vetro-inspired floating search button that appears alongside the navigation bar.
 * Positioned to the right of the navbar with context-aware search functionality.
 *
 * Visibility rules:
 * - Hidden during selection mode (selection actions take priority)
 * - Hidden when navbar is hidden (permission denied, empty tabs)
 * - Hidden when mini-player or Quick Play would overlap it
 * - Hidden on narrow screens where space is constrained
 *
 * @param visible Controls overall visibility based on navigation and selection state
 * @param onSearchClick Callback to trigger search for the active destination
 * @param navbarEndOffset Horizontal position where the navbar ends, to position button relative to it
 * @param bottomPadding Bottom padding to align with or clear other floating elements
 * @param modifier Modifier for positioning and styling
 */
@Composable
fun FloatingSearchButton(
  visible: Boolean,
  onSearchClick: () -> Unit,
  navbarEndOffset: Dp,
  bottomPadding: Dp,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val screenWidth = configuration.screenWidthDp.dp
  val isTablet = configuration.smallestScreenWidthDp >= 600
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

  // Hide on narrow screens where the button would crowd the navbar or overlap other controls
  val hasRoom = screenWidth >= 360.dp || isTablet || isLandscape

  var isPressed by remember { mutableStateOf(false) }

  // Pulse animation scale
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.9f else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium,
    ),
    label = "floating_search_scale",
  )

  AnimatedVisibility(
    visible = visible && hasRoom,
    enter = scaleIn(
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
      ),
    ) + fadeIn(),
    exit = scaleOut(
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
    ) + fadeOut(),
    modifier = modifier,
  ) {
    GlassComponents.GlassNavigationPill(
      forceGlass = true,
      shape = CircleShape,
      modifier = Modifier.padding(bottom = bottomPadding),
    ) {
      IconButton(
        onClick = {
          isPressed = true
          onSearchClick()
          isPressed = false
        },
        modifier = Modifier
          .size(52.dp)
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
}
