/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components.sheets

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.presentation.components.PlayerSheet
import com.quantummpv.app.ui.components.themedSegmentedButtonColors
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.PlayerViewModel
import com.quantummpv.app.ui.player.TrackNode
import com.quantummpv.app.ui.player.scopes.MediaScopeTab
import com.quantummpv.app.ui.player.scopes.VideoScopeMode
import com.quantummpv.app.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ScopesSheet(
  viewModel: PlayerViewModel,
  audioTracks: ImmutableList<TrackNode>,
  onSelectAudio: (TrackNode) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val state by viewModel.mediaScopesUiState.collectAsState()
  val tabs = MediaScopeTab.entries

  PlayerSheet(onDismissRequest) {
    Column(
      modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.scopes_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = {
            viewModel.toggleMediaScopesExpanded()
            onDismissRequest()
          },
        ) {
          Icon(Icons.RoundedFilled.ZoomOutMap, stringResource(R.string.scopes_expand))
        }
        IconButton(onClick = onDismissRequest) {
          Icon(Icons.RoundedFilled.Close, stringResource(R.string.ui_close))
        }
      }

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
          SegmentedButton(
            selected = state.tab == tab,
            onClick = { viewModel.setMediaScopeTab(tab) },
            shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
            colors = themedSegmentedButtonColors(),
            label = {
              Text(
                stringResource(
                  if (tab == MediaScopeTab.Audio) R.string.pref_audio else R.string.media_info_tab_video,
                ),
              )
            },
          )
        }
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { viewModel.setMediaScopesOverlayVisible(!state.overlayVisible) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.scopes_show_overlay),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f),
        )
        Switch(
          checked = state.overlayVisible,
          onCheckedChange = viewModel::setMediaScopesOverlayVisible,
        )
      }

      when (state.tab) {
        MediaScopeTab.Audio -> {
          val selectedTrack = audioTracks.firstOrNull(TrackNode::isSelected)
          Text(
            text =
              if (selectedTrack != null) {
                getTrackTitle(selectedTrack)
              } else {
                stringResource(R.string.scopes_no_audio_track)
              },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
          ) {
            audioTracks.forEach { track ->
              FilterChip(
                selected = track.isSelected,
                onClick = {
                  onSelectAudio(track)
                  viewModel.setMediaScopesOverlayVisible(true)
                },
                label = { Text(getTrackTitle(track), maxLines = 1) },
              )
            }
          }
          Text(
            text =
              stringResource(
                R.string.scopes_channel_count,
                selectedTrack?.demuxChannelCount?.toInt()?.coerceAtLeast(1) ?: 0,
              ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        MediaScopeTab.Video -> {
          val modes = VideoScopeMode.entries
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
              SegmentedButton(
                selected = state.videoMode == mode,
                onClick = { viewModel.setVideoScopeMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                colors = themedSegmentedButtonColors(),
                label = {
                  Text(
                    stringResource(
                      when (mode) {
                        VideoScopeMode.LumaWaveform -> R.string.scopes_luma
                        VideoScopeMode.RgbyParade -> R.string.scopes_rgby
                        VideoScopeMode.Vectorscope -> R.string.scopes_vectorscope
                      },
                    ),
                  )
                },
              )
            }
          }

          ScopeOptionRow(
            label = stringResource(R.string.scopes_resolution),
            values = listOf(256, 360, 540, 720),
            selected = state.analysisResolution,
            valueLabel = { "$it px" },
            onSelect = viewModel::setMediaScopeAnalysisResolution,
          )
          ScopeOptionRow(
            label = stringResource(R.string.scopes_frame_rate),
            values = listOf(5, 10, 15, 30),
            selected = state.frameRate,
            valueLabel = { "$it fps" },
            onSelect = viewModel::setMediaScopeFrameRate,
          )
        }
      }

      TextButton(
        onClick = {
          runCatching {
            context.startActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aagedal/Aagedal-Media-Player")),
            )
          }
        },
      ) {
        Text(
          text = stringResource(R.string.scopes_credit),
          color = MaterialTheme.colorScheme.primary,
          textDecoration = TextDecoration.Underline,
        )
      }
    }
  }
}

@Composable
private fun ScopeOptionRow(
  label: String,
  values: List<Int>,
  selected: Int,
  valueLabel: (Int) -> String,
  onSelect: (Int) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.width(2.dp))
    values.forEach { value ->
      FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(valueLabel(value)) },
      )
    }
  }
}