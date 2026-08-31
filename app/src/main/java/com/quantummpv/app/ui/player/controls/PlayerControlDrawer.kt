/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.PlayerButton
import com.quantummpv.app.preferences.getPlayerButtonLabel
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.LocalHidePlayerButtonsBackground
import com.quantummpv.app.ui.theme.controlColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun PlayerControlDrawer(
  buttons: List<PlayerButton>,
  controlsVisible: Boolean,
  onVisibilityChanged: (Boolean) -> Unit,
  renderButton: @Composable (PlayerButton) -> Unit,
) {
  var drawerLayerVisible by remember { mutableStateOf(false) }
  val clickEvent = LocalPlayerButtonsClickEvent.current
  val visibilityChangedCurrent by rememberUpdatedState(onVisibilityChanged)
  val openDrawer = {
    if (controlsVisible && buttons.isNotEmpty() && !drawerLayerVisible) {
      clickEvent()
      drawerLayerVisible = true
    }
  }

  DisposableEffect(drawerLayerVisible) {
    if (drawerLayerVisible) visibilityChangedCurrent(true)
    onDispose {
      if (drawerLayerVisible) visibilityChangedCurrent(false)
    }
  }

  Box(Modifier.fillMaxSize()) {
    if (!drawerLayerVisible) {
      PlayerControlEdgeHandle(
        enabled = controlsVisible && buttons.isNotEmpty(),
        onOpen = openDrawer,
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }

    if (drawerLayerVisible) {
      PlayerControlDrawerLayer(
        buttons = buttons,
        renderButton = renderButton,
        onClosed = { drawerLayerVisible = false },
      )
    }
  }
}

@Composable
private fun PlayerControlEdgeHandle(
  enabled: Boolean,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val thresholdPx = with(density) { EdgePullThreshold.toPx() }
  val maxPullPx = with(density) { EdgePullMaximum.toPx() }
  var pullDistancePx by remember { mutableFloatStateOf(0f) }
  var handleVisible by remember { mutableStateOf(true) }
  var isDragging by remember { mutableStateOf(false) }
  val animatedPull by
    animateFloatAsState(
      targetValue = pullDistancePx,
      animationSpec =
        spring(
          dampingRatio = 0.9f,
          stiffness = 900f,
        ),
      label = "PlayerDrawerEdgePull",
    )
  val pullProgress = (animatedPull / thresholdPx).coerceIn(0f, 1f)
  val handleAlpha by
    animateFloatAsState(
      targetValue = if (enabled && (handleVisible || isDragging || isHovered)) 1f else 0f,
      animationSpec = tween(durationMillis = 220),
      label = "PlayerDrawerHandleAlpha",
    )

  LaunchedEffect(enabled, isHovered, isDragging) {
    if (!enabled) {
      handleVisible = false
      return@LaunchedEffect
    }
    handleVisible = true
    if (!isHovered && !isDragging) {
      delay(HandleAutoHideDelayMs)
      handleVisible = false
    }
  }

  Box(
    modifier =
      modifier
        .width(EdgeTouchWidth)
        .fillMaxHeight(EdgeTouchHeightFraction)
        .hoverable(interactionSource = interactionSource, enabled = enabled)
        .pointerInput(enabled, thresholdPx, maxPullPx) {
          if (!enabled) return@pointerInput
          var thresholdReached = false
          detectHorizontalDragGestures(
            onDragStart = {
              isDragging = true
              thresholdReached = false
              pullDistancePx = 0f
            },
            onDragCancel = {
              isDragging = false
              pullDistancePx = 0f
            },
            onDragEnd = {
              isDragging = false
              pullDistancePx = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
              val directedDelta = -dragAmount
              if (directedDelta > 0f || pullDistancePx > 0f) {
                change.consume()
                val resistance =
                  if (pullDistancePx >= thresholdPx && directedDelta > 0f) {
                    0.24f
                  } else {
                    1f
                  }
                pullDistancePx =
                  (pullDistancePx + directedDelta * resistance).coerceIn(0f, maxPullPx)
                if (!thresholdReached && pullDistancePx >= thresholdPx) {
                  thresholdReached = true
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onOpen()
                } else if (thresholdReached && pullDistancePx < thresholdPx * 0.82f) {
                  thresholdReached = false
                }
              }
            },
          )
        }.clickable(
          enabled = enabled,
          interactionSource = interactionSource,
          indication = null,
          onClick = onOpen,
        ),
    contentAlignment = Alignment.CenterEnd,
  ) {
    Icon(
      imageVector = Icons.RoundedFilled.ChevronLeft,
      contentDescription = stringResource(R.string.player_sheets_more_title),
      tint = controlColor,
      modifier =
        Modifier
          .padding(end = 4.dp)
          .size(28.dp)
          .graphicsLayer {
            alpha = handleAlpha
            translationX = -animatedPull * 0.72f
            scaleX = 1f + pullProgress * 0.16f
            scaleY = 1f + pullProgress * 0.16f
          },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControlDrawerLayer(
  buttons: List<PlayerButton>,
  renderButton: @Composable (PlayerButton) -> Unit,
  onClosed: () -> Unit,
) {
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val parentClickEvent = LocalPlayerButtonsClickEvent.current
  val parentClickEventCurrent by rememberUpdatedState(parentClickEvent)
  val onClosedCurrent by rememberUpdatedState(onClosed)
  val drawerWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.88f).coerceAtMost(420.dp)

  LaunchedEffect(Unit) {
    drawerState.open()
    snapshotFlow { drawerState.isClosed && !drawerState.isAnimationRunning }
      .filter { it }
      .first()
    onClosedCurrent()
  }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = true,
      scrimColor = Color.Black.copy(alpha = 0.32f),
      drawerContent = {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          ModalDrawerSheet(
            drawerState = drawerState,
            modifier =
              Modifier
                .width(drawerWidth)
                .fillMaxHeight(),
            drawerShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawerContentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = WindowInsets.safeDrawing,
          ) {
            PlayerControlDrawerContent(
              buttons = buttons,
              renderButton = renderButton,
              onDismissRequest = { scope.launch { drawerState.close() } },
              parentClickEvent = { parentClickEventCurrent() },
            )
          }
        }
      },
    ) {
      Box(Modifier.fillMaxSize())
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerControlDrawerContent(
  buttons: List<PlayerButton>,
  renderButton: @Composable (PlayerButton) -> Unit,
  onDismissRequest: () -> Unit,
  parentClickEvent: () -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = 8.dp, bottom = 4.dp),
    ) {
      Text(
        text = stringResource(R.string.player_sheets_more_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.align(Alignment.CenterStart),
      )
      IconButton(
        onClick = onDismissRequest,
        modifier = Modifier.align(Alignment.CenterEnd),
      ) {
        Icon(
          imageVector = Icons.RoundedFilled.Close,
          contentDescription = stringResource(R.string.generic_cancel),
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    CompositionLocalProvider(
      LocalHidePlayerButtonsBackground provides false,
      LocalPlayerButtonsClickEvent provides {
        onDismissRequest()
        parentClickEvent()
      },
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp),
      ) {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalArrangement = Arrangement.spacedBy(12.dp),
          maxItemsInEachRow = 3,
        ) {
          buttons.forEach { button ->
            Column(
              modifier = Modifier.width(88.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
              ) {
                renderButton(button)
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
        Spacer(Modifier.size(8.dp))
      }
    }
  }
}

private val EdgeTouchWidth = 48.dp
private val EdgePullThreshold = 64.dp
private val EdgePullMaximum = 92.dp
private const val EdgeTouchHeightFraction = 0.34f
private const val HandleAutoHideDelayMs = 1_800L