/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.states

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.theme.Dimens
import com.quantummpv.app.ui.theme.LocalReduceMotion

@Composable
fun EmptyState(
  icon: AppIcon,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  // One-shot settle-in on first appearance; nothing loops.
  val reduceMotion = LocalReduceMotion.current
  val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
  val spec = AppMotion.defaultSpatial<Float>()
  LaunchedEffect(Unit) { entrance.animateTo(1f, spec) }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 48.dp)
          .padding(bottom = 80.dp),
      // Account for bottom navigation bar
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      EmptyStateIllustration(
        icon = icon,
        modifier =
          Modifier.graphicsLayer {
            val p = entrance.value
            alpha = p.coerceIn(0f, 1f)
            val scale = 0.9f + 0.1f * p
            scaleX = scale
            scaleY = scale
          },
      )

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
      )

      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(20.dp))
        FilledTonalButton(
          onClick = onAction,
          modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

/**
 * Small vector illustration: two offset tonal "cards" behind a primary-container tile carrying the
 * icon. Drawn with theme roles only, so it follows dynamic color and dark/light themes.
 */
@Composable
fun EmptyStateIllustration(
  icon: AppIcon,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val back = colors.tertiaryContainer
  val middle = colors.secondaryContainer
  val accent = colors.primary
  Box(
    modifier =
      modifier
        .size(width = 140.dp, height = 112.dp)
        .drawBehind {
          val tile = Size(size.width * 0.56f, size.height * 0.7f)
          val radius = CornerRadius(tile.minDimension * 0.28f)
          rotateTile(back, tile, radius, Offset(size.width * 0.06f, size.height * 0.2f), -10f)
          rotateTile(middle, tile, radius, Offset(size.width * 0.38f, size.height * 0.1f), 8f)
          // Accent dots give the composition some life without motion.
          drawCircle(accent.copy(alpha = 0.5f), radius = size.minDimension * 0.045f, center = Offset(size.width * 0.12f, size.height * 0.12f))
          drawCircle(accent.copy(alpha = 0.35f), radius = size.minDimension * 0.03f, center = Offset(size.width * 0.92f, size.height * 0.82f))
        },
      contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(72.dp)
          .drawBehind {
            drawRoundRect(colors.primaryContainer, cornerRadius = CornerRadius(size.minDimension * 0.32f))
          },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(36.dp),
        tint = colors.onPrimaryContainer,
      )
    }
  }
}

private fun DrawScope.rotateTile(
  color: Color,
  tile: Size,
  radius: CornerRadius,
  topLeft: Offset,
  degrees: Float,
) {
  val pivot = Offset(topLeft.x + tile.width / 2f, topLeft.y + tile.height / 2f)
  rotate(degrees, pivot) {
    drawRoundRect(color, topLeft = topLeft, size = tile, cornerRadius = radius)
  }
}
