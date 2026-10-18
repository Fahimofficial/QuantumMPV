/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.media.model.VideoFolder
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.MediaLayoutMode
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.browser.cards.FolderCard
import com.quantummpv.app.ui.browser.cards.VideoCard
import com.quantummpv.app.ui.browser.cards.VideoCardUiConfig
import com.quantummpv.app.ui.utils.calculateResponsiveGridSpans
import org.koin.compose.koinInject

/** Search results in the user's folder layout (grid or list): matching folders, then media. */
@Composable
internal fun MediaSearchResults(
  results: List<MediaItem>,
  mediaLayoutMode: MediaLayoutMode,
  bottomPadding: Dp,
  onFolderClick: (VideoFolder) -> Unit,
  onMediaClick: (Video) -> Unit,
  modifier: Modifier = Modifier,
) {
  val folders = remember(results) { results.filterIsInstance<MediaItem.Folder>() }
  val media = remember(results) { results.filterIsInstance<MediaItem.Media>() }
  val videoCardUiConfig = rememberVideoCardUiConfig()
  val showSubtitleIndicator by koinInject<BrowserPreferences>().showSubtitleIndicator.collectAsState()
  val contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottomPadding + 8.dp)

  Box(modifier = modifier.fillMaxSize()) {
    if (mediaLayoutMode == MediaLayoutMode.GRID) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val spansInfo = calculateResponsiveGridSpans(maxWidth = maxWidth, isGridMode = true)
        LazyVerticalGrid(
          columns = GridCells.Fixed(spansInfo.spans),
          modifier = Modifier.fillMaxSize(),
          contentPadding = contentPadding,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(
            items = folders,
            key = { "folder:${it.folder.bucketId}" },
            contentType = { "folder_item" },
            span = { GridItemSpan(spansInfo.folderSpan) },
          ) { item ->
            FolderCard(
              folder = item.folder,
              isSelected = false,
              isRecentlyPlayed = false,
              onClick = { onFolderClick(item.folder) },
              onLongClick = {},
              onThumbClick = { onFolderClick(item.folder) },
              newVideoCount = 0,
              isGridMode = true,
            )
          }
          items(
            items = media,
            key = { "media:${it.video.path}" },
            contentType = { "video_item" },
            span = { GridItemSpan(spansInfo.videoSpan) },
          ) { item ->
            VideoCard(
              video = item.video,
              isSelected = false,
              onClick = { onMediaClick(item.video) },
              onLongClick = {},
              onThumbClick = { onMediaClick(item.video) },
              isGridMode = true,
              showSubtitleIndicator = showSubtitleIndicator,
              uiConfig = videoCardUiConfig,
            )
          }
        }
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(items = folders, key = { "folder:${it.folder.bucketId}" }, contentType = { "folder_item" }) { item ->
          FolderCard(
            folder = item.folder,
            isSelected = false,
            isRecentlyPlayed = false,
            onClick = { onFolderClick(item.folder) },
            onLongClick = {},
            onThumbClick = { onFolderClick(item.folder) },
            newVideoCount = 0,
            isGridMode = false,
          )
        }
        items(items = media, key = { "media:${it.video.path}" }, contentType = { "video_item" }) { item ->
          VideoCard(
            video = item.video,
            isSelected = false,
            onClick = { onMediaClick(item.video) },
            onLongClick = {},
            onThumbClick = { onMediaClick(item.video) },
            isGridMode = false,
            showSubtitleIndicator = showSubtitleIndicator,
            uiConfig = videoCardUiConfig,
          )
        }
      }
    }
  }
}

@Composable
private fun rememberVideoCardUiConfig(): VideoCardUiConfig {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showCodecSupportIndicator by browserPreferences.showCodecSupportIndicator.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val showUnplayedOldVideoLabel by appearancePreferences.showUnplayedOldVideoLabel.collectAsState()
  val unplayedOldVideoDays by appearancePreferences.unplayedOldVideoDays.collectAsState()
  val showExtensionField by browserPreferences.showExtensionField.collectAsState()
  val showDurationField by browserPreferences.showDurationField.collectAsState()
  val centerGridTitles by browserPreferences.centerGridTitles.collectAsState()
  val thumbnailQuality by browserPreferences.thumbnailQuality.collectAsState()
  return remember(
    unlimitedNameLines,
    showVideoThumbnails,
    showSizeChip,
    showResolutionChip,
    showFramerateInResolution,
    showCodecSupportIndicator,
    showProgressBar,
    showDateChip,
    showUnplayedOldVideoLabel,
    unplayedOldVideoDays,
    showExtensionField,
    showDurationField,
    centerGridTitles,
    thumbnailQuality,
  ) {
    VideoCardUiConfig(
      unlimitedNameLines = unlimitedNameLines,
      showThumbnails = showVideoThumbnails,
      showSizeChip = showSizeChip,
      showResolutionChip = showResolutionChip,
      showFramerateInResolution = showFramerateInResolution,
      showCodecSupportIndicator = showCodecSupportIndicator,
      showProgressBar = showProgressBar,
      showDateChip = showDateChip,
      showUnplayedOldVideoLabel = showUnplayedOldVideoLabel,
      unplayedOldVideoDays = unplayedOldVideoDays,
      showExtensionField = showExtensionField,
      showDurationField = showDurationField,
      centerGridTitles = centerGridTitles,
      thumbnailQuality = thumbnailQuality,
    )
  }
}
