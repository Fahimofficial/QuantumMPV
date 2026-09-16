package com.quantummpv.app.ui.browser.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.AppMotion

@Composable
internal fun animatedSelectionColor(selected: Boolean): Color {
  val color by animateColorAsState(
    targetValue = MaterialTheme.colorScheme.tertiary.copy(alpha = if (selected) 0.3f else 0f),
    animationSpec = AppMotion.spatial(AppMotion.Effect.Color, snap()),
    label = "selectionTint",
  )
  return color
}

@Composable
internal fun SelectionIndicator(
  selected: Boolean,
  modifier: Modifier = Modifier,
) {
  val opacity by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = AppMotion.spatial(AppMotion.Effect.Alpha, snap()),
    label = "selectionIndicatorAlpha",
  )
  val scale by animateFloatAsState(
    targetValue = if (selected) 1f else 0.88f,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.ExpressiveFast, snap()),
    label = "selectionIndicatorScale",
  )
  Box(
    modifier =
      modifier.size(24.dp).clearAndSetSemantics { }
        .graphicsLayer {
          alpha = opacity
          scaleX = scale
          scaleY = scale
        }.background(MaterialTheme.colorScheme.tertiary, CircleShape)
        .padding(4.dp),
  ) {
    Icon(Icons.RoundedFilled.Check, null, tint = MaterialTheme.colorScheme.onTertiary)
  }
}