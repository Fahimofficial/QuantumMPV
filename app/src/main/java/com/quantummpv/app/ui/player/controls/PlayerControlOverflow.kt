/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quantummpv.app.R
import com.quantummpv.app.preferences.PlayerButton
import com.quantummpv.app.preferences.getPlayerButtonLabel
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.LocalHidePlayerButtonsBackground
import com.quantummpv.app.ui.player.controls.components.playerButtonBorderColor
import com.quantummpv.app.ui.player.controls.components.playerButtonContainerColor
import com.quantummpv.app.ui.player.controls.components.playerButtonContentColor
import com.quantummpv.app.ui.theme.controlColor
import com.quantummpv.app.ui.theme.spacing

internal enum class OverflowPullDirection(
  val multiplier: Float,
) {
  LEFT(-1f),
  RIGHT(1f),
}

@Composable
internal fun PlayerControlOverflowGroup(
  overflowButtons: List<PlayerButton>,
  pullDirection: OverflowPullDirection,
  handleAtStart: Boolean,
  onOverflowVisibilityChanged: (Boolean) -> Unit,
  renderButton: @Composable (PlayerButton, Boolean) -> Unit,
  content: @Composable RowScope.() -> Unit,
) {
  var showOverflow by remember { mutableStateOf(false) }
  val clickEvent = LocalPlayerButtonsClickEvent.current
  val onVisibilityChangedCurrent by rememberUpdatedState(onOverflowVisibilityChanged)
  val openOverflow = {
    clickEvent()
    showOverflow = true
  }

  DisposableEffect(showOverflow) {
    if (showOverflow) onVisibilityChangedCurrent(true)
    onDispose {
      if (showOverflow) onVisibilityChangedCurrent(false)
    }
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    if (handleAtStart && overflowButtons.isNotEmpty()) {
      OverflowPullHandle(
        direction = pullDirection,
        onOpen = openOverflow,
      )
    }
    content()
    if (!handleAtStart && overflowButtons.isNotEmpty()) {
      OverflowPullHandle(
        direction = pullDirection,
        onOpen = openOverflow,
      )
    }
  }

  if (showOverflow) {
    PlayerControlOverflowDialog(
      buttons = overflowButtons,
      renderButton = renderButton,
      onDismissRequest = { showOverflow = false },
    )
  }
}

@Composable
private fun OverflowPullHandle(
  direction: OverflowPullDirection,
  onOpen: () -> Unit,
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  val hideBackground = LocalHidePlayerButtonsBackground.current
  val onOpenCurrent by rememberUpdatedState(onOpen)
  val thresholdPx = with(density) { PullThreshold.toPx() }
  var pullDistancePx by remember { mutableFloatStateOf(0f) }
  var isArmed by remember { mutableStateOf(false) }
  val targetProgress = (pullDistancePx / thresholdPx).coerceIn(0f, 1f)
  val progress by
    animateFloatAsState(
      targetValue = targetProgress,
      animationSpec = spring(dampingRatio = 0.82f, stiffness = 650f),
      label = "PlayerOverflowPullProgress",
    )
  val interactionSource = remember { MutableInteractionSource() }
  val containerColor =
    if (hideBackground) {
      Color.Transparent
    } else {
      lerp(
        playerButtonContainerColor(),
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
        progress,
      )
    }
  val contentColor =
    if (hideBackground) {
      if (isArmed) MaterialTheme.colorScheme.primary else controlColor
    } else {
      lerp(playerButtonContentColor(), MaterialTheme.colorScheme.onPrimaryContainer, progress)
    }

  Surface(
    modifier =
      Modifier
        .width(PullHandleWidth)
        .height(45.dp)
        .clip(CircleShape)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(),
          onClick = onOpen,
        ).pointerInput(direction, thresholdPx) {
          var thresholdReached = false
          detectDragGesturesAfterLongPress(
            onDragStart = {
              isArmed = true
              thresholdReached = false
              pullDistancePx = 0f
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDragCancel = {
              isArmed = false
              pullDistancePx = 0f
            },
            onDragEnd = {
              val shouldOpen = pullDistancePx >= thresholdPx * OpenThresholdProgress
              isArmed = false
              pullDistancePx = 0f
              if (shouldOpen) onOpenCurrent()
            },
            onDrag = { change, dragAmount ->
              val directedDelta = dragAmount.x * direction.multiplier
              if (directedDelta > 0f || pullDistancePx > 0f) {
                change.consume()
                pullDistancePx =
                  (pullDistancePx + directedDelta).coerceIn(
                    minimumValue = 0f,
                    maximumValue = thresholdPx * 1.2f,
                  )
                val isThresholdReached = pullDistancePx >= thresholdPx
                if (thresholdReached != isThresholdReached) {
                  thresholdReached = isThresholdReached
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
              }
            },
          )
        },
    shape = CircleShape,
    color = containerColor,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border =
      if (hideBackground) {
        null
      } else {
        BorderStroke(
          1.dp,
          lerp(playerButtonBorderColor(), MaterialTheme.colorScheme.primary, progress),
        )
      },
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier
            .align(
              if (direction == OverflowPullDirection.LEFT) {
                Alignment.CenterEnd
              } else {
                Alignment.CenterStart
              },
            ).fillMaxHeight()
            .fillMaxWidth(progress.coerceAtLeast(0.08f))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
      )
      Icon(
        imageVector =
          if (direction == OverflowPullDirection.LEFT) {
            Icons.RoundedFilled.ChevronLeft
          } else {
            Icons.RoundedFilled.ChevronRight
          },
        contentDescription = stringResource(R.string.player_sheets_more_title),
        tint = contentColor,
        modifier =
          Modifier
            .size(20.dp)
            .graphicsLayer {
              translationX = direction.multiplier * progress * with(density) { 4.dp.toPx() }
              scaleX = 1f + progress * 0.18f
              scaleY = 1f + progress * 0.18f
            },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerControlOverflowDialog(
  buttons: List<PlayerButton>,
  renderButton: @Composable (PlayerButton, Boolean) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val parentClickEvent = LocalPlayerButtonsClickEvent.current
  val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
  val visibleState =
    remember {
      MutableTransitionState(false).apply { targetState = true }
    }

  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      AnimatedVisibility(
        visibleState = visibleState,
        enter =
          fadeIn(animationSpec = spring(stiffness = 500f)) +
            scaleIn(
              initialScale = 0.86f,
              animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
            ),
      ) {
        Surface(
          modifier =
            Modifier
              .fillMaxWidth(0.92f)
              .widthIn(max = 560.dp)
              .heightIn(max = maxHeight),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
          tonalElevation = 6.dp,
          shadowElevation = 12.dp,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier =
              Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = stringResource(R.string.player_sheets_more_title),
                style = MaterialTheme.typography.titleMedium,
              )
              IconButton(onClick = onDismissRequest) {
                Icon(
                  imageVector = Icons.RoundedFilled.Close,
                  contentDescription = stringResource(R.string.generic_cancel),
                )
              }
            }
            HorizontalDivider(
              modifier = Modifier.padding(bottom = 12.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
            CompositionLocalProvider(
              LocalHidePlayerButtonsBackground provides false,
              LocalPlayerButtonsClickEvent provides {
                onDismissRequest()
                parentClickEvent()
              },
            ) {
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 7,
              ) {
                buttons.forEach { button ->
                  Column(
                    modifier = Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Box(
                      modifier = Modifier.size(48.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      renderButton(button, true)
                    }
                    Text(
                      text = getPlayerButtonLabel(button),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      textAlign = TextAlign.Center,
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private val PullHandleWidth = 30.dp
private val PullThreshold = 72.dp
private const val OpenThresholdProgress = 0.95f
