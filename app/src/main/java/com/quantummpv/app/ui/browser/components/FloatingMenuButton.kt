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
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.GlassComponents
import com.quantummpv.app.ui.theme.spacing

private const val PRESSED_SCALE = 0.92f
private const val OPEN_ROTATION_DEGREES = 90f

// The panel grows out of the overflow button: it starts small, scaled around the button's centre,
// and becomes opaque during the first part of the motion so it never looks like a ghost.
private const val PANEL_ENTER_SCALE = 0.5f

// Opaque within the first frames: a shadow is drawn only outside its caster's outline, so any
// lasting translucency would reveal the shadow's empty interior as a light rectangle.
private const val PANEL_OPAQUE_AT = 0.15f
private val PanelShadowElevation = 12.dp

/** Fallback pivot (panel bottom-end) until the button's position has been measured. */
private val PanelTransformOrigin = TransformOrigin(1f, 1f)

internal val FloatingMenuButtonSize = 48.dp
private val MenuIconSize = 24.dp
private val MinRowHeight = 48.dp

// Concentric corners: tile radius = panel radius - panel padding, so the tile curves run parallel
// to the panel's outer curves instead of meeting them at a visibly different angle.
private val PanelCornerRadius = 20.dp
private val PanelPadding = 8.dp
private val PanelShape = RoundedCornerShape(PanelCornerRadius)
private val TileShape = RoundedCornerShape(PanelCornerRadius - PanelPadding)
private const val TILE_TINT_ALPHA = 0.06f

/** A compact overflow trigger embedded at the end of the navigation pill. */
@Composable
fun FloatingMenuButton(
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  onCenterPositioned: (Offset) -> Unit = {},
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
  // Same spec as the panel's progress, so the icon turns exactly while the panel grows/shrinks.
  val rotation by animateFloatAsState(
    targetValue = if (expanded && !reduceMotion) OPEN_ROTATION_DEGREES else 0f,
    animationSpec = AppMotion.spatial(PanelMotionSpec, AppMotion.ReducedAlpha),
    label = "floating_menu_rotation",
  )
  val tint by animateColorAsState(
    targetValue = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    animationSpec = AppMotion.spatial(AppMotion.Effect.Color, AppMotion.Effect.Color),
    label = "floating_menu_tint",
  )

  IconButton(
    onClick = { onExpandedChange(!expanded) },
    interactionSource = interactionSource,
    modifier =
      modifier
        .size(FloatingMenuButtonSize)
        .onGloballyPositioned { onCenterPositioned(it.boundsInRoot().center) }
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
      tint = tint,
    )
  }
}

/**
 * In-window menu anchored above the dock. Keeping it in the main composition lets the shared Haze
 * backdrop produce real glass in glass mode, while Classic gets the same solid surface as the dock.
 * The caller bounds its height; when a short window leaves less room than both rows need, the rows
 * scroll inside the panel instead of being clipped.
 */
@Composable
fun FloatingGlassMenuPanel(
  visible: Boolean,
  onSettingsClick: () -> Unit,
  onLibraryInsightsClick: () -> Unit,
  modifier: Modifier = Modifier,
  glass: Boolean = false,
  anchorCenterInRoot: () -> Offset? = { null },
) {
  val reduceMotion = AppMotion.shouldReduceMotion()
  var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  // Root cause of the late/abrupt shadow: the previous enter/exit ran on AnimatedVisibility's own
  // layer. expandVertically clipped that layer to a growing rectangle, and an animated alpha < 1
  // renders a layer offscreen, clipped to its bounds. Both cut the Surface's elevation shadow
  // (which lies outside the bounds) and squared the panel's rounded corners until the animation
  // ended, so the full shadow popped in at the end and vanished first on close.
  // Fix: one progress value drives alpha + scale on a single layer that composites with
  // ModulateAlpha (no offscreen buffer), so the native shadow fades and scales with the surface.
  AnimatedVisibility(
    visible = visible,
    enter = EnterTransition.None,
    exit = ExitTransition.None,
    modifier = modifier,
  ) {
    // Critically damped: settles without overshoot, and reverses smoothly when tapped mid-flight.
    val progressSpec = AppMotion.spatial(PanelMotionSpec, AppMotion.ReducedAlpha)
    // Rows ignore taps once dismissal starts, so a fast second tap can't navigate twice.
    val interactive = transition.targetState == EnterExitState.Visible
    val progress by transition.animateFloat(
      transitionSpec = { progressSpec },
      label = "floating_menu_progress",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val scaleWithProgress = !reduceMotion
    // Follows the dock: solid in Classic, glass only when the dock itself is glass.
    val usesClassicShadow = !glass && !LocalGlassConfig.current.usesGlassStyle()
    val shadowPx = with(LocalDensity.current) { PanelShadowElevation.toPx() }
    GlassComponents.GlassFloatingMenuSurface(
      shape = PanelShape,
      forceGlass = glass,
      classicShadowElevation = 0.dp,
      modifier =
        Modifier
          .fillMaxWidth()
          // Measured outside the animated layer, i.e. the panel's resting bounds.
          .onGloballyPositioned { panelCoordinates = it }
          .graphicsLayer {
            val fraction = progress.coerceIn(0f, 1f)
            alpha = (fraction / PANEL_OPAQUE_AT).coerceIn(0f, 1f)
            compositingStrategy = CompositingStrategy.ModulateAlpha
            // The shadow lives on this same layer and deepens with the motion, so it develops with
            // the surface (and recedes with it on close) instead of appearing at full strength.
            if (usesClassicShadow) {
              shadowElevation = shadowPx * fraction
              shape = PanelShape
            }
            if (scaleWithProgress) {
              val scale = PANEL_ENTER_SCALE + (1f - PANEL_ENTER_SCALE) * fraction
              scaleX = scale
              scaleY = scale
              transformOrigin =
                panelTransformOriginFor(panelCoordinates, anchorCenterInRoot()) ?: PanelTransformOrigin
            }
          },
    ) {
      Column(
        modifier =
          Modifier
            // ModulateAlpha on the surface multiplies each draw by alpha but applies it to child
            // layers (text, clips) individually, which showed as flat rectangles mid-fade. The
            // content casts no shadow, so compositing it as one offscreen layer is safe and exact.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .verticalScroll(rememberScrollState())
            .padding(PanelPadding),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
      ) {
        MenuRow(
          icon = Icons.RoundedFilled.Settings,
          label = stringResource(R.string.ui_settings),
          enabled = interactive,
          onClick = onSettingsClick,
        )
        MenuRow(
          icon = Icons.RoundedFilled.Insights,
          label = stringResource(R.string.library_insights_title),
          enabled = interactive,
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
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val shape = TileShape
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = MinRowHeight)
        .tvFocusHighlight(shape, focusedScale = 1.02f)
        .clip(shape)
        // Subtle tonal tile so the rounded tile shape reads at rest, not only under the ripple.
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TILE_TINT_ALPHA))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.smaller),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(MenuIconSize), tint = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}

/** Shared by the panel's progress and the button's rotation so both move as one. */
private val PanelMotionSpec = AppMotion.Spatial.Standard

/**
 * Pivot that makes the panel scale toward/away from the overflow button. Expressed as a fraction
 * of the panel size, so it may lie outside 0..1 (the button sits below the panel).
 */
internal fun panelTransformOriginFor(
  panel: LayoutCoordinates?,
  anchorCenterInRoot: Offset?,
): TransformOrigin? {
  if (panel == null || anchorCenterInRoot == null || !panel.isAttached) return null
  val width = panel.size.width
  val height = panel.size.height
  if (width <= 0 || height <= 0) return null
  return panelTransformOrigin(panel.positionInRoot(), width.toFloat(), height.toFloat(), anchorCenterInRoot)
}

/** Pure form of [panelTransformOriginFor], kept separate for unit tests. */
internal fun panelTransformOrigin(
  panelTopLeftInRoot: Offset,
  panelWidth: Float,
  panelHeight: Float,
  anchorCenterInRoot: Offset,
): TransformOrigin =
  TransformOrigin(
    pivotFractionX = (anchorCenterInRoot.x - panelTopLeftInRoot.x) / panelWidth,
    pivotFractionY = (anchorCenterInRoot.y - panelTopLeftInRoot.y) / panelHeight,
  )

/** Same rule GlassContainer uses: Classic/Minimal draw a plain Surface, everything else is glass. */
private fun com.quantummpv.app.ui.theme.DesignTokens.GlassConfig.usesGlassStyle(): Boolean =
  style != com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle.CLASSIC &&
    style != com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle.MINIMAL
