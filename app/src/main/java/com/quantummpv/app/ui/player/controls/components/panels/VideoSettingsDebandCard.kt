/*
 * SPDX-License-Identifier: CC-BY-NC-4.0
 *
 * This work is licensed under Creative Commons Attribution-NonCommercial 4.0 International License.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc/4.0/
 */

package com.quantummpv.app.ui.player.controls.components.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.quantummpv.app.ui.player.DebandSettings
import com.quantummpv.app.ui.player.Debanding
import com.quantummpv.app.ui.player.controls.CARDS_MAX_WIDTH
import com.quantummpv.app.ui.player.controls.panelCardsColors
import com.quantummpv.app.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Composable
fun VideoSettingsDebandCard(modifier: Modifier = Modifier) {
  val decoderPreferences = koinInject<DecoderPreferences>()
  val deband by decoderPreferences.debanding.collectAsState()
  var isExpanded by remember { mutableStateOf(true) }

  ExpandableCard(
    isExpanded,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.RoundedFilled.Gradient, null)
        Text(stringResource(R.string.player_sheets_deband_title))
      }
    },
    onExpand = { isExpanded = !isExpanded },
    modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    ProvidePreferenceLocals {
      Column {
        Row(
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Debanding.entries.forEach {
            IconToggleButton(
              checked = deband == it,
              onCheckedChange = { _ ->
                decoderPreferences.debanding.set(it)
                when (it) {
                  Debanding.None -> {
                    MPVLib.setOptionString("deband", "no")
                    MPVLib.command("vf", "remove", "@deband")
                  }
                  Debanding.CPU -> {
                    MPVLib.setOptionString("deband", "no")
                    MPVLib.command("vf", "add", "@deband:gradfun=radius=12")
                  }
                  Debanding.GPU -> {
                    MPVLib.setOptionString("deband", "yes")
                    MPVLib.command("vf", "remove", "@deband")
                  }
                }
              },
            ) {
              when (it) {
                Debanding.None -> Icon(Icons.RoundedFilled.NotInterested, null)
                Debanding.CPU -> Icon(Icons.RoundedFilled.Memory, null)
                Debanding.GPU -> Icon(Icons.RoundedFilled.DeveloperBoard, null)
              }
            }
          }

          Text(stringResource(deband.titleRes))

          Spacer(Modifier.weight(1f))
          TextButton(
            onClick = {
              decoderPreferences.debanding.set(Debanding.None)
              MPVLib.setOptionString("deband", "no")
              MPVLib.command("vf", "remove", "@deband")
              DebandSettings.entries.forEach {
                MPVLib.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
              }
            },
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.RoundedFilled.ResetIso, null)
              Text(stringResource(R.string.generic_reset))
            }
          }
        }

        DebandSettings.entries.forEach { debandSettings ->
          val value by debandSettings.preference(decoderPreferences).collectAsState()
          SliderItem(
            label = stringResource(debandSettings.titleRes),
            value = value,
            valueText = value.toString(),
            onChange = {
              debandSettings.preference(decoderPreferences).set(it)
              MPVLib.setPropertyInt(debandSettings.mpvProperty, it)
            },
            min = debandSettings.start,
            max = debandSettings.end,
          )
        }
      }
    }
  }
}
