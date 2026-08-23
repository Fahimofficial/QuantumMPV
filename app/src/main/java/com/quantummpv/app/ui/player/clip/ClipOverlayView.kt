/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.clip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon as AppIcon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.PlaybackSession
import com.quantummpv.app.ui.player.PlayerActivity
import com.quantummpv.app.ui.player.PlayerViewModel
import com.quantummpv.app.ui.player.controls.components.panels.DraggablePanel
import com.quantummpv.app.ui.theme.MpvrxTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private data class ClipPanelState(
  val startTime: String = "--:--",
  val endTime: String? = null,
  val startSeconds: Float = 0f,
  val endSeconds: Float? = null,
  val durationSeconds: Float = 0f,
  val crop: ClipCrop? = null,
  val canSave: Boolean = false,
  val exporting: Boolean = false,
  val cancelling: Boolean = false,
  val progress: Int = 0,
  val panelActive: Boolean = false,
)

/**
 * Player-local editor surface for Clip.
 *
 * The scissors action itself is a normal customizable Compose player button. This sibling overlay
 * exists only for UI that must sit directly over the video: the Clip editor card and crop selector.
 * Outside those visible children it does not consume input, so the normal seekbar and gestures keep
 * working while a Clip draft is open.
 */
class ClipOverlayView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
  private data class ClipDraft(
    var startSeconds: Double,
    var endSeconds: Double? = null,
    var crop: ClipCrop? = null,
  )

  private val panelView = ComposeView(context)
  private var panelState by mutableStateOf(ClipPanelState())

  private var draft: ClipDraft? = null
  private var cropView: CropSelectionView? = null
  private var cropControls: ComposeView? = null
  private var pausedBeforeCrop = true
  private var reopenPanelAfterCrop = true
  private var lastTerminalState: ClipExportState? = null
  private var bottomInset = 0

  private val pollState =
    object : Runnable {
      override fun run() {
        updateExportState()
        postDelayed(this, STATE_POLL_MS)
      }
    }

  init {
    isClickable = false
    isFocusable = false
    clipChildren = false
    clipToPadding = false
    setupPanel()

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
      updateOverlayMargins()
      insets
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    removeCallbacks(pollState)
    post(pollState)
  }

  override fun onDetachedFromWindow() {
    removeCallbacks(pollState)
    if (cropView != null) exitCropMode(keepSelection = false)
    super.onDetachedFromWindow()
  }

  override fun onSizeChanged(
    w: Int,
    h: Int,
    oldw: Int,
    oldh: Int,
  ) {
    super.onSizeChanged(w, h, oldw, oldh)
    updateOverlayMargins()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean =
    if (cropView != null) true else super.onTouchEvent(event)

  /** Opens the compact Clip editor from a long-press on the player control. */
  fun openClip(cropImmediately: Boolean = false) {
    beginClip(cropImmediately)
  }

  /** Marks the current playback position as the quick Clip start without opening the editor. */
  fun markStartAtCurrent() {
    if (PlaybackSession.state.value.currentItem == null) {
      toast(R.string.clip_video_unavailable)
      return
    }
    val current = currentPosition() ?: return
    val active = draft ?: ClipDraft(startSeconds = current, endSeconds = null).also { draft = it }
    active.startSeconds = current
    if ((active.endSeconds ?: Double.POSITIVE_INFINITY) <= current + MIN_CLIP_SECONDS) {
      active.endSeconds = null
    }
    // Quick Start behaves like A/B Loop point A: update the range without hiding player controls.
    refreshDraftUi()
  }

  /** Marks the current playback position as the quick Clip end without opening the editor. */
  fun markEndAtCurrent() {
    if (PlaybackSession.state.value.currentItem == null) {
      toast(R.string.clip_video_unavailable)
      return
    }
    val current = currentPosition() ?: return
    val active =
      draft ?: ClipDraft(
        startSeconds = (current - DEFAULT_CLIP_SECONDS).coerceAtLeast(0.0),
        endSeconds = null,
      ).also { draft = it }
    if (current <= active.startSeconds + MIN_CLIP_SECONDS) {
      toast(R.string.clip_invalid_range)
      return
    }
    active.endSeconds = current
    // Quick End behaves like A/B Loop point B: update the range without hiding player controls.
    refreshDraftUi()
  }

  /** Opens only crop UI; Done/Cancel returns directly to normal player controls. */
  fun openCrop() {
    if (PlaybackSession.state.value.currentItem == null) {
      toast(R.string.clip_video_unavailable)
      return
    }
    ensureDraft()
    refreshDraftUi()
    enterCropMode(reopenEditorAfterCrop = false)
  }

  private fun playerViewModel(): PlayerViewModel? {
    val owner = findViewTreeViewModelStoreOwner() ?: return null
    return ViewModelProvider(owner)[PlayerViewModel::class.java]
  }

  private fun setupPanel() {
    panelView.apply {
      visibility = GONE
      isClickable = false
      isFocusable = false
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MpvrxTheme {
          ClipEditorPanel(
            state = panelState,
            onRangeChange = ::updateClipRange,
            onMarkStart = ::markClipStart,
            onMarkEnd = ::markClipEnd,
            onCrop = ::enterCropMode,
            onCancel = ::cancelOrClose,
            onSave = ::saveOrCancelExport,
          )
        }
      }
    }
    addView(
      panelView,
      LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
  }

  private fun beginClip(cropImmediately: Boolean) {
    if (PlaybackSession.state.value.currentItem == null) {
      toast(R.string.clip_video_unavailable)
      return
    }

    if (ClipExportManager.state.value is ClipExportState.Exporting) {
      panelState = panelState.copy(panelActive = true)
      panelView.visibility = VISIBLE
      updateExportState()
      return
    }

    ensureDraft()
    refreshDraftUi()
    panelState = panelState.copy(panelActive = true)
    panelView.visibility = VISIBLE

    // Keep the seekbar visible while the draggable editor is open, without covering the video in controls.
    playerViewModel()?.autoHideControls()

    if (cropImmediately) enterCropMode()
  }

  private fun ensureDraft(): ClipDraft {
    draft?.let { return it }

    val duration = mediaDurationSeconds()
    var start = (currentPosition() ?: 0.0).coerceAtLeast(0.0)
    val end =
      if (duration > MIN_CLIP_SECONDS) {
        if (start >= duration - MIN_CLIP_SECONDS) {
          start = (duration - DEFAULT_CLIP_SECONDS).coerceAtLeast(0.0)
        }
        (start + DEFAULT_CLIP_SECONDS).coerceAtMost(duration)
      } else {
        start + DEFAULT_CLIP_SECONDS
      }

    return ClipDraft(
      startSeconds = start,
      endSeconds = end.coerceAtLeast(start + MIN_CLIP_SECONDS),
    ).also {
      draft = it
      refreshDraftUi()
    }
  }

  private fun updateClipRange(
    start: Float,
    end: Float,
    preview: Float,
  ) {
    val active = ensureDraft()
    val duration = mediaDurationSeconds().takeIf { it > MIN_CLIP_SECONDS }
    val maxEnd = duration?.toFloat() ?: maxOf(end, start + 0.05f)
    val safeStart = start.coerceIn(0f, (maxEnd - 0.05f).coerceAtLeast(0f))
    val safeEnd = end.coerceIn(safeStart + 0.05f, maxEnd)
    active.startSeconds = safeStart.toDouble()
    active.endSeconds = safeEnd.toDouble()
    refreshDraftUi()
    PlaybackSession.setPropertyDouble(
      "time-pos",
      preview.coerceIn(safeStart, safeEnd).toDouble(),
    )
    playerViewModel()?.autoHideControls()
  }

  private fun markClipStart() {
    val current = currentPosition() ?: return
    val active = ensureDraft()
    active.startSeconds = current
    if ((active.endSeconds ?: Double.POSITIVE_INFINITY) <= current + MIN_CLIP_SECONDS) {
      active.endSeconds = null
    }
    refreshDraftUi()
    playerViewModel()?.autoHideControls()
  }

  private fun markClipEnd() {
    val active = ensureDraft()
    val current = currentPosition() ?: return
    if (current <= active.startSeconds + MIN_CLIP_SECONDS) {
      toast(R.string.clip_invalid_range)
      return
    }
    active.endSeconds = current
    refreshDraftUi()
    playerViewModel()?.autoHideControls()
  }

  private fun saveOrCancelExport() {
    val exportState = ClipExportManager.state.value
    if (exportState is ClipExportState.Exporting) {
      ClipExportManager.cancel()
      updateExportState()
      return
    }

    val active = draft ?: return
    val end = active.endSeconds
    val item = PlaybackSession.state.value.currentItem
    if (end == null || end <= active.startSeconds + MIN_CLIP_SECONDS || item == null) {
      toast(R.string.clip_invalid_range)
      return
    }

    val accepted =
      ClipExportManager.export(
        context,
        ClipRequest(
          item = item,
          startSeconds = active.startSeconds,
          endSeconds = end,
          crop = active.crop,
        ),
      )
    if (!accepted) toast(R.string.clip_export_busy)
    updateExportState()
  }

  private fun cancelOrClose() {
    if (ClipExportManager.state.value is ClipExportState.Exporting) {
      ClipExportManager.cancel()
      updateExportState()
    } else {
      closeDraft()
    }
  }

  private fun closeDraft() {
    if (cropView != null) exitCropMode(keepSelection = false)
    draft = null
    ClipEditorUiState.clear()
    panelState = ClipPanelState()
    panelView.visibility = GONE
    playerViewModel()?.showControls()
  }

  private fun enterCropMode() {
    enterCropMode(reopenEditorAfterCrop = true)
  }

  private fun enterCropMode(reopenEditorAfterCrop: Boolean) {
    if (cropView != null) return
    this.reopenPanelAfterCrop = reopenEditorAfterCrop
    val active = ensureDraft()
    val sourceWidth = PlaybackSession.getPropertyInt("video-params/w") ?: 0
    val sourceHeight = PlaybackSession.getPropertyInt("video-params/h") ?: 0
    if (sourceWidth <= 0 || sourceHeight <= 0) {
      toast(R.string.clip_video_unavailable)
      return
    }

    val rotation = ((PlaybackSession.getPropertyInt("video-params/rotate") ?: 0) % 360 + 360) % 360
    val outputWidth = PlaybackSession.getPropertyInt("video-out-params/dw") ?: 0
    val outputHeight = PlaybackSession.getPropertyInt("video-out-params/dh") ?: 0

    playerViewModel()?.hideControls()
    pausedBeforeCrop = PlaybackSession.getPropertyBoolean("pause") ?: true
    if (!pausedBeforeCrop) PlaybackSession.setPropertyBoolean("pause", true)

    panelState = panelState.copy(panelActive = false)
    panelView.visibility = GONE
    val selector =
      CropSelectionView(
        context = context,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        displayWidth = outputWidth,
        displayHeight = outputHeight,
        rotation = rotation,
        initialCrop = active.crop,
      )
    cropView = selector
    addView(selector, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

    val controls = buildCropControls(selector)
    cropControls = controls
    addView(
      controls,
      LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
        leftMargin = dp(8)
        rightMargin = dp(8)
        bottomMargin = bottomInset + dp(24)
      },
    )
  }

  private fun buildCropControls(selector: CropSelectionView): ComposeView =
    ComposeView(context).apply {
      isClickable = true
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MpvrxTheme {
          ClipCropControls(
            onCancel = { exitCropMode(keepSelection = false) },
            onDone = {
              draft?.crop = selector.currentCrop()
              exitCropMode(keepSelection = true)
            },
          )
        }
      }
    }

  private fun exitCropMode(keepSelection: Boolean) {
    val selector = cropView ?: return
    if (!keepSelection) {
      // Cancelling crop intentionally keeps the previous crop selection, if one existed.
    }
    removeView(selector)
    cropView = null
    cropControls?.let(::removeView)
    cropControls = null
    if (!pausedBeforeCrop) PlaybackSession.setPropertyBoolean("pause", false)
    val reopenPanel = draft != null && reopenPanelAfterCrop
    reopenPanelAfterCrop = true
    panelState = panelState.copy(panelActive = reopenPanel)
    panelView.visibility = if (reopenPanel) VISIBLE else GONE
    refreshDraftUi()
    if (reopenPanel) {
      playerViewModel()?.autoHideControls()
    } else {
      playerViewModel()?.showControls()
    }
  }

  private fun updateExportState() {
    when (val state = ClipExportManager.state.value) {
      ClipExportState.Idle -> {
        panelState =
          panelState.copy(
            exporting = false,
            cancelling = false,
            progress = 0,
          )
        lastTerminalState = null
      }
      is ClipExportState.Exporting -> {
        panelView.visibility = VISIBLE
        panelState =
          panelState.copy(
            exporting = true,
            cancelling = state.cancelling,
            progress = (state.progress * 100f).roundToInt().coerceIn(0, 100),
            panelActive = true,
          )
      }
      is ClipExportState.Success -> {
        if (lastTerminalState !== state) {
          toast(context.getString(R.string.clip_saved, state.displayName))
          lastTerminalState = state
          draft = null
          ClipEditorUiState.clear()
          panelState = ClipPanelState()
          panelView.visibility = GONE
          playerViewModel()?.showControls()
          ClipExportManager.consumeTerminalState()
        }
      }
      is ClipExportState.Error -> {
        if (lastTerminalState !== state) {
          toast(context.getString(R.string.clip_export_failed, state.message))
          lastTerminalState = state
          ClipExportManager.consumeTerminalState()
          panelState = panelState.copy(exporting = false, cancelling = false)
          refreshDraftUi()
        }
      }
    }
  }

  private fun refreshDraftUi() {
    val active = draft
    if (active == null) {
      ClipEditorUiState.clear()
      panelState =
        panelState.copy(
          startTime = "--:--",
          endTime = null,
          startSeconds = 0f,
          endSeconds = null,
          durationSeconds = 0f,
          crop = null,
          canSave = false,
        )
      return
    }

    val duration = mediaDurationSeconds().toFloat().coerceAtLeast(0f)
    ClipEditorUiState.publish(active.startSeconds, active.endSeconds)
    panelState =
      panelState.copy(
        startTime = formatTime(active.startSeconds),
        endTime = active.endSeconds?.let(::formatTime),
        startSeconds = active.startSeconds.toFloat(),
        endSeconds = active.endSeconds?.toFloat(),
        durationSeconds = duration,
        crop = active.crop,
        canSave =
          active.endSeconds?.let { it > active.startSeconds + MIN_CLIP_SECONDS } == true &&
            ClipExportManager.state.value !is ClipExportState.Exporting,
      )
  }

  private fun updateOverlayMargins() {
    (cropControls?.layoutParams as? LayoutParams)?.let { params ->
      params.bottomMargin = bottomInset + dp(24)
      cropControls?.layoutParams = params
    }
  }

  private fun mediaDurationSeconds(): Double =
    PlaybackSession.getPropertyDouble("duration")
      ?: PlaybackSession.getPropertyInt("duration")?.toDouble()
      ?: 0.0

  private fun currentPosition(): Double? = PlaybackSession.getPropertyDouble("time-pos")

  private fun formatTime(seconds: Double): String {
    val totalTenths = (seconds.coerceAtLeast(0.0) * 10.0).roundToInt()
    val hours = totalTenths / 36_000
    val minutes = (totalTenths / 600) % 60
    val secs = (totalTenths / 10) % 60
    val tenths = totalTenths % 10
    return if (hours > 0) {
      "%d:%02d:%02d.%d".format(Locale.US, hours, minutes, secs, tenths)
    } else {
      "%02d:%02d.%d".format(Locale.US, minutes, secs, tenths)
    }
  }

  private fun toast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  }

  private fun toast(messageRes: Int) {
    Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

  companion object {
    private const val OVERLAY_TAG = "mpvrx_clip_overlay"
    private const val STATE_POLL_MS = 250L
    private const val MIN_CLIP_SECONDS = 0.05
    private const val DEFAULT_CLIP_SECONDS = 10.0

    /** Attaches the Clip editor above the player at runtime; no player_layout.xml host is needed. */
    internal fun ensureAttached(activity: PlayerActivity): ClipOverlayView {
      val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
      contentRoot.findViewWithTag<ClipOverlayView>(OVERLAY_TAG)?.let { return it }

      return ClipOverlayView(activity).also { overlay ->
        overlay.tag = OVERLAY_TAG
        contentRoot.addView(
          overlay,
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          ),
        )
      }
    }
  }
}

@Composable
private fun ClipEditorPanel(
  state: ClipPanelState,
  onRangeChange: (Float, Float, Float) -> Unit,
  onMarkStart: () -> Unit,
  onMarkEnd: () -> Unit,
  onCrop: () -> Unit,
  onCancel: () -> Unit,
  onSave: () -> Unit,
) {
  BackHandler(enabled = state.panelActive, onBack = onCancel)

  DraggablePanel(
    header = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      ) {
        AppIcon(
imageVector = Icons.RoundedFilled.ContentCut,
contentDescription = null,
tint = MaterialTheme.colorScheme.primary,
modifier = Modifier.size(20.dp),
        )
        Text(
text = stringResource(R.string.clip_action),
style = MaterialTheme.typography.titleMedium,
modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onCancel) {
AppIcon(
  imageVector = Icons.RoundedFilled.Close,
  contentDescription = stringResource(R.string.clip_cancel),
  modifier = Modifier.size(24.dp),
)
        }
      }
    },
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      Row(modifier = Modifier.fillMaxWidth()) {
        ClipInfoPill(
text = stringResource(R.string.clip_start_time, state.startTime),
modifier = Modifier.weight(1f),
        )
        ClipInfoPill(
text = state.endTime?.let { stringResource(R.string.clip_end_time, it) }
  ?: stringResource(R.string.clip_end_not_set),
modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
      }

      val end = state.endSeconds
      val duration = state.durationSeconds
      if (end != null && duration > 0.05f) {
        val start = state.startSeconds.coerceIn(0f, duration)
        val safeEnd = end.coerceIn(start + 0.05f, duration)
        RangeSlider(
value = start..safeEnd,
onValueChange = { range ->
  val startDelta = abs(range.start - start)
  val endDelta = abs(range.endInclusive - safeEnd)
  val preview = if (startDelta >= endDelta) range.start else range.endInclusive
  onRangeChange(range.start, range.endInclusive, preview)
},
valueRange = 0f..duration,
enabled = !state.exporting,
modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
      }

      Text(
        text = state.crop?.let { stringResource(R.string.clip_crop_size, it.width, it.height) }
?: stringResource(R.string.clip_crop_full_frame),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
      )

      Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        FilledTonalButton(onClick = onMarkStart, enabled = !state.exporting, modifier = Modifier.weight(1f)) {
Text(stringResource(R.string.clip_start_short), maxLines = 1)
        }
        FilledTonalButton(
onClick = onCrop,
enabled = !state.exporting,
modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        ) {
Text(stringResource(R.string.clip_crop), maxLines = 1)
        }
        FilledTonalButton(onClick = onMarkEnd, enabled = !state.exporting, modifier = Modifier.weight(1f)) {
Text(stringResource(R.string.clip_end_short), maxLines = 1)
        }
      }

      if (state.exporting) {
        LinearProgressIndicator(
progress = { state.progress / 100f },
modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
text = if (state.cancelling) stringResource(R.string.clip_cancelling)
  else stringResource(R.string.clip_exporting, state.progress),
style = MaterialTheme.typography.bodySmall,
color = MaterialTheme.colorScheme.onSurfaceVariant,
modifier = Modifier.padding(top = 4.dp),
        )
      }

      if (state.exporting) {
        Button(
onClick = onSave,
enabled = !state.cancelling,
modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
Text(stringResource(R.string.clip_cancel))
        }
      } else {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
  Text(stringResource(R.string.clip_cancel))
}
Button(
  onClick = onSave,
  enabled = state.canSave,
  modifier = Modifier.weight(1f).padding(start = 8.dp),
) {
  Text(stringResource(R.string.clip_save))
}
        }
      }
    }
  }
}

@Composable
private fun ClipInfoPill(
  text: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
  }
}

@Composable
private fun ClipCropControls(
  onCancel: () -> Unit,
  onDone: () -> Unit,
) {
  BackHandler(onBack = onCancel)

  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp),
      ) {
        Text(
          text = stringResource(R.string.clip_crop),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = stringResource(R.string.clip_select_crop),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
          OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
          ) {
            Text(stringResource(R.string.clip_cancel))
          }
          Button(
            onClick = onDone,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
          ) {
            Text(stringResource(R.string.clip_done))
          }
        }
      }
    }
  }
}

private class CropSelectionView(
  context: Context,
  private val sourceWidth: Int,
  private val sourceHeight: Int,
  displayWidth: Int,
  displayHeight: Int,
  private val rotation: Int,
  private val initialCrop: ClipCrop?,
) : View(context) {
  private enum class DragMode {
    NONE,
    MOVE,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
  }

  private val density = resources.displayMetrics.density
  private val videoBounds = RectF()
  private val selection = RectF()
  private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
  private val borderPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeWidth = dpF(2f)
    }
  private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
  private val labelPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = spF(14f)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDD111111.toInt() }
  private val contentAspectWidth: Int
  private val contentAspectHeight: Int
  private val orientedWidth: Int
  private val orientedHeight: Int

  private var dragMode = DragMode.NONE
  private var lastX = 0f
  private var lastY = 0f
  private var selectionInitialized = false

  init {
    val quarterTurns = ((rotation % 360 + 360) % 360) == 90 || ((rotation % 360 + 360) % 360) == 270
    orientedWidth = if (quarterTurns) sourceHeight else sourceWidth
    orientedHeight = if (quarterTurns) sourceWidth else sourceHeight
    contentAspectWidth = displayWidth.takeIf { it > 0 } ?: orientedWidth
    contentAspectHeight = displayHeight.takeIf { it > 0 } ?: orientedHeight
    isClickable = true
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
  }

  override fun onSizeChanged(
    w: Int,
    h: Int,
    oldw: Int,
    oldh: Int,
  ) {
    super.onSizeChanged(w, h, oldw, oldh)
    updateVideoBounds(w.toFloat(), h.toFloat())
    initializeSelectionIfNeeded()
  }

  private fun updateVideoBounds(
    availableWidth: Float,
    availableHeight: Float,
  ) {
    if (availableWidth <= 0f || availableHeight <= 0f) return
    val aspect = contentAspectWidth.toFloat() / contentAspectHeight.toFloat().coerceAtLeast(1f)
    val viewAspect = availableWidth / availableHeight
    if (viewAspect > aspect) {
      val videoWidth = availableHeight * aspect
      val left = (availableWidth - videoWidth) / 2f
      videoBounds.set(left, 0f, left + videoWidth, availableHeight)
    } else {
      val videoHeight = availableWidth / aspect
      val top = (availableHeight - videoHeight) / 2f
      videoBounds.set(0f, top, availableWidth, top + videoHeight)
    }
  }

  private fun initializeSelectionIfNeeded() {
    if (selectionInitialized || videoBounds.isEmpty) return
    val existing = initialCrop?.takeIf { it.rotation == rotation && it.width > 0 && it.height > 0 }
    if (existing != null) {
      val left = videoBounds.left + videoBounds.width() * existing.x / orientedWidth.toFloat()
      val top = videoBounds.top + videoBounds.height() * existing.y / orientedHeight.toFloat()
      val right = videoBounds.left + videoBounds.width() * (existing.x + existing.width) / orientedWidth.toFloat()
      val bottom = videoBounds.top + videoBounds.height() * (existing.y + existing.height) / orientedHeight.toFloat()
      selection.set(left, top, right, bottom)
    } else {
      val horizontalInset = videoBounds.width() * 0.08f
      val verticalInset = videoBounds.height() * 0.08f
      selection.set(
        videoBounds.left + horizontalInset,
        videoBounds.top + verticalInset,
        videoBounds.right - horizontalInset,
        videoBounds.bottom - verticalInset,
      )
    }
    selectionInitialized = true
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    initializeSelectionIfNeeded()
    if (selection.isEmpty) return

    val outside =
      Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        addRoundRect(selection, dpF(4f), dpF(4f), Path.Direction.CW)
      }
    canvas.drawPath(outside, dimPaint)
    canvas.drawRoundRect(selection, dpF(4f), dpF(4f), borderPaint)

    val handleRadius = dpF(6f)
    handlePoints().forEach { (x, y) -> canvas.drawCircle(x, y, handleRadius, handlePaint) }

    val crop = currentCrop()
    val text = "${crop.width} × ${crop.height}"
    val textWidth = labelPaint.measureText(text)
    val textHeight = labelPaint.fontMetrics.run { bottom - top }
    val horizontalPadding = dpF(10f)
    val verticalPadding = dpF(6f)
    val labelWidth = textWidth + horizontalPadding * 2f
    val labelHeight = textHeight + verticalPadding * 2f
    val preferredTop = selection.top - labelHeight - dpF(8f)
    val labelTop =
      if (preferredTop >= videoBounds.top) preferredTop else min(selection.top + dpF(10f), selection.bottom - labelHeight - dpF(4f))
    val labelLeft = (selection.centerX() - labelWidth / 2f).coerceIn(videoBounds.left, videoBounds.right - labelWidth)
    val labelRect = RectF(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
    canvas.drawRoundRect(labelRect, dpF(12f), dpF(12f), labelBackgroundPaint)
    val baseline = labelRect.top + verticalPadding - labelPaint.fontMetrics.top
    canvas.drawText(text, labelRect.left + horizontalPadding, baseline, labelPaint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!isEnabled || selection.isEmpty) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        dragMode = hitTest(event.x, event.y)
        if (dragMode == DragMode.NONE) return false
        lastX = event.x
        lastY = event.y
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (dragMode == DragMode.NONE) return false
        val dx = event.x - lastX
        val dy = event.y - lastY
        updateSelection(dx, dy)
        lastX = event.x
        lastY = event.y
        invalidate()
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (dragMode != DragMode.NONE) {
          dragMode = DragMode.NONE
          parent?.requestDisallowInterceptTouchEvent(false)
          performClick()
          return true
        }
      }
    }
    return super.onTouchEvent(event)
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  fun currentCrop(): ClipCrop {
    val normalizedLeft = ((selection.left - videoBounds.left) / videoBounds.width()).coerceIn(0f, 1f)
    val normalizedTop = ((selection.top - videoBounds.top) / videoBounds.height()).coerceIn(0f, 1f)
    val normalizedRight = ((selection.right - videoBounds.left) / videoBounds.width()).coerceIn(0f, 1f)
    val normalizedBottom = ((selection.bottom - videoBounds.top) / videoBounds.height()).coerceIn(0f, 1f)

    var x = floor(normalizedLeft * orientedWidth).toInt().coerceIn(0, (orientedWidth - 2).coerceAtLeast(0))
    var y = floor(normalizedTop * orientedHeight).toInt().coerceIn(0, (orientedHeight - 2).coerceAtLeast(0))
    var right = ceil(normalizedRight * orientedWidth).toInt().coerceIn(x + 1, orientedWidth)
    var bottom = ceil(normalizedBottom * orientedHeight).toInt().coerceIn(y + 1, orientedHeight)

    x = evenFloor(x)
    y = evenFloor(y)
    right = evenFloor(right).coerceAtLeast(x + 2).coerceAtMost(evenFloor(orientedWidth))
    bottom = evenFloor(bottom).coerceAtLeast(y + 2).coerceAtMost(evenFloor(orientedHeight))
    if (right <= x) right = min(evenFloor(orientedWidth), x + 2)
    if (bottom <= y) bottom = min(evenFloor(orientedHeight), y + 2)

    return ClipCrop(
      x = x,
      y = y,
      width = (right - x).coerceAtLeast(2),
      height = (bottom - y).coerceAtLeast(2),
      rotation = rotation,
    )
  }

  private fun hitTest(
    x: Float,
    y: Float,
  ): DragMode {
    val threshold = dpF(28f)
    val nearLeft = kotlin.math.abs(x - selection.left) <= threshold
    val nearRight = kotlin.math.abs(x - selection.right) <= threshold
    val nearTop = kotlin.math.abs(y - selection.top) <= threshold
    val nearBottom = kotlin.math.abs(y - selection.bottom) <= threshold
    val withinHorizontal = x in (selection.left - threshold)..(selection.right + threshold)
    val withinVertical = y in (selection.top - threshold)..(selection.bottom + threshold)

    return when {
      nearLeft && nearTop -> DragMode.TOP_LEFT
      nearRight && nearTop -> DragMode.TOP_RIGHT
      nearLeft && nearBottom -> DragMode.BOTTOM_LEFT
      nearRight && nearBottom -> DragMode.BOTTOM_RIGHT
      nearLeft && withinVertical -> DragMode.LEFT
      nearRight && withinVertical -> DragMode.RIGHT
      nearTop && withinHorizontal -> DragMode.TOP
      nearBottom && withinHorizontal -> DragMode.BOTTOM
      selection.contains(x, y) -> DragMode.MOVE
      else -> DragMode.NONE
    }
  }

  private fun updateSelection(
    dx: Float,
    dy: Float,
  ) {
    val minSize = dpF(48f)
    when (dragMode) {
      DragMode.MOVE -> {
        var moveX = dx
        var moveY = dy
        if (selection.left + moveX < videoBounds.left) moveX = videoBounds.left - selection.left
        if (selection.right + moveX > videoBounds.right) moveX = videoBounds.right - selection.right
        if (selection.top + moveY < videoBounds.top) moveY = videoBounds.top - selection.top
        if (selection.bottom + moveY > videoBounds.bottom) moveY = videoBounds.bottom - selection.bottom
        selection.offset(moveX, moveY)
      }
      DragMode.LEFT, DragMode.TOP_LEFT, DragMode.BOTTOM_LEFT -> {
        selection.left = (selection.left + dx).coerceIn(videoBounds.left, selection.right - minSize)
        if (dragMode == DragMode.TOP_LEFT) {
          selection.top = (selection.top + dy).coerceIn(videoBounds.top, selection.bottom - minSize)
        } else if (dragMode == DragMode.BOTTOM_LEFT) {
          selection.bottom = (selection.bottom + dy).coerceIn(selection.top + minSize, videoBounds.bottom)
        }
      }
      DragMode.RIGHT, DragMode.TOP_RIGHT, DragMode.BOTTOM_RIGHT -> {
        selection.right = (selection.right + dx).coerceIn(selection.left + minSize, videoBounds.right)
        if (dragMode == DragMode.TOP_RIGHT) {
          selection.top = (selection.top + dy).coerceIn(videoBounds.top, selection.bottom - minSize)
        } else if (dragMode == DragMode.BOTTOM_RIGHT) {
          selection.bottom = (selection.bottom + dy).coerceIn(selection.top + minSize, videoBounds.bottom)
        }
      }
      DragMode.TOP -> selection.top = (selection.top + dy).coerceIn(videoBounds.top, selection.bottom - minSize)
      DragMode.BOTTOM -> selection.bottom = (selection.bottom + dy).coerceIn(selection.top + minSize, videoBounds.bottom)
      DragMode.NONE -> Unit
    }
  }

  private fun handlePoints(): List<Pair<Float, Float>> =
    listOf(
      selection.left to selection.top,
      selection.centerX() to selection.top,
      selection.right to selection.top,
      selection.left to selection.centerY(),
      selection.right to selection.centerY(),
      selection.left to selection.bottom,
      selection.centerX() to selection.bottom,
      selection.right to selection.bottom,
    )

  private fun evenFloor(value: Int): Int = value and -2

  private fun dpF(value: Float): Float = value * density

  private fun spF(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
