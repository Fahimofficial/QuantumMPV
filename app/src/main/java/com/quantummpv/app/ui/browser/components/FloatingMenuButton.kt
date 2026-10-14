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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.GlassComponents

/**
 * A floating menu button that opens a glass popup with Settings and Library Insights options.
 * Positioned to the right of the navigation bar, inspired by Vetro's design patterns.
 *
 * The popup appears above the button with a smooth upward animation and glass styling.
 *
 * @param visible Controls overall visibility based on navigation and selection state
 * @param onSettingsClick Callback to open Settings screen
 * @param onLibraryInsightsClick Callback to open Library Insights screen
 * @param navbarEndOffset Horizontal position where the navbar ends
 * @param bottomPadding Bottom padding to align with other floating elements
 * @param modifier Modifier for positioning and styling
 */
@Composable
fun FloatingMenuButton(
  visible: Boolean,
  onSettingsClick: () -> Unit,
  onLibraryInsightsClick: () -> Unit,
  navbarEndOffset: Dp,
  bottomPadding: Dp,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val density = LocalDensity.current
  val screenWidth = configuration.screenWidthDp.dp
  val isTablet = configuration.smallestScreenWidthDp >= 600
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

  // Hide on narrow screens where the button would crowd the navbar
  val hasRoom = screenWidth >= 360.dp || isTablet || isLandscape

  var isMenuExpanded by remember { mutableStateOf(false) }
  var isPressed by remember { mutableStateOf(false) }

  // Scale animation for press feedback
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.9f else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium,
    ),
    label = "floating_menu_scale",
  )

  // Rotation animation for menu icon
  val rotation by animateFloatAsState(
    targetValue = if (isMenuExpanded) 90f else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium,
    ),
    label = "menu_icon_rotation",
  )

  Box(modifier = modifier) {
    // Glass popup menu that appears above the button
    AnimatedVisibility(
      visible = isMenuExpanded,
      enter = slideInVertically(
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow,
        ),
        initialOffsetY = { it / 2 },
      ) + fadeIn(tween(200)) + scaleIn(
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow,
        ),
        initialScale = 0.8f,
      ),
      exit = slideOutVertically(
        animationSpec = tween(200),
        targetOffsetY = { it / 2 },
      ) + fadeOut(tween(150)) + scaleOut(
        animationSpec = tween(200),
        targetScale = 0.8f,
      ),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .offset(y = (-80).dp), // Position above the button
    ) {
      GlassComponents.GlassSurface(
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(16.dp),
      ) {
        Column(
          modifier = Modifier
            .padding(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          MenuOption(
            icon = Icons.RoundedFilled.Settings,
            text = stringResource(R.string.ui_settings),
            onClick = {
              isMenuExpanded = false
              onSettingsClick()
            },
          )
          MenuOption(
            icon = Icons.RoundedFilled.Insights,
            text = stringResource(R.string.library_insights_title),
            onClick = {
              isMenuExpanded = false
              onLibraryInsightsClick()
            },
          )
        }
      }
    }

    // The circular menu button
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
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = bottomPadding),
    ) {
      GlassComponents.GlassNavigationPill(
        forceGlass = true,
        shape = CircleShape,
      ) {
        IconButton(
          onClick = {
            isPressed = true
            isMenuExpanded = !isMenuExpanded
            isPressed = false
          },
          modifier = Modifier
            .size(52.dp)
            .tvFocusHighlight(CircleShape, focusedScale = 1.06f)
            .graphicsLayer {
              scaleX = scale
              scaleY = scale
              rotationZ = rotation
            },
        ) {
          Icon(
            Icons.RoundedFilled.MoreVert,
            contentDescription = stringResource(R.string.ui_menu),
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    // Scrim to dismiss menu when clicking outside
    if (isMenuExpanded) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { isMenuExpanded = false },
          ),
      )
    }
  }
}

@Composable
private fun MenuOption(
  icon: com.quantummpv.app.ui.icons.AppIcon,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(),
        role = Role.Button,
        onClick = onClick,
      )
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start,
  ) {
    Icon(
      icon,
      contentDescription = null,
      modifier = Modifier.size(24.dp),
      tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
