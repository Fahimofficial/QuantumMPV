/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.preferences

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quantummpv.app.R
import com.quantummpv.app.domain.jellyfin.JellyfinServer
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.jellyfin.AddJellyfinServerDialog
import com.quantummpv.app.ui.browser.jellyfin.JellyfinViewModel
import com.quantummpv.app.ui.browser.jellyfin.ManageJellyfinServersDialog
import com.quantummpv.app.ui.browser.jellyfin.seerr.SeerrConnectionDialog
import com.quantummpv.app.ui.browser.jellyfin.seerr.SeerrViewModel
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.LocalShowSettingsBackArrow
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Serializable
object MediaServersPreferencesScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val jellyfinViewModel: JellyfinViewModel =
      viewModel(factory = JellyfinViewModel.factory(context.applicationContext as Application))
    val jellyfinUiState by jellyfinViewModel.uiState.collectAsStateWithLifecycle()

    val seerrViewModel: SeerrViewModel =
      viewModel(factory = SeerrViewModel.factory(context.applicationContext as Application))
    val seerrUiState by seerrViewModel.uiState.collectAsStateWithLifecycle()

    var isManageServersOpen by remember { mutableStateOf(false) }
    var isAddServerOpen by remember { mutableStateOf(false) }
    var serverToReauth by remember { mutableStateOf<JellyfinServer?>(null) }
    var isSeerrConnectionDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_media_servers_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            if (LocalShowSettingsBackArrow.current) {
              IconButton(onClick = { backStack.popSafely() }) {
                Icon(
                  Icons.RoundedFilled.ArrowBack,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val (settingsListState, settingsHighlight) =
          rememberSettingsSearchList(MediaServersPreferencesScreen, MaterialTheme.colorScheme.primary)

        LazyColumn(
          state = settingsListState,
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding)
              .then(settingsHighlight),
        ) {
          // --- JELLYFIN SECTION ---
          item {
            PreferenceSectionHeader(
              title = stringResource(R.string.pref_jellyfin_title),
              modifier = Modifier.settingsSearchTarget(R.string.pref_media_servers_title),
            )
          }

          item {
            val activeServer = jellyfinUiState.activeServer
            val serverDesc =
              if (activeServer != null) {
                "${activeServer.name} (${activeServer.serverUrl})"
              } else {
                stringResource(R.string.pref_jellyfin_no_server)
              }

            PreferenceCard {
              Preference(
                modifier = Modifier.settingsSearchTarget(R.string.pref_jellyfin_server_management),
                title = { Text(stringResource(R.string.pref_jellyfin_server_management)) },
                summary = {
                  Text(
                    text = serverDesc,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    painter = painterResource(R.drawable.ic_jellyfin),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                  )
                },
                onClick = { isManageServersOpen = true },
              )
            }
          }

          // --- SEERR SECTION ---
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_seerr_title))
          }

          item {
            val connectionDesc =
              if (seerrUiState.isConnected) {
                val userText =
                  seerrUiState.currentUser?.displayName
                    ?: seerrUiState.currentUser?.username
                    ?: seerrUiState.currentUser?.email
                    ?: "Connected"
                stringResource(R.string.pref_seerr_connected_as, userText)
              } else {
                stringResource(R.string.pref_seerr_not_connected)
              }

            PreferenceCard {
              Preference(
                modifier = Modifier.settingsSearchTarget(R.string.pref_seerr_server_management),
                title = { Text(stringResource(R.string.pref_seerr_server_management)) },
                summary = {
                  Text(
                    text = connectionDesc,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.RoundedFilled.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { isSeerrConnectionDialogOpen = true },
              )

              if (seerrUiState.isConnected) {
                PreferenceDivider()
                Preference(
                  title = { Text(stringResource(R.string.pref_seerr_disconnect)) },
                  summary = {
                    Text(
                      text = seerrUiState.serverUrl,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                  icon = {
                    Icon(
                      Icons.RoundedFilled.CloudOff,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error,
                    )
                  },
                  onClick = { seerrViewModel.disconnect() },
                )
              }
            }
          }
        }
      }
    }

    // Manage Jellyfin Servers Dialog
    ManageJellyfinServersDialog(
      isOpen = isManageServersOpen,
      servers = jellyfinUiState.servers,
      activeServer = jellyfinUiState.activeServer,
      onDismiss = { isManageServersOpen = false },
      onSelectServer = { server ->
        jellyfinViewModel.selectServer(server)
      },
      onDeleteServer = { server ->
        jellyfinViewModel.deleteServer(server)
      },
      onAddServerClick = {
        isAddServerOpen = true
      },
    )

    // Add / Re-authenticate Jellyfin Server Dialog
    AddJellyfinServerDialog(
      isOpen = isAddServerOpen,
      isLoading = jellyfinUiState.isAuthenticating,
      errorMessage = jellyfinUiState.authError,
      initialServer = serverToReauth,
      onDismiss = {
        isAddServerOpen = false
        serverToReauth = null
      },
      onConnect = { serverUrl, serverName, authMode, username, password, token ->
        val existingId = serverToReauth?.id
        jellyfinViewModel.addServer(
          serverUrl = serverUrl,
          serverName = serverName,
          authMode = authMode,
          username = username,
          password = password,
          token = token,
          existingServerId = existingId,
          onSuccess = {
            isAddServerOpen = false
            serverToReauth = null
          },
        )
      },
    )

    // Seerr Connection Dialog
    SeerrConnectionDialog(
      isOpen = isSeerrConnectionDialogOpen,
      isConnected = seerrUiState.isConnected,
      currentUser = seerrUiState.currentUser,
      currentServerUrl = seerrUiState.serverUrl,
      currentApiKey = seerrUiState.apiKey,
      activeJellyfinServer = jellyfinUiState.activeServer,
      isConnecting = seerrUiState.isConnecting,
      errorMessage = seerrUiState.connectionError,
      onDismiss = { isSeerrConnectionDialogOpen = false },
      onConnectWithCredentials = { url, user, pass, useJellyfin ->
        seerrViewModel.connectWithCredentials(url, user, pass, useJellyfin)
      },
      onConnectWithApiKey = { url, apiKey ->
        seerrViewModel.connectWithApiKey(url, apiKey)
      },
      onDisconnect = {
        seerrViewModel.disconnect()
      },
    )
  }
}
