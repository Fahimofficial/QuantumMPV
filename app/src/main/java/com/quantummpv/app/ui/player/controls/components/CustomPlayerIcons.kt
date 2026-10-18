/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components

import android.graphics.Typeface
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.theme.AppMotion
import android.graphics.Paint as AndroidPaint

/**
 * Play/Pause glyph that morphs between the two shapes with [R.drawable.avd_play_to_pause] and
 * [R.drawable.avd_pause_to_play].
 *
 * Each direction plays its own AVD from the start: [key] gives every [isPlaying] value a fresh
 * painter, which starts at the AVD's first frame and then runs to its end. The first composition
 * starts at the end instead, so opening a screen shows the current glyph without animating.
 */
@Composable
fun AnimatedPlayPauseIcon(
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
  contentDescription: String? = null,
) {
  val animateChanges = remember { AnimateAfterFirstFrame() }
  val disableMotion = AppMotion.shouldDisableAnimations()
  key(isPlaying) {
    val image =
      AnimatedImageVector.animatedVectorResource(
        if (isPlaying) R.drawable.avd_play_to_pause else R.drawable.avd_pause_to_play,
      )
    var atEnd by remember { mutableStateOf(!animateChanges.enabled || disableMotion) }
    LaunchedEffect(Unit) {
      atEnd = true
      animateChanges.enabled = true
    }
    Icon(
      painter = rememberAnimatedVectorPainter(image, atEnd),
      contentDescription = contentDescription,
      modifier = modifier,
      tint = tint,
    )
  }
}

/** Plain holder: flipping it must not trigger a recomposition. */
private class AnimateAfterFirstFrame {
  var enabled: Boolean = false
}

@Composable
fun AbLoopIcon(
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
  isASet: Boolean = true,
  isBSet: Boolean = true,
  textA: String = "A",
  textB: String = "B",
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(1.dp),
    ) {
      val iconScale = size.minDimension
      if (iconScale <= 0f) return@Canvas

      fun tintWith(multiplier: Float): Color = tint.copy(alpha = tint.alpha * multiplier)

      val aColor = tint
      val bColor = tint
      val labelAColor = tint
      val labelBColor = tint
      val shadingColor = tintWith(if (isASet && isBSet) 0.88f else 0.62f)

      val circleDiameter = iconScale * 0.54f
      val circleRadius = circleDiameter / 2f
      val circleSeparation = iconScale * 0.36f
      val centerX = size.width / 2f
      val centerY = size.height / 2f
      val circleA = Offset(centerX - (circleSeparation / 2f), centerY)
      val circleB = Offset(centerX + (circleSeparation / 2f), centerY)

      val outlineWidth = iconScale * 0.065f
      val shadingStroke = iconScale * 0.018f
      val shadingGap = iconScale * 0.03f
      val textSize = iconScale * 0.22f
      val labelHorizontalOffset = circleRadius * 0.20f
      val labelVerticalOffset = circleRadius * 0.04f

      val pathA =
        Path().apply {
          addOval(
            Rect(
              left = circleA.x - circleRadius,
              top = circleA.y - circleRadius,
              right = circleA.x + circleRadius,
              bottom = circleA.y + circleRadius,
            ),
          )
        }
      val pathB =
        Path().apply {
          addOval(
            Rect(
              left = circleB.x - circleRadius,
              top = circleB.y - circleRadius,
              right = circleB.x + circleRadius,
              bottom = circleB.y + circleRadius,
            ),
          )
        }
      val intersectionPath =
        Path().apply {
          op(pathA, pathB, PathOperation.Intersect)
        }

      clipPath(intersectionPath) {
        var startX = -size.height
        while (startX < size.width + size.height) {
          drawLine(
            color = shadingColor,
            start = Offset(startX, 0f),
            end = Offset(startX + size.height, size.height),
            strokeWidth = shadingStroke,
            cap = StrokeCap.Round,
          )
          startX += shadingStroke + shadingGap
        }
      }

      drawCircle(
        color = aColor,
        center = circleA,
        radius = circleRadius,
        style = Stroke(width = outlineWidth),
      )
      drawCircle(
        color = bColor,
        center = circleB,
        radius = circleRadius,
        style = Stroke(width = outlineWidth),
      )

      drawIntoCanvas { canvas ->
        fun drawLabel(
          text: String,
          center: Offset,
          color: Color,
          horizontalOffset: Float,
        ) {
          val paint =
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
              this.color = color.toArgb()
              this.textSize = textSize
              typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
              textAlign = AndroidPaint.Align.CENTER
            }
          val fontMetrics = paint.fontMetrics
          val labelCenter = Offset(center.x + horizontalOffset, center.y - labelVerticalOffset)
          val labelBaseline = labelCenter.y - ((fontMetrics.ascent + fontMetrics.descent) / 2f)
          canvas.nativeCanvas.drawText(text, labelCenter.x, labelBaseline, paint)
        }

        drawLabel(textA, circleA, labelAColor, -labelHorizontalOffset)
        drawLabel(textB, circleB, labelBColor, labelHorizontalOffset)
      }
    }
  }
}
