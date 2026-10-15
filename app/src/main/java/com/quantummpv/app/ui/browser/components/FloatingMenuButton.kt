/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.DesignTokens
import com.quantummpv.app.ui.theme.GlassComponents

private const val PRESSED_SCALE = 0.92f
private const val OPEN_ROTATION_DEGREES = 90f
private const val PANEL_ENTER_SCALE = 0.94f
internal val FloatingMenuButtonSize = 48.dp
private val MenuIconSize = 24.dp
private val MinRowHeight = 48.dp

/** A compact overflow trigger embedded at the end of the navigation pill. */
@Composable
fun FloatingMenuButton(
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val reduceMotion = AppMotion.shouldReduceMotion()
  val feedbackSpec = AppMotion.spatial(AppMotion.Spatial.Snappy, AppMotion.ReducedAlpha)
  val scale by animateFloatAsState(
    targetValue = if (isPressed && !reduceMotion) PRESSED_SCALE else 1f,
    animationSpec = feedbackSpec,
    label = "floating_menu_press",
  )
  val rotation by animateFloatAsState(
    targetValue = if (expanded && !reduceMotion) OPEN_ROTATION_DEGREES else 0f,
    animationSpec = feedbackSpec,
    label = "floating_menu_rotation",
  )

  IconButton(
    onClick = { onExpandedChange(!expanded) },
    interactionSource = interactionSource,
    modifier =
      modifier
        .size(FloatingMenuButtonSize)
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
      modifier = Modifier.size(MenuIconSize),
      tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * In-window glass menu anchored above the dock. Keeping it in the main composition lets the shared
 * Haze backdrop produce real glass instead of the flat translucent surface used in preview 4.
 * The caller bounds its height; when a short window leaves less room than both rows need, the rows
 * scroll inside the panel instead of being clipped.
 */
@Composable
fun FloatingGlassMenuPanel(
  visible: Boolean,
  onSettingsClick: () -> Unit,
  onLibraryInsightsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val reduceMotion = AppMotion.shouldReduceMotion()
  val disableMotion = AppMotion.shouldDisableAnimations()
  AnimatedVisibility(
    visible = visible,
    enter = when {
      disableMotion -> EnterTransition.None
      reduceMotion -> fadeIn(animationSpec = AppMotion.ReducedAlpha)
      else ->
        expandVertically(
          animationSpec = AppMotion.spatial(AppMotion.IntSizeSpring, AppMotion.ReducedIntSize),
          expandFrom = Alignment.Bottom,
        ) + fadeIn(animationSpec = AppMotion.Effect.Alpha) +
          scaleIn(
            animationSpec = AppMotion.Spatial.Standard,
            initialScale = PANEL_ENTER_SCALE,
            transformOrigin = TransformOrigin(1f, 1f),
          )
    },
    exit = when {
      disableMotion -> ExitTransition.None
      reduceMotion -> fadeOut(animationSpec = AppMotion.ReducedAlpha)
      else ->
        shrinkVertically(
          animationSpec = AppMotion.spatial(AppMotion.IntSizeSpring, AppMotion.ReducedIntSize),
          shrinkTowards = Alignment.Bottom,
        ) + fadeOut(animationSpec = AppMotion.Effect.Alpha) +
          scaleOut(
            animationSpec = AppMotion.Spatial.Standard,
            targetScale = PANEL_ENTER_SCALE,
            transformOrigin = TransformOrigin(1f, 1f),
          )
    },
    modifier = modifier,
  ) {
    GlassComponents.GlassFloatingMenuSurface(
      shape = RoundedCornerShape(DesignTokens.Radius.large),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier =
          Modifier
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
      ) {
        MenuRow(
          icon = Icons.RoundedFilled.Settings,
          label = stringResource(R.string.ui_settings),
          onClick = onSettingsClick,
        )
        MenuRow(
          icon = Icons.RoundedFilled.Insights,
          label = stringResource(R.string.library_insights_title),
          onClick = onLibraryInsightsClick,
        )
      }
    }
  }
}

/** A focused, pressed and touch-accessible menu row. */
@Composable
private fun MenuRow(
  icon: AppIcon,
  label: String,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(DesignTokens.Radius.card)
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = MinRowHeight)
        .tvFocusHighlight(shape, focusedScale = 1.02f)
        .clip(shape)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = DesignTokens.Spacing.md, vertical = DesignTokens.Spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(MenuIconSize), tint = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.width(DesignTokens.Spacing.md))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}
