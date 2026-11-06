/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.profile

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantummpv.app.R
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.thumbnail.ThumbnailRepository
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.theme.AppShapeScale
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** 16:9 frame for video or 1:1 frame for audio plus a caption. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(
  title: String,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  isAudio: Boolean,
  frame: @Composable BoxScope.() -> Unit,
) {
  val cardWidth = if (isAudio) SHELF_AUDIO_CARD_WIDTH else SHELF_VIDEO_CARD_WIDTH
  val cardShape = if (isAudio) RoundedCornerShape(16.dp) else RoundedCornerShape(14.dp)
  Column(
    modifier =
      Modifier
        .width(cardWidth)
        .tvFocusHighlight(cardShape)
        .clip(cardShape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(if (isAudio) 1f else 16f / 9f)
          .clip(cardShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center,
      content = frame,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
  }
}

@Composable
internal fun RecentVideoCard(
  video: Video,
  progress: Float?,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val density = LocalDensity.current
  val isAudio = video.isAudio
  val widthPx = with(density) { (if (isAudio) SHELF_AUDIO_CARD_WIDTH else SHELF_VIDEO_CARD_WIDTH).toPx().roundToInt() }
  val heightPx = if (isAudio) widthPx else widthPx * 9 / 16

  var thumbnail by remember(video.path) {
    mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, widthPx, heightPx))
  }
  LaunchedEffect(video.path) {
    if (thumbnail == null) thumbnail = thumbnailRepository.getThumbnail(video, widthPx, heightPx)
  }

  ShelfCard(title = video.displayName, onClick = onClick, onLongClick = onLongClick, isAudio = isAudio) {
    RecentVideoFrame(thumbnail, isAudio, video.durationFormatted, progress)
  }
}

@Composable
private fun BoxScope.RecentVideoFrame(
  thumbnail: Bitmap?,
  isAudio: Boolean,
  duration: String,
  progress: Float?,
) {
  val presentation = recentVideoFramePresentation(thumbnail != null, isAudio, duration, progress)
  RecentVideoArtwork(thumbnail, presentation.fallback)
  RecentVideoDuration(presentation.durationLabel)
  RecentVideoProgress(presentation.progress)
}

internal enum class RecentMediaFallback {
  AUDIO,
  VIDEO,
}

internal data class RecentVideoFramePresentation(
  val fallback: RecentMediaFallback?,
  val durationLabel: String?,
  val progress: Float?,
)

/** Maps media and playback state to the visible placeholder, duration badge, and progress bar. */
internal fun recentVideoFramePresentation(
  hasThumbnail: Boolean,
  isAudio: Boolean,
  duration: String,
  progress: Float?,
): RecentVideoFramePresentation =
  RecentVideoFramePresentation(
    fallback = if (hasThumbnail) null else if (isAudio) RecentMediaFallback.AUDIO else RecentMediaFallback.VIDEO,
    durationLabel = duration.takeIf { it.isNotBlank() && it != "--:--" },
    progress = progress?.takeIf { it > 0f },
  )

@Composable
private fun BoxScope.RecentVideoArtwork(
  thumbnail: Bitmap?,
  fallback: RecentMediaFallback?,
) {
  if (thumbnail != null) {
    Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
  } else {
    Icon(
      if (fallback == RecentMediaFallback.AUDIO) Icons.RoundedFilled.Audiotrack else Icons.RoundedFilled.PlayArrow,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.align(Alignment.Center).size(if (fallback == RecentMediaFallback.AUDIO) 36.dp else 32.dp),
    )
  }
}

@Composable
private fun BoxScope.RecentVideoDuration(duration: String?) {
  if (duration != null) {
    Surface(
      shape = RoundedCornerShape(6.dp),
      color = Color.Black.copy(alpha = 0.75f),
      contentColor = Color.White,
      modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
    ) {
      Text(
        text = duration,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
  }
}

@Composable
private fun BoxScope.RecentVideoProgress(progress: Float?) {
  if (progress != null && progress > 0f) {
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
      color = MaterialTheme.colorScheme.primary,
      trackColor = Color.Black.copy(alpha = 0.35f),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentVideoActionsSheet(
  video: Video,
  onDismiss: () -> Unit,
  onPlay: () -> Unit,
  onShare: () -> Unit,
  onRemove: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState =
      rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
      ),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
      Text(
        text = video.displayName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
      SheetAction(Icons.RoundedFilled.PlayArrow, stringResource(R.string.ui_play), MaterialTheme.colorScheme.onSurface, onPlay)
      if (!video.path.contains("://")) {
        SheetAction(Icons.RoundedFilled.Share, stringResource(R.string.generic_share), MaterialTheme.colorScheme.onSurface, onShare)
      }
      SheetAction(Icons.RoundedFilled.Delete, stringResource(R.string.profile_remove_from_history), MaterialTheme.colorScheme.error, onRemove)
    }
  }
}

@Composable
private fun SheetAction(
  icon: AppIcon,
  label: String,
  tint: Color,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .clip(AppShapeScale.medium)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
  }
}
