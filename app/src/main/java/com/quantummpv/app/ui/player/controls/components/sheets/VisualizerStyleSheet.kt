/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AudioVisualizerStyle
import com.quantummpv.app.presentation.components.PlayerSheet

@Composable
fun VisualizerStyleSheet(
  selectedStyle: AudioVisualizerStyle,
  onSelectStyle: (AudioVisualizerStyle) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  PlayerSheet(onDismissRequest, title = stringResource(R.string.pref_audio_visualizer_style_title)) {
    LazyColumn(
      modifier = modifier.fillMaxWidth(),
      contentPadding = PaddingValues(bottom = 8.dp),
    ) {
      items(AudioVisualizerStyle.entries, key = { it.name }) { style ->
        AudioTrackRow(
          title = stringResource(style.title),
          isSelected = selectedStyle == style,
          onClick = {
            onSelectStyle(style)
            onDismissRequest()
          },
        )
      }
    }
  }
}
