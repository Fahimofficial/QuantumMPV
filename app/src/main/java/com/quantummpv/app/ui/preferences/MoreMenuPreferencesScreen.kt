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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.MoreMenuShortcut
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.moreMenuShortcutIcon
import com.quantummpv.app.ui.browser.moreMenuShortcutLabel
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.preferences.components.SwitchPreference
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.LocalShowSettingsBackArrow
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

/** Settings → Appearance → Customize More menu: picks the optional rows of the dock's ⋮ menu. */
@Serializable
object MoreMenuPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()
    val enabledNames by preferences.moreMenuShortcuts.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.pref_more_menu_title)) },
          navigationIcon = {
            if (LocalShowSettingsBackArrow.current) {
              IconButton(onClick = { backStack.popSafely() }) {
                Icon(Icons.RoundedFilled.ArrowBack, contentDescription = stringResource(R.string.back))
              }
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
          item {
            PreferenceCard {
              // Settings and Support are fixed rows: shown, but their switches cannot be turned off.
              LockedRow(Icons.RoundedFilled.Settings, stringResource(R.string.ui_settings))
              PreferenceDivider()
              LockedRow(Icons.RoundedFilled.Favorite, stringResource(R.string.more_menu_support))
            }
          }

          item { PreferenceSectionHeader(title = stringResource(R.string.pref_more_menu_optional_header)) }

          item {
            PreferenceCard {
              MoreMenuShortcut.entries.forEachIndexed { index, shortcut ->
                if (index > 0) PreferenceDivider()
                SwitchPreference(
                  value = shortcut.name in enabledNames,
                  onValueChange = { enabled ->
                    // Re-read so two quick toggles never overwrite each other with a stale set.
                    val current = preferences.moreMenuShortcuts.get()
                    preferences.moreMenuShortcuts.set(if (enabled) current + shortcut.name else current - shortcut.name)
                  },
                  title = { Text(moreMenuShortcutLabel(shortcut)) },
                  icon = { ShortcutIcon(moreMenuShortcutIcon(shortcut)) },
                )
              }
            }
          }

          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_more_menu_reset)) },
                summary = {
                  Text(stringResource(R.string.pref_more_menu_reset_summary), color = MaterialTheme.colorScheme.outline)
                },
                icon = { ShortcutIcon(Icons.RoundedFilled.Refresh) },
                onClick = { preferences.moreMenuShortcuts.delete() },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LockedRow(
  icon: AppIcon,
  label: String,
) {
  SwitchPreference(
    value = true,
    onValueChange = { },
    enabled = false,
    title = { Text(label) },
    summary = { Text(stringResource(R.string.pref_more_menu_fixed_summary), color = MaterialTheme.colorScheme.outline) },
    icon = { ShortcutIcon(icon) },
  )
}

@Composable
private fun ShortcutIcon(icon: AppIcon) {
  Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}
