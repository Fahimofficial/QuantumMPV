/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.ytdlp

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quantummpv.app.R

/**
 * Nightly yt-dlp builds are unreviewed snapshots of the main branch that run with this app's
 * permissions, so switching to them is an explicit, informed choice.
 */
@Composable
fun YtdlpNightlyConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.ytdlp_nightly_confirm_title)) },
    text = { Text(stringResource(R.string.ytdlp_nightly_confirm_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(stringResource(R.string.ui_switch_to_nightly)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
    },
  )
}
