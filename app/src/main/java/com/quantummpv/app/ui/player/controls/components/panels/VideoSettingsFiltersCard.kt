/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components.panels

import com.quantummpv.app.ui.player.PlaybackSession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantummpv.app.R
import com.quantummpv.app.preferences.DecoderPreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.preferences.preference.deleteAndGet
import com.quantummpv.app.presentation.components.ExpandableCard
import com.quantummpv.app.presentation.components.SliderItem
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.VideoFilters
import com.quantummpv.app.ui.player.controls.CARDS_MAX_WIDTH
import com.quantummpv.app.ui.player.controls.panelCardsColors
import com.quantummpv.app.ui.theme.spacing
import com.quantummpv.app.ui.utils.currentMpvConfigOverrideOptions
import me.zhanghai.compose.preference.FooterPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Composable
fun VideoSettingsFiltersCard(modifier: Modifier = Modifier) {
  val decoderPreferences = koinInject<DecoderPreferences>()
  val configOwnedOptions = currentMpvConfigOverrideOptions()
  val hasOwnedFilter = VideoFilters.entries.any { it.mpvProperty in configOwnedOptions }
  var isExpanded by remember { mutableStateOf(true) }

  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Icon(Icons.RoundedFilled.Tune, null)
        Text(stringResource(R.string.player_sheets_filters_title))
      }
    },
    colors = panelCardsColors(),
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
  ) {
    ProvidePreferenceLocals {
      Column {
        TextButton(
          enabled = !hasOwnedFilter,
          onClick = {
            VideoFilters.entries.forEach {
              PlaybackSession.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
            }
          },
        ) {
          Text(text = stringResource(id = R.string.generic_reset))
        }

        VideoFilters.entries.forEach { filter ->
          val value by filter.preference(decoderPreferences).collectAsState()
          SliderItem(
            label = stringResource(filter.titleRes),
            value = value,
            valueText = value.toString(),
            onChange = {
              filter.preference(decoderPreferences).set(it)
              PlaybackSession.setPropertyInt(filter.mpvProperty, it)
            },
            max = filter.max,
            min = filter.min,
            enabled = filter.mpvProperty !in configOwnedOptions,
          )
        }

        if (!decoderPreferences.gpuNext.get()) {
          FooterPreference(
            summary = {
              Text(text = stringResource(id = R.string.player_sheets_filters_warning))
            },
          )
        }
      }
    }
  }
}
