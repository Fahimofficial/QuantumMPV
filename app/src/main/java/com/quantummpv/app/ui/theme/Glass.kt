/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.quantummpv.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared state for the optional app-wide Liquid Glass appearance. */
val LocalGlassUi = staticCompositionLocalOf { false }
val LocalGlassStrength = staticCompositionLocalOf { 0.55f }
val LocalReduceTransparency = staticCompositionLocalOf { false }

/** Paints soft theme-tinted highlights behind translucent glass surfaces. */
@Composable
fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
  val enabled = LocalGlassUi.current
  val strength = LocalGlassStrength.current
  val reduceTransparency = LocalReduceTransparency.current
  val primary = MaterialTheme.colorScheme.primary
  val tertiary = MaterialTheme.colorScheme.tertiary
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .then(
          if (enabled) Modifier.glassBackdrop(primary, tertiary, strength, reduceTransparency)
          else Modifier,
        ),
    content = content,
  )
}

@Composable
fun glassContainerColor(base: Color): Color {
  val strength = LocalGlassStrength.current
  val reduceTransparency = LocalReduceTransparency.current
  val alpha = if (reduceTransparency) 0.90f else 0.58f + strength.coerceIn(0f, 1f) * 0.22f
  return base.copy(alpha = alpha)
}

@Composable
fun glassBorderColor(): Color {
  val strength = LocalGlassStrength.current
  val reduceTransparency = LocalReduceTransparency.current
  val alpha = if (reduceTransparency) 0.60f else 0.28f + strength.coerceIn(0f, 1f) * 0.28f
  return MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
}

@Composable
fun LiquidGlassPreview(modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(22.dp)
  val primary = MaterialTheme.colorScheme.primary
  val tertiary = MaterialTheme.colorScheme.tertiary
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
          Brush.linearGradient(
            listOf(
              primary.copy(alpha = 0.24f),
              MaterialTheme.colorScheme.surfaceContainerLow,
              tertiary.copy(alpha = 0.18f),
            ),
          ),
        )
        .border(1.dp, glassBorderColor(), shape)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text("Liquid Glass preview", style = MaterialTheme.typography.titleMedium)
    Text(
      "Soft color bloom, translucent surfaces, and readable borders. Video content stays sharp.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Surface(
        modifier = Modifier.weight(1f).height(54.dp),
        shape = RoundedCornerShape(16.dp),
        color = glassContainerColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, glassBorderColor()),
      ) { Box(Modifier.padding(12.dp)) { Text("Glass card") } }
      Surface(
        modifier = Modifier.weight(1f).height(54.dp),
        shape = RoundedCornerShape(16.dp),
        color = primary.copy(alpha = 0.30f),
        border = androidx.compose.foundation.BorderStroke(1.dp, primary.copy(alpha = 0.52f)),
      ) { Box(Modifier.padding(12.dp)) { Text("Focused control") } }
    }
  }
}

private fun Modifier.glassBackdrop(
  primary: Color,
  tertiary: Color,
  strength: Float,
  reduceTransparency: Boolean,
): Modifier {
  val multiplier = if (reduceTransparency) 0.55f else 1f
  return drawBehind {
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.24f * strength * multiplier), Color.Transparent),
        center = center.copy(x = size.width * 0.16f, y = size.height * 0.12f),
        radius = size.maxDimension * 0.84f,
      ),
    )
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(tertiary.copy(alpha = 0.18f * strength * multiplier), Color.Transparent),
        center = center.copy(x = size.width * 0.86f, y = size.height * 0.80f),
        radius = size.maxDimension * 0.72f,
      ),
    )
  }
}
