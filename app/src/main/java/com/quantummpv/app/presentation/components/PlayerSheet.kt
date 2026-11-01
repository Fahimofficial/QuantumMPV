/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("DEPRECATION")

package com.quantummpv.app.presentation.components

import android.annotation.SuppressLint
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.player.controls.components.tvFocusGroup
import com.quantummpv.app.ui.theme.AppMotion
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val sheetAnimationSpec = AppMotion.Spatial.Standard

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun PlayerSheet(
  onDismissRequest: () -> Unit,
  title: String? = null,
  modifier: Modifier = Modifier,
  tonalElevation: Dp = 1.dp,
  customMaxWidth: Dp? = null,
  customMaxHeight: Dp? = null,
  surfaceColor: Color? = null,
  isSwipeActive: Boolean = false,
  swipeOffset: Float = 0f,
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
  val maxWidth =
    customMaxWidth
      ?: if (LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE) {
        640.dp
      } else {
        420.dp
      }
  val isImeVisible = WindowInsets.ime.getBottom(density) > 0
  val maxHeight =
    customMaxHeight ?: when {
      isImeVisible -> LocalConfiguration.current.screenHeightDp.dp
      LocalConfiguration.current.orientation == ORIENTATION_PORTRAIT ->
        LocalConfiguration.current.screenHeightDp.dp * .90f
      else -> LocalConfiguration.current.screenHeightDp.dp
    }

  val screenHeightPx =
    with(density) {
      LocalConfiguration.current.screenHeightDp.dp
        .toPx()
    }
  val decayAnimationSpec = rememberSplineBasedDecay<Float>()
  val anchoredDraggableState =
    remember {
      AnchoredDraggableState(
        initialValue = 1,
        snapAnimationSpec = sheetAnimationSpec,
        decayAnimationSpec = decayAnimationSpec,
        positionalThreshold = { with(density) { 56.dp.toPx() } },
        velocityThreshold = { with(density) { 125.dp.toPx() } },
      )
    }

  val scaledSwipeOffset = swipeOffset * 2f
  val currentIsSwipeActive by rememberUpdatedState(isSwipeActive)
  val currentScaledSwipeOffset by rememberUpdatedState(scaledSwipeOffset)
  val height =
    if (anchoredDraggableState.anchors.size >
      0
    ) {
      anchoredDraggableState.anchors.positionOf(1)
    } else {
      screenHeightPx
    }
  // Samples the externally driven swipe so its release can hand the finger's velocity to the sheet.
  val swipeVelocityTracker = remember { VelocityTracker1D(isDataDifferential = false) }
  var wasOpened by remember { mutableStateOf(false) }

  val internalOnDismissRequest = {
    if (anchoredDraggableState.currentValue == 0) {
      scope.launch {
        anchoredDraggableState.animateTo(1)
      }
    }
  }
  Box(
    modifier =
      Modifier
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = internalOnDismissRequest,
        ).fillMaxSize()
        // The scrim is a function of where the sheet actually is, read in the draw phase: it tracks
        // the finger 1:1 during drags, lands with the sheet's own spring, and never recomposes.
        .drawBehind {
          val closed = if (anchoredDraggableState.anchors.size > 0) anchoredDraggableState.anchors.positionOf(1) else Float.NaN
          val sheetOffset = anchoredDraggableState.offset
          if (closed.isFinite() && closed > 0f && sheetOffset.isFinite()) {
            val openFraction = (1f - sheetOffset / closed).coerceIn(0f, 1f)
            if (openFraction > 0f) drawRect(Color.Black, alpha = SCRIM_MAX_ALPHA * openFraction)
          }
        }.onSizeChanged {
          val anchors =
            DraggableAnchors {
              0 at 0f
              1 at it.height.toFloat()
            }
          anchoredDraggableState.updateAnchors(anchors)
        },
    contentAlignment = Alignment.BottomCenter,
  ) {
    Surface(
      modifier =
        Modifier
          .sizeIn(maxWidth = maxWidth, maxHeight = maxHeight)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
          ).nestedScroll(
            remember(anchoredDraggableState) {
              anchoredDraggableState.preUpPostDownNestedScrollConnection()
            },
          ).then(modifier)
          .tvFocusGroup()
          .offset {
            val baseOffset =
              anchoredDraggableState.offset
                .takeIf { it.isFinite() }
                ?: screenHeightPx
            IntOffset(0, baseOffset.roundToInt())
          }.graphicsLayer {
            this.alpha = if (anchoredDraggableState.anchors.size > 0) 1f else 0f
          }.anchoredDraggable(
            state = anchoredDraggableState,
            orientation = Orientation.Vertical,
          ).windowInsetsPadding(
            WindowInsets.systemBars
              .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
          ).imePadding(),
      shape = MaterialTheme.shapes.extraLarge.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
      color = surfaceColor ?: MaterialTheme.colorScheme.surface,
      tonalElevation = tonalElevation,
      content = {
        BackHandler(
          enabled = anchoredDraggableState.targetValue == 0,
          onBack = internalOnDismissRequest,
        )
        // The whole sheet already drags to dismiss; the handle makes that discoverable. The
        // title is announced as the pane title instead of being drawn twice (sheets render their own).
        Column(modifier = if (title != null) Modifier.semantics { paneTitle = title } else Modifier) {
          SheetDragHandle()
          content()
        }
      },
    )

    LaunchedEffect(anchoredDraggableState, screenHeightPx) {
      snapshotFlow {
        Triple(
          currentIsSwipeActive,
          currentScaledSwipeOffset,
          anchoredDraggableState.anchors.size,
        )
      }.collectLatest { (swipeActive, latestSwipeOffset, anchorCount) ->
        if (swipeActive && anchorCount > 0) {
          val closedOffset = anchoredDraggableState.anchors.positionOf(1)
          val currentOffset = anchoredDraggableState.offset
          if (closedOffset.isFinite() && currentOffset.isFinite()) {
            val targetOffset = (closedOffset + latestSwipeOffset).coerceIn(0f, screenHeightPx)
            swipeVelocityTracker.addDataPoint(SystemClock.uptimeMillis(), targetOffset)
            anchoredDraggableState.dispatchRawDelta(targetOffset - currentOffset)
          }
        }
      }
    }

    var wasSwipeActive by remember { mutableStateOf(false) }
    LaunchedEffect(anchoredDraggableState, isSwipeActive) {
      if (isSwipeActive) {
        wasSwipeActive = true
      } else {
        if (wasSwipeActive) {
          val currentOffset = anchoredDraggableState.offset
          // A release sample: if the finger paused before lifting, the gap makes the tracker read
          // the gesture as stopped instead of reusing the earlier flick's speed.
          if (currentOffset.isFinite()) swipeVelocityTracker.addDataPoint(SystemClock.uptimeMillis(), currentOffset)
          val releaseVelocity =
            swipeVelocityTracker.calculateVelocity().takeIf { it.isFinite() } ?: 0f
          swipeVelocityTracker.resetTracking()
          // Decide from where the gesture is heading, not only where the finger let go: a short,
          // fast upward flick opens the sheet. Open once the projected rest point clears 10%.
          val projectedOffset = currentOffset + projectSheetMomentum(releaseVelocity)
          val target = if (projectedOffset >= height * 0.9f) 1 else 0
          anchoredDraggableState.animateToWithVelocity(target, releaseVelocity)
          // When the sheet had opened, the currentValue collector below reports the dismissal;
          // reporting here too would fire onDismissRequest twice.
          if (target == 1 && !wasOpened) {
            latestOnDismissRequest()
          }
          wasSwipeActive = false
        } else {
          // Title tap opening: wait for anchors to be measured before animating open (0)
          snapshotFlow { anchoredDraggableState.anchors.size }
            .filter { it > 0 }
            .collectLatest {
              anchoredDraggableState.animateTo(0)
            }
        }
      }
    }

    LaunchedEffect(anchoredDraggableState) {
      snapshotFlow { anchoredDraggableState.currentValue }
        .collectLatest { value ->
          if (value == 0) {
            wasOpened = true
          } else if (value == 1 && wasOpened) {
            latestOnDismissRequest()
          }
        }
    }
  }
}

private const val SCRIM_MAX_ALPHA = 0.5f

/**
 * Distance a released swipe would still travel under scroll-like deceleration (rate 0.99 per ms),
 * used to pick the settle target from the gesture's trajectory.
 */
private fun projectSheetMomentum(velocityPxPerSecond: Float, decelerationRate: Float = 0.99f): Float =
  (velocityPxPerSecond / 1000f) * decelerationRate / (1f - decelerationRate)

/** Springs to [target] starting at the finger's release [velocity], so drag and settle have no seam. */
private suspend fun AnchoredDraggableState<Int>.animateToWithVelocity(
  target: Int,
  velocity: Float,
) {
  anchoredDrag(target) { anchors, latestTarget ->
    val targetOffset = anchors.positionOf(latestTarget)
    if (!targetOffset.isNaN() && offset.isFinite()) {
      animate(
        initialValue = offset,
        targetValue = targetOffset,
        initialVelocity = velocity,
        animationSpec = sheetAnimationSpec,
      ) { value, currentVelocity -> dragTo(value, currentVelocity) }
    }
  }
}

private fun <T> AnchoredDraggableState<T>.preUpPostDownNestedScrollConnection() =
  object : NestedScrollConnection {
    override fun onPreScroll(
      available: Offset,
      source: NestedScrollSource,
    ): Offset {
      val delta = available.toFloat()
      return if (delta < 0 && source == NestedScrollSource.UserInput) {
        dispatchRawDelta(delta).toOffset()
      } else {
        Offset.Zero
      }
    }

    override fun onPostScroll(
      consumed: Offset,
      available: Offset,
      source: NestedScrollSource,
    ): Offset =
      if (source == NestedScrollSource.UserInput) {
        dispatchRawDelta(available.toFloat()).toOffset()
      } else {
        Offset.Zero
      }

    override suspend fun onPreFling(available: Velocity): Velocity {
      val toFling = available.toFloat()
      return if (toFling < 0 && offset > anchors.minPosition()) {
        settle(toFling)
        available
      } else {
        Velocity.Zero
      }
    }

    override suspend fun onPostFling(
      consumed: Velocity,
      available: Velocity,
    ): Velocity {
      val toFling = available.toFloat()
      return if (toFling > 0) {
        settle(toFling)
        available
      } else {
        Velocity.Zero
      }
    }

    private fun Float.toOffset(): Offset = Offset(0f, this)

    @JvmName("velocityToFloat")
    private fun Velocity.toFloat() = y

    private fun Offset.toFloat(): Float = y
  }

/** Quiet grab bar shared with the player panels' handle: 32 x 4 dp on a 16 dp strip. */
@Composable
private fun SheetDragHandle() {
  Box(
    modifier = Modifier.fillMaxWidth().height(16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(width = 32.dp, height = 4.dp)
          .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
    )
  }
}
