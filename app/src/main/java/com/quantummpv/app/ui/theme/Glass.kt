package com.quantummpv.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Shared state for the optional app-wide Liquid Glass appearance. */
val LocalGlassUi = staticCompositionLocalOf { false }

/** Paints soft theme-tinted highlights behind translucent glass surfaces. */
@Composable
fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
  val enabled = LocalGlassUi.current
  val primary = MaterialTheme.colorScheme.primary
  val tertiary = MaterialTheme.colorScheme.tertiary
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .then(if (enabled) Modifier.glassBackdrop(primary, tertiary) else Modifier),
    content = content,
  )
}

private fun Modifier.glassBackdrop(primary: Color, tertiary: Color): Modifier =
  drawBehind {
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.14f), Color.Transparent),
        center = center.copy(x = size.width * 0.16f, y = size.height * 0.12f),
        radius = size.maxDimension * 0.84f,
      ),
    )
    drawRect(
      brush = Brush.radialGradient(
        colors = listOf(tertiary.copy(alpha = 0.10f), Color.Transparent),
        center = center.copy(x = size.width * 0.86f, y = size.height * 0.80f),
        radius = size.maxDimension * 0.72f,
      ),
    )
  }
