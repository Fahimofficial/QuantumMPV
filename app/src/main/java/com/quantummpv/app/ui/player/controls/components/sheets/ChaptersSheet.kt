/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantummpv.app.R
import com.quantummpv.app.presentation.components.PlayerSheet
import com.quantummpv.app.ui.player.PlayerViewModel
import com.quantummpv.app.ui.utils.rememberAppHaptics
import com.quantummpv.app.ui.theme.spacing
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ChaptersSheet(
  chapters: ImmutableList<Segment>,
  currentChapter: Segment?,
  onClick: (Segment) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  itemActions: @Composable (Segment) -> Unit = {},
) {
  val listState = rememberLazyListState()

  LaunchedEffect(currentChapter, chapters) {
    val index = if (currentChapter != null) chapters.indexOf(currentChapter) else -1
    if (index >= 0) {
      listState.scrollToItem(index + 1)
    }
  }

  PlayerSheet(onDismissRequest) {
    Column(
      modifier =
        modifier
          .padding(vertical = MaterialTheme.spacing.medium),
    ) {
      LazyColumn(state = listState) {
        item {
          val isAudiobook = com.quantummpv.app.ui.player.PlaybackSession.state.collectAsStateWithLifecycle().value.currentItem?.audiobook != null
          Text(
            text = stringResource(if (isAudiobook) R.string.audiobook_chapters else R.string.btn_label_bookmarks),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        if (chapters.isEmpty()) item {
          Text(stringResource(R.string.playback_bookmarks_empty), Modifier.padding(MaterialTheme.spacing.medium),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(chapters) { index, chapter ->
          ChapterTrack(
            chapter = chapter,
            index = index,
            selected = currentChapter == chapter,
            onClick = { onClick(chapter) },
            trailingContent = { itemActions(chapter) },
          )
        }
      }
    }
  }
}

@Composable
fun ChapterTrack(
  chapter: Segment,
  index: Int,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  trailingContent: @Composable () -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = MaterialTheme.spacing.smaller, horizontal = MaterialTheme.spacing.medium),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(R.string.player_sheets_track_title_wo_lang, index + 1, chapter.name),
      fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      modifier = Modifier.weight(1f),
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      Utils.prettyTime(chapter.start.toInt()),
      fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(start = MaterialTheme.spacing.small),
    )
    trailingContent()
  }
}

@Composable
internal fun PlaybackBookmarkEditor(viewModel: PlayerViewModel, onSaved: () -> Unit, onDismiss: () -> Unit) {
  val draft by viewModel.bookmarkDraft.collectAsStateWithLifecycle()
  val mediaId by viewModel.bookmarkMediaId.collectAsStateWithLifecycle()
  val bookmark = draft?.takeIf { it.mediaId == mediaId }
  if (bookmark == null) {
    LaunchedEffect(draft, mediaId) { onDismiss() }
    return
  }
  var name by rememberSaveable(bookmark.mediaId, bookmark.id, bookmark.positionMs) { mutableStateOf(bookmark.title) }
  var saving by remember(bookmark) { mutableStateOf(false) }
  var failed by remember(bookmark) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val haptics = rememberAppHaptics()
  PlayerSheet(onDismissRequest = { if (!saving) onDismiss() }) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(stringResource(R.string.audiobook_bookmark_name), style = MaterialTheme.typography.titleMedium)
      Text(Utils.prettyTime((viewModel.bookmarkPositionMs(bookmark) / 1000).toInt()),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), enabled = !saving, maxLines = 3,
        label = { Text(stringResource(R.string.audiobook_bookmark_name)) })
      if (failed) Text(stringResource(R.string.playback_bookmark_update_failed), color = MaterialTheme.colorScheme.error)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.generic_cancel)) }
        TextButton(enabled = name.isNotBlank() && !saving, onClick = {
          saving = true
          failed = false
          scope.launch {
            try {
              viewModel.savePlaybackBookmark(bookmark, name)
              haptics.confirm()
              onSaved()
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              failed = true
            } finally {
              saving = false
            }
          }
        }) { Text(stringResource(R.string.audiobook_save)) }
      }
    }
  }
}
