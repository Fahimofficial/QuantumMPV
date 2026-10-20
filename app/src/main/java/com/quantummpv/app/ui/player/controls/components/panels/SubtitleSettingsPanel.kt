/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantummpv.app.R
import com.quantummpv.app.ui.player.PlayerViewModel
import com.quantummpv.app.ui.theme.spacing

@Composable
fun SubtitleSettingsPanel(
  viewModel: PlayerViewModel,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DraggablePanel(
    modifier = modifier,
    header = {
      PlayerPanelHeader(title = stringResource(R.string.player_sheets_subtitles_settings_title), onClose = onDismissRequest)
    },
  ) {
    Column(
      Modifier.padding(MaterialTheme.spacing.medium),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
      SubtitleSettingsTypographyCard(viewModel = viewModel)
      SubtitleSettingsColorsCard(viewModel = viewModel)
      SubtitlesMiscellaneousCard()
    }
  }
}
