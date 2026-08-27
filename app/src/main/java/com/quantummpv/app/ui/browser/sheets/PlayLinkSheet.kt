/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.quantummpv.app.database.repository.PlaylistRepository
import com.quantummpv.app.database.repository.NetworkStreamEntryRepository
import com.quantummpv.app.domain.torrent.isTorrentSource
import com.quantummpv.app.domain.torrent.normalizeTorrentSource
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.ytdlp.YtdlpManager
import com.quantummpv.app.utils.history.RecentlyPlayedOps
import com.quantummpv.app.utils.media.MediaInfoParser
import com.quantummpv.app.utils.media.MediaUtils
import com.quantummpv.app.utils.media.SharedUrlExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayLinkSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onPlayLink: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  var linkInputUrl by remember { mutableStateOf("") }
  var isLinkInputUrlValid by remember { mutableStateOf(true) }
  var isSubmitting by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  val playlistRepository = koinInject<PlaylistRepository>()
  val streamEntryRepository = koinInject<NetworkStreamEntryRepository>()

  val handleDismiss = { onDismiss() }

  val normalizedInput = SharedUrlExtractor.normalizeInput(linkInputUrl)
  val isPlaylistInput =
    isLinkInputUrlValid &&
      YtdlpManager.isPotentialPlaylistUrl(normalizedInput) &&
      YtdlpManager.requiresYtdlp(normalizedInput)

  val handleConfirm = {
    val url = normalizedInput
    if (url.isNotBlank() && MediaUtils.isURLValid(url)) {
      val playableSource = normalizeTorrentSource(url) ?: url
      isSubmitting = true
      coroutineScope.launch {
        try {
          if (isPlaylistInput) {
            val importResult = playlistRepository.createM3UPlaylist(playableSource)
            val playlistId = importResult.getOrNull()?.toInt()
            if (playlistId != null) {
              val playlistName = playlistRepository.getPlaylistById(playlistId)?.name.orEmpty()
              val itemCount = playlistRepository.getPlaylistItemCount(playlistId)
              android.widget.Toast
                .makeText(
                  context,
                  context.getString(
                    com.quantummpv.app.R.string.playlist_import_success_detail,
                    playlistName,
                    itemCount,
                  ),
                  android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
              val error = importResult.exceptionOrNull()
              android.widget.Toast
                .makeText(
                  context,
                  context.getString(
                    com.quantummpv.app.R.string.playlist_import_playback_fallback,
                    error?.message ?: context.getString(com.quantummpv.app.R.string.generic_unknown_error),
                  ),
                  android.widget.Toast.LENGTH_LONG,
                ).show()
            }
          }

          val name = MediaInfoParser.parseStreamTitle(playableSource)
          if (!isTorrentSource(playableSource)) {
            try {
              RecentlyPlayedOps.addRecentlyPlayed(
                filePath = playableSource,
                fileName = name,
                launchSource = "play_link",
              )
              streamEntryRepository.saveNormalEntry(
                canonicalSourceUri = playableSource,
                fileName = name,
              )

              val uri = runCatching { android.net.Uri.parse(playableSource) }.getOrNull()
              if (com.quantummpv.app.utils.media.HttpUtils.isYouTubeUrl(uri)) {
                val ytMeta = com.quantummpv.app.utils.media.HttpUtils.fetchYouTubeMetadata(playableSource)
                if (ytMeta != null && ytMeta.title.isNotBlank()) {
                  RecentlyPlayedOps.updateVideoMetadata(
                    filePath = playableSource,
                    videoTitle = ytMeta.title,
                    duration = 0L,
                    fileSize = 0L,
                    width = 0,
                    height = 0,
                  )
                  streamEntryRepository.saveNormalEntry(
                    canonicalSourceUri = playableSource,
                    fileName = ytMeta.title,
                    posterUrl = ytMeta.thumbnailUrl,
                    backdropUrl = ytMeta.thumbnailUrl,
                  )
                }
              }
            } catch (cancellation: CancellationException) {
              throw cancellation
            } catch (_: Exception) {
              // Playback must still open even if optional history persistence fails.
            }
          }
          onPlayLink(playableSource)
          onDismiss()
        } finally {
          isSubmitting = false
        }
      }
    }
  }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

  ModalBottomSheet(
    onDismissRequest = handleDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Title
      Text(
        text =
          androidx.compose.ui.res
            .stringResource(com.quantummpv.app.R.string.ui_play_link),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      // URL Input
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = linkInputUrl,
          onValueChange = { newValue ->
            linkInputUrl = newValue
            val normalizedInput = SharedUrlExtractor.normalizeInput(newValue)
            isLinkInputUrlValid = newValue.isBlank() || MediaUtils.isURLValid(normalizedInput)
          },
          modifier = Modifier.fillMaxWidth(),
          label = {
            Text(
              androidx.compose.ui.res
                .stringResource(com.quantummpv.app.R.string.ui_enter_url),
            )
          },
          placeholder = { Text("https://example.com/video.mp4") },
          singleLine = true,
          enabled = !isSubmitting,
          isError = linkInputUrl.isNotBlank() && !isLinkInputUrlValid,
          trailingIcon = {
            if (linkInputUrl.isNotBlank()) {
              ValidationIcon(isValid = isLinkInputUrlValid)
            }
          },
        )

        if (linkInputUrl.isNotBlank() && !isLinkInputUrlValid) {
          Text(
            text =
              androidx.compose.ui.res
                .stringResource(com.quantummpv.app.R.string.ui_unsupported_url_protocol),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      // Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = handleDismiss) {
          Text(
            text =
              androidx.compose.ui.res
                .stringResource(com.quantummpv.app.R.string.generic_cancel),
            fontWeight = FontWeight.Medium,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = handleConfirm,
          enabled = linkInputUrl.isNotBlank() && isLinkInputUrlValid && !isSubmitting,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
          Text(
            text =
              when {
                isSubmitting && isPlaylistInput ->
                  androidx.compose.ui.res.stringResource(com.quantummpv.app.R.string.playlist_importing)
                isPlaylistInput ->
                  androidx.compose.ui.res.stringResource(com.quantummpv.app.R.string.playlist_import_and_play)
                else -> androidx.compose.ui.res.stringResource(com.quantummpv.app.R.string.ui_play)
              },
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun ValidationIcon(isValid: Boolean) {
  if (isValid) {
    Icon(
      Icons.RoundedFilled.Check,
      contentDescription =
        androidx.compose.ui.res
          .stringResource(com.quantummpv.app.R.string.ui_valid_url),
      tint = MaterialTheme.colorScheme.primary,
    )
  } else {
    Icon(
      Icons.RoundedFilled.Close,
      contentDescription =
        androidx.compose.ui.res
          .stringResource(com.quantummpv.app.R.string.ui_invalid_url),
      tint = MaterialTheme.colorScheme.error,
    )
  }
}
