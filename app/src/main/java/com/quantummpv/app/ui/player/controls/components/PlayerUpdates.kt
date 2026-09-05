/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.quantummpv.app.ui.player.PlayerActivity
import com.quantummpv.app.ui.player.PlayerViewModel

private val tabularFigures = "tnum"

@Composable
private fun rememberPlayerUpdateOffset(): androidx.compose.ui.unit.Dp {
  val activity = LocalActivity.current as? PlayerActivity
  val playerViewModel =
    remember(activity) {
      activity?.let { ViewModelProvider(it)[PlayerViewModel::class.java] }
    }

  val controlsShown = playerViewModel?.controlsShown?.collectAsState()?.value ?: false
  val controlsLocked = playerViewModel?.areControlsLocked?.collectAsState()?.value ?: false
  val brightnessSliderShown = playerViewModel?.isBrightnessSliderShown?.collectAsState()?.value ?: false
  val volumeSliderShown = playerViewModel?.isVolumeSliderShown?.collectAsState()?.value ?: false
  val areButtonsVisible = controlsShown && !controlsLocked && !brightnessSliderShown && !volumeSliderShown
  val isPortrait = LocalConfiguration.current.orientation == ORIENTATION_PORTRAIT

  // Keep mpvRx's current visible-control placement intact (104dp portrait / 64dp landscape).
  // mpvRex moves its OSD toward the top when controls disappear. Applying that movement as an
  // internal offset avoids touching the carefully tuned PlayerControls constraints and padding.
  val targetOffset =
    if (areButtonsVisible) {
      0.dp
    } else if (isPortrait) {
      (-40).dp
    } else {
      (-32).dp
    }

  return animateDpAsState(
    targetValue = targetOffset,
    animationSpec = spring(),
    label = "player_update_controls_offset",
  ).value
}

@Composable
fun PlayerUpdate(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = {},
) {
  val controlsOffset = rememberPlayerUpdateOffset()

  Surface(
    shape = RoundedCornerShape(100.dp),
    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border =
      BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
      ),
    modifier =
      modifier
        .offset(y = controlsOffset)
        .animateContentSize(),
  ) {
    Box(
      modifier = Modifier.padding(vertical = 4.dp, horizontal = 10.dp),
      contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }
}

@Composable
fun TextPlayerUpdate(
  text: String,
  modifier: Modifier = Modifier,
) {
  val stableTextStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = tabularFigures)
  PlayerUpdate(modifier) {
    Text(
      text = text,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      style = stableTextStyle,
    )
  }
}

@Composable
fun MultipleSpeedPlayerUpdate(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  CompactSpeedIndicator(currentSpeed = currentSpeed, modifier = modifier)
}

@Composable
fun CompactSpeedIndicator(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  val speedString = remember(currentSpeed) { currentSpeed.formatSpeed() }

  PlayerUpdate(modifier) {
    AnimatedContent(
      targetState = speedString,
      transitionSpec = {
        (fadeIn(animationSpec = tween(100)) +
          scaleIn(
            initialScale = 0.85f,
            animationSpec =
              spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
              ),
          ) +
          slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec =
              spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
              ),
          )).togetherWith(
          fadeOut(animationSpec = tween(100)) +
            scaleOut(targetScale = 1.1f, animationSpec = tween(100)) +
            slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = tween(100)),
        ).using(SizeTransform(clip = false))
      },
      label = "SpeedJumpAnimation",
    ) { targetSpeed ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = targetSpeed,
          fontWeight = FontWeight.ExtraBold,
          style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = tabularFigures),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "x",
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(start = 1.dp),
          style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = tabularFigures),
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
      }
    }
  }
}

@Composable
@Preview
private fun PreviewMultipleSpeedPlayerUpdate() {
  MultipleSpeedPlayerUpdate(currentSpeed = 2f)
}

private fun Float.formatSpeed(): String =
  if (this % 1.0f == 0.0f) {
    this.toInt().toString()
  } else {
    String.format("%.1f", this)
  }

@Composable
fun SeekPlayerUpdate(
  currentTime: String,
  seekDelta: String,
  modifier: Modifier = Modifier,
) {
  val stableTextStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = tabularFigures)
  PlayerUpdate(modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = currentTime,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        style = stableTextStyle,
      )

      Text(
        text = " $seekDelta",
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        style = stableTextStyle,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
      )
    }
  }
}
