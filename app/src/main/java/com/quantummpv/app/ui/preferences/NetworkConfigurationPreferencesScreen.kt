/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.preferences.components.SwitchPreference
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.LocalShowSettingsBackArrow
import com.quantummpv.app.ui.utils.navigateTo
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Serializable
object NetworkConfigurationPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val enableP2pStreaming by preferences.enableP2pStreaming.collectAsState()
    val enableHlsProxy by preferences.enableHlsProxy.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.ui_network)) },
          navigationIcon = {
            if (LocalShowSettingsBackArrow.current) {
              IconButton(onClick = { backStack.popSafely() }) {
                Icon(Icons.RoundedFilled.ArrowBack, contentDescription = null)
              }
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val (settingsListState, settingsHighlight) =
          rememberSettingsSearchList(NetworkConfigurationPreferencesScreen, MaterialTheme.colorScheme.primary)
        LazyColumn(
          state = settingsListState,
          modifier = Modifier.fillMaxSize().padding(padding).then(settingsHighlight),
        ) {
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_section_p2p_streaming))
          }
          item {
            PreferenceCard {
              SwitchPreference(
                modifier = Modifier.settingsSearchTarget(R.string.pref_enable_p2p_streaming_title),
                value = enableP2pStreaming,
                onValueChange = preferences.enableP2pStreaming::set,
                title = { Text(stringResource(R.string.pref_enable_p2p_streaming_title)) },
                summary = { Text(stringResource(R.string.pref_enable_p2p_streaming_summary), color = MaterialTheme.colorScheme.outline) },
              )
              PreferenceDivider()
              SwitchPreference(
                modifier = Modifier.settingsSearchTarget(R.string.pref_enable_hls_proxy_title),
                value = enableHlsProxy,
                onValueChange = preferences.enableHlsProxy::set,
                title = { Text(stringResource(R.string.pref_enable_hls_proxy_title)) },
                summary = { Text(stringResource(R.string.pref_enable_hls_proxy_summary), color = MaterialTheme.colorScheme.outline) },
              )
            }
          }
          item {
            PreferenceSectionHeader(title = stringResource(R.string.ui_yt_dlp_manager))
          }
          item {
            PreferenceCard {
              Preference(
                modifier = Modifier.settingsSearchTarget(R.string.ui_yt_dlp_manager),
                title = { Text(stringResource(R.string.ui_yt_dlp_manager)) },
                summary = { Text(stringResource(R.string.ui_install_and_update_yt_dlp_for_streaming_support), color = MaterialTheme.colorScheme.outline) },
                icon = { Icon(Icons.RoundedFilled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { backStack.navigateTo(YtdlpSettingsScreen) },
              )
            }
          }
        }
      }
    }
  }
}
