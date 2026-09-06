/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun AddXtreamPlaylistDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onImported: () -> Unit,
  onCreateXtreamPlaylist: suspend (String, String, String) -> Result<Long>,
) {
  if (!isOpen) return

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var serverUrl by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  var submitted by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val parsedServerUrl = serverUrl.trim().toHttpUrlOrNull()
  val isServerUrlValid =
    parsedServerUrl != null &&
      parsedServerUrl.scheme in setOf("http", "https") &&
      parsedServerUrl.username.isEmpty() &&
      parsedServerUrl.password.isEmpty() &&
      parsedServerUrl.query == null &&
      parsedServerUrl.fragment == null
  val showServerUrlError = !isServerUrlValid && (submitted || serverUrl.isNotBlank())
  val canSubmit = isServerUrlValid && username.isNotBlank() && password.isNotBlank() && !isLoading

  val submit = {
    submitted = true
    errorMessage = null
    if (canSubmit) {
      isLoading = true
      coroutineScope.launch {
        onCreateXtreamPlaylist(serverUrl.trim(), username, password)
          .onSuccess {
            Toast
              .makeText(context, context.getString(R.string.playlist_xtream_import_success), Toast.LENGTH_SHORT)
              .show()
            password = ""
            onImported()
          }.onFailure { error ->
            errorMessage = error.message ?: context.getString(R.string.generic_unknown_error)
          }
        isLoading = false
      }
    }
  }

  Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
    Card(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      shape = MaterialTheme.shapes.extraLarge,
    ) {
      Column(
        modifier =
          Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = stringResource(R.string.playlist_xtream_add_title),
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = stringResource(R.string.playlist_xtream_add_description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = serverUrl,
          onValueChange = { serverUrl = it },
          label = { Text(stringResource(R.string.playlist_xtream_server_url)) },
          placeholder = { Text(stringResource(R.string.playlist_xtream_server_placeholder)) },
          singleLine = true,
          isError = showServerUrlError,
          enabled = !isLoading,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text(stringResource(R.string.playlist_xtream_username)) },
          singleLine = true,
          isError = submitted && username.isBlank(),
          enabled = !isLoading,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(stringResource(R.string.playlist_xtream_password)) },
          singleLine = true,
          isError = submitted && password.isBlank(),
          enabled = !isLoading,
          visualTransformation =
            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { submit() }),
          trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading) {
              Icon(
                imageVector =
                  if (passwordVisible) Icons.RoundedFilled.VisibilityOff else Icons.RoundedFilled.Visibility,
                contentDescription =
                  stringResource(
                    if (passwordVisible) {
                      R.string.playlist_xtream_hide_password
                    } else {
                      R.string.playlist_xtream_show_password
                    },
                  ),
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )

        if (showServerUrlError) {
          Text(
            text = stringResource(R.string.playlist_xtream_url_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        errorMessage?.let { message ->
          Text(
            text = stringResource(R.string.playlist_xtream_import_error, message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        if (isLoading) {
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss, enabled = !isLoading) {
            Text(stringResource(R.string.generic_cancel))
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(onClick = submit, enabled = canSubmit) {
            Text(stringResource(R.string.playlist_xtream_connect))
          }
        }
      }
    }
  }
}
