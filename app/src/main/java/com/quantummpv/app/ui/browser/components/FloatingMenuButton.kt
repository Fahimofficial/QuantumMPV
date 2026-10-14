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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.DesignTokens
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.theme.GlassComponents

private const val PRESSED_SCALE = 0.92f
private const val OPEN_ROTATION_DEGREES = 90f
private const val PANEL_ENTER_SCALE = 0.92f
private const val GLASS_PANEL_ALPHA = 0.92f
private val ButtonSize = 52.dp
private val IconSize = 26.dp
private val MinRowHeight = 48.dp
private val PanelGap = DesignTokens.Spacing.sm
private val PanelEdgeMargin = DesignTokens.Spacing.lg

/**
 * Circular menu button that opens a floating panel with exactly two destinations: Settings and
 * Library Insights.
 *
 * The panel lives in a [Popup], so it sits above the mini player and selection bars, dismisses on an
 * outside tap or Back, and is kept inside the window by [AboveAnchorPositionProvider]. All motion
 * goes through [AppMotion], so the Off and Reduced motion styles are respected.
 *
 * @param onSettingsClick Opens the existing Settings destination.
 * @param onLibraryInsightsClick Opens the Library Insights destination.
 */
@Composable
fun FloatingMenuButton(
  onSettingsClick: () -> Unit,
  onLibraryInsightsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val panelState = remember { MutableTransitionState(false) }
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
    targetValue = if (panelState.targetState && !reduceMotion) OPEN_ROTATION_DEGREES else 0f,
    animationSpec = feedbackSpec,
    label = "floating_menu_rotation",
  )

  val density = LocalDensity.current
  val positionProvider =
    remember(density) {
      with(density) { AboveAnchorPositionProvider(gapPx = PanelGap.roundToPx(), marginPx = PanelEdgeMargin.roundToPx()) }
    }

  fun close() {
    panelState.targetState = false
  }

  Box(modifier = modifier) {
    GlassComponents.GlassNavigationPill(forceGlass = true, shape = CircleShape) {
      IconButton(
        onClick = { panelState.targetState = !panelState.targetState },
        interactionSource = interactionSource,
        modifier =
          Modifier
            .size(ButtonSize)
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
          modifier = Modifier.size(IconSize),
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    // Stay composed until the exit animation has finished, then drop the popup window.
    if (panelState.currentState || panelState.targetState) {
      Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = ::close,
        properties = PopupProperties(focusable = true),
      ) {
        AnimatedVisibility(
          visibleState = panelState,
          enter = panelEnter(reduceMotion),
          exit = panelExit(reduceMotion),
        ) {
          MenuPanel(
            onSettingsClick = {
              close()
              onSettingsClick()
            },
            onLibraryInsightsClick = {
              close()
              onLibraryInsightsClick()
            },
          )
        }
      }
    }
  }
}

/** Fade in, plus a short scale from the bottom-end corner (where the button is) unless motion is reduced. */
@Composable
private fun panelEnter(reduceMotion: Boolean): EnterTransition {
  val fade = AppMotion.spatial(AppMotion.Effect.Alpha, AppMotion.ReducedAlpha)
  if (reduceMotion) return fadeIn(animationSpec = fade)
  val scale = AppMotion.spatial(AppMotion.Spatial.Standard, AppMotion.ReducedAlpha)
  return fadeIn(animationSpec = fade) +
    scaleIn(animationSpec = scale, initialScale = PANEL_ENTER_SCALE, transformOrigin = TransformOrigin(1f, 1f))
}

/** Mirror of [panelEnter], so dismissal is as deliberate as opening. */
@Composable
private fun panelExit(reduceMotion: Boolean): ExitTransition {
  val fade = AppMotion.spatial(AppMotion.Effect.Alpha, AppMotion.ReducedAlpha)
  if (reduceMotion) return fadeOut(animationSpec = fade)
  val scale = AppMotion.spatial(AppMotion.Spatial.Standard, AppMotion.ReducedAlpha)
  return fadeOut(animationSpec = fade) +
    scaleOut(animationSpec = scale, targetScale = PANEL_ENTER_SCALE, transformOrigin = TransformOrigin(1f, 1f))
}

/**
 * The panel surface. A popup is a separate window and cannot sample the app's blurred backdrop, so
 * glass styles use slightly translucent tint rather than a blur.
 */
@Composable
private fun MenuPanel(
  onSettingsClick: () -> Unit,
  onLibraryInsightsClick: () -> Unit,
) {
  val usesGlass = LocalGlassConfig.current.style != SurfaceStyle.CLASSIC
  val container = MaterialTheme.colorScheme.surfaceContainerHigh
  Surface(
    shape = RoundedCornerShape(DesignTokens.Radius.large),
    color = if (usesGlass) container.copy(alpha = GLASS_PANEL_ALPHA) else container,
    shadowElevation = DesignTokens.Elevation.level5,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
  ) {
    Column(
      modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).padding(DesignTokens.Spacing.sm),
      verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
    ) {
      MenuRow(Icons.RoundedFilled.Settings, stringResource(R.string.ui_settings), onSettingsClick)
      MenuRow(Icons.RoundedFilled.Insights, stringResource(R.string.library_insights_title), onLibraryInsightsClick)
    }
  }
}

/** A tappable icon-and-label row with a minimum 48dp touch height. */
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
    Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize), tint = MaterialTheme.colorScheme.primary)
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

/**
 * Places a popup above its anchor, end-aligned with it, and keeps it fully inside the window.
 * If there is no room above, it opens below the anchor instead.
 *
 * @property gapPx Space between the anchor and the popup.
 * @property marginPx Minimum distance kept from every window edge.
 */
internal class AboveAnchorPositionProvider(
  private val gapPx: Int,
  private val marginPx: Int,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val preferredX =
      if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
    val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)

    val above = anchorBounds.top - popupContentSize.height - gapPx
    val preferredY = if (above >= marginPx) above else anchorBounds.bottom + gapPx
    val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)

    return IntOffset(preferredX.coerceIn(marginPx, maxX), preferredY.coerceIn(marginPx, maxY))
  }
}
