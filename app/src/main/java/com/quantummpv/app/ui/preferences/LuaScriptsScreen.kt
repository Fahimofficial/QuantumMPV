/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.preferences

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.lua.LuaRuntimeStatusCard
import com.quantummpv.app.ui.lua.LuaScriptToggleCard
import com.quantummpv.app.ui.lua.LuaScriptsEmptyState
import com.quantummpv.app.ui.lua.LuaScriptsLoadingState
import com.quantummpv.app.ui.lua.LuaSelectionFootnote
import com.quantummpv.app.ui.lua.rememberLuaScriptsCatalog
import com.quantummpv.app.ui.theme.spacing
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.navigateTo
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object LuaScriptsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
    val selectedScripts by preferences.selectedLuaScripts.collectAsState()
    val enableLuaScripts by preferences.enableLuaScripts.collectAsState()
    val catalog =
      rememberLuaScriptsCatalog(
        storageUri = mpvConfStorageLocation,
        selectedScripts = selectedScripts,
        onSelectionPruned = preferences.selectedLuaScripts::set,
      )
    val enabledScriptsCount = selectedScripts.count { it in catalog.availableScripts }
    val scriptsListEnabled = enableLuaScripts && mpvConfStorageLocation.isNotBlank()

    fun toggleScriptSelection(scriptName: String) {
      val isEnabled = selectedScripts.contains(scriptName)
      val newSelection =
        if (isEnabled) {
          Toast
            .makeText(
              context,
              "$scriptName disabled. Reopen the video if the script stays active.",
              Toast.LENGTH_LONG,
            ).show()
          selectedScripts - scriptName
        } else {
          selectedScripts + scriptName
        }
      preferences.selectedLuaScripts.set(newSelection)
    }

    fun shareScript(scriptName: String) {
      if (mpvConfStorageLocation.isBlank()) {
        Toast
          .makeText(
            context,
            stringResource(com.quantummpv.app.R.string.ui_no_storage_location_configured),
            Toast.LENGTH_SHORT,
          ).show()
        return
      }

      runCatching {
        val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
        if (tree != null && tree.exists()) {
          val scriptsDir =
            tree.listFiles().firstOrNull {
              it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
            } ?: tree

          val scriptFile =
            scriptsDir.listFiles().firstOrNull {
              it.isFile && it.name == scriptName
            }

          if (scriptFile != null) {
            val cacheFile = File(context.cacheDir, scriptName)
            context.contentResolver.openInputStream(scriptFile.uri)?.use { input ->
              cacheFile.outputStream().use { output ->
                input.copyTo(output)
              }
            }

            val shareUri =
              FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheFile,
              )

            val shareIntent =
              Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, scriptName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }

            context.startActivity(Intent.createChooser(shareIntent, "Share script"))
          } else {
            Toast
              .makeText(
                context,
                stringResource(com.quantummpv.app.R.string.ui_script_file_not_found),
                Toast.LENGTH_SHORT,
              ).show()
          }
        }
      }.onFailure { error ->
        Toast
          .makeText(
            context,
            stringResource(
              R.string.toast_error_sharing_script,
              error.message ?: stringResource(R.string.generic_unknown_error),
            ),
            Toast.LENGTH_LONG,
          ).show()
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text =
                androidx.compose.ui.res
                  .stringResource(com.quantummpv.app.R.string.pref_section_scripts),
              style = MaterialTheme.typography.headlineSmall,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) {
              Icon(
                imageVector = Icons.RoundedFilled.ArrowBack,
                contentDescription =
                  androidx.compose.ui.res
                    .stringResource(com.quantummpv.app.R.string.back),
              )
            }
          },
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = { backStack.navigateTo(LuaScriptEditorScreen(scriptName = null)) },
          containerColor = MaterialTheme.colorScheme.primary,
        ) {
          Icon(
            imageVector = Icons.RoundedFilled.Add,
            contentDescription =
              androidx.compose.ui.res.stringResource(
                com.quantummpv.app.R.string.ui_create_new_script,
              ),
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      },
    ) { padding ->
      LazyColumn(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding =
          PaddingValues(
            start = MaterialTheme.spacing.medium,
            top = MaterialTheme.spacing.medium,
            end = MaterialTheme.spacing.medium,
            // Extra clearance so the FAB doesn't overlap the last item
            bottom = MaterialTheme.spacing.medium + 80.dp,
          ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        item {
          LuaRuntimeStatusCard(
            enabled = enableLuaScripts,
            hasStorageLocation = mpvConfStorageLocation.isNotBlank(),
            enabledScriptsCount = enabledScriptsCount,
            availableScriptsCount = catalog.availableScripts.size,
            onEnabledChange = preferences.enableLuaScripts::set,
          )
        }

        when {
          catalog.isLoading -> {
            item {
              LuaScriptsLoadingState()
            }
          }
          mpvConfStorageLocation.isBlank() -> {
            item {
              LuaScriptsEmptyState(
                title = stringResource(R.string.lua_no_mpv_folder),
                summary = "Choose an MPV config folder in Advanced settings to browse and manage scripts.",
              )
            }
          }
          catalog.availableScripts.isEmpty() -> {
            item {
              LuaScriptsEmptyState(
                title = stringResource(R.string.lua_no_scripts_found),
                summary = "Put your .lua or .js files inside the MPV scripts folder to manage them here.",
              )
            }
          }
          else -> {
            items(catalog.availableScripts, key = { it }) { scriptName ->
              LuaScriptToggleCard(
                scriptName = scriptName,
                selected = selectedScripts.contains(scriptName),
                controlsEnabled = scriptsListEnabled,
                onToggle = { toggleScriptSelection(scriptName) },
                trailingContent = {
                  IconButton(onClick = { shareScript(scriptName) }) {
                    Icon(
                      imageVector = Icons.RoundedFilled.Share,
                      contentDescription =
                        androidx.compose.ui.res.stringResource(
                          com.quantummpv.app.R.string.generic_share,
                        ),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(onClick = { backStack.navigateTo(LuaScriptEditorScreen(scriptName = scriptName)) }) {
                    Icon(
                      imageVector = Icons.RoundedFilled.Edit,
                      contentDescription =
                        androidx.compose.ui.res
                          .stringResource(com.quantummpv.app.R.string.ui_edit),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                },
              )
            }
          }
        }

        item {
          Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
          ) {
            LuaSelectionFootnote()
          }
        }
      }
    }
  }
}
