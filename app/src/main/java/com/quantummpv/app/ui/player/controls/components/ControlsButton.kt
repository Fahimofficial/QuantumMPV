/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.LocalPlayerButtonsClickEvent
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.theme.GlassComponents
import com.quantummpv.app.ui.theme.LocalDarkAppColorScheme
import com.quantummpv.app.ui.theme.spacing

private const val PRESSED_SCALE = 0.92f

@Suppress("CompositionLocalAllowlist")
internal val LocalForceDarkPlayerButtonsBackground = staticCompositionLocalOf { false }

@Suppress("CompositionLocalAllowlist")
internal val LocalHidePlayerButtonsBackground = staticCompositionLocalOf { false }

/** Spoken name for a player button whose caller did not pass a title. */
@Suppress("CompositionLocalAllowlist")
internal val LocalPlayerButtonLabel = staticCompositionLocalOf<String?> { null }

@Composable
private fun playerButtonColorScheme(
  forceDark: Boolean =
    LocalForceDarkPlayerButtonsBackground.current && !LocalHidePlayerButtonsBackground.current,
): ColorScheme =
  if (forceDark) LocalDarkAppColorScheme.current ?: MaterialTheme.colorScheme else MaterialTheme.colorScheme

@Composable
internal fun playerButtonContainerColor(): Color =
  playerButtonColorScheme().surfaceContainer.copy(alpha = 0.55f)

@Composable
internal fun playerButtonContentColor(): Color = playerButtonColorScheme().onSurface

@Composable
internal fun playerButtonBorderColor(): Color =
  playerButtonColorScheme().outlineVariant.copy(alpha = 0.4f)

@Suppress("ModifierClickableOrder")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsButton(
  icon: AppIcon,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: () -> Unit = {},
  title: String? = null,
  color: Color? = null,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val hideBackground = LocalHidePlayerButtonsBackground.current
  val surfaceStyle = LocalGlassConfig.current.style
  val useGlassSurface = surfaceStyle != SurfaceStyle.CLASSIC && surfaceStyle != SurfaceStyle.MINIMAL
  val resolvedColor = color ?: playerButtonContentColor()
  val label = title ?: LocalPlayerButtonLabel.current

  val clickEvent = LocalPlayerButtonsClickEvent.current
  // Same press language as the dock and menu tiles: a short spring down to 0.92, read in the layer
  // phase so it never recomposes the HUD. Off under Reduced motion.
  val isPressed by interactionSource.collectIsPressedAsState()
  val reduceMotion = AppMotion.shouldReduceMotion()
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed && enabled && !reduceMotion) PRESSED_SCALE else 1f,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.Snappy, AppMotion.ReducedAlpha),
    label = "controls_button_press",
  )
  // At least 48 dp to touch and to accessibility services, whatever size the HUD draws it at.
  Surface(
    modifier =
      Modifier
        .minimumInteractiveComponentSize()
        .then(modifier)
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        }.tvFocusHighlight(CircleShape, enabled)
        .clip(CircleShape)
        .combinedClickable(
          enabled = enabled,
          onClick = {
            clickEvent()
            onClick()
          },
          onLongClick = {
            clickEvent()
            onLongClick()
          },
          interactionSource = interactionSource,
          indication = ripple(),
          role = Role.Button,
        ),
    shape = CircleShape,
    color = if (hideBackground || useGlassSurface) Color.Transparent else playerButtonContainerColor(),
    contentColor = resolvedColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border =
      if (hideBackground || useGlassSurface) {
        null
      } else {
        BorderStroke(
          1.dp,
          playerButtonBorderColor(),
        )
      },
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (useGlassSurface && !hideBackground) {
        GlassComponents.GlassPlayerSurface(
          modifier = Modifier.matchParentSize(),
          shape = CircleShape,
          isOnVideo = true,
        ) {}
      }
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (enabled) resolvedColor else resolvedColor.copy(alpha = 0.38f),
        modifier =
          Modifier
            .padding(MaterialTheme.spacing.small)
            .size(20.dp),
      )
    }
  }
}

@Composable
fun ControlsGroup(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  val spacing = MaterialTheme.spacing

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement =
      androidx.compose.foundation.layout.Arrangement
        .spacedBy(spacing.extraSmall),
    content = content,
  )
}

@Preview
@Composable
private fun PreviewControlsButton() {
  ControlsButton(
    Icons.RoundedFilled.CatchingPokemon,
    onClick = {},
  )
}
