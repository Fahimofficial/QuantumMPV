/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.PlaybackErrorKind
import com.quantummpv.app.ui.player.PlaybackErrorUi
import com.quantummpv.app.ui.theme.AppShapeScale

/**
 * Explains why playback failed and offers the actions that can actually help, instead of a
 * generic toast. Always uses dark colours because it is drawn over video.
 */
@Composable
fun PlaybackErrorCard(
  error: PlaybackErrorUi,
  onRetry: () -> Unit,
  onSoftwareDecoding: () -> Unit,
  onOpenExternally: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showDetail by rememberSaveable(error) { mutableStateOf(false) }
  val (title, body) =
    when (error.kind) {
      PlaybackErrorKind.FILE_UNAVAILABLE -> R.string.playback_error_file_title to R.string.playback_error_file_body
      PlaybackErrorKind.NETWORK -> R.string.playback_error_network_title to R.string.playback_error_network_body
      PlaybackErrorKind.UNSUPPORTED_FORMAT -> R.string.playback_error_format_title to R.string.playback_error_format_body
      PlaybackErrorKind.DECODER -> R.string.playback_error_decoder_title to R.string.playback_error_decoder_body
      PlaybackErrorKind.TIMEOUT -> R.string.playback_error_timeout_title to R.string.playback_error_timeout_body
      PlaybackErrorKind.UNKNOWN -> R.string.playback_error_unknown_title to R.string.playback_error_unknown_body
    }

  MaterialTheme(colorScheme = darkColorScheme()) {
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
      Surface(
        shape = AppShapeScale.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
      ) {
        Column(
          modifier = Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            Icons.RoundedFilled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
          )
          Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
          Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (showDetail && error.detail != null) {
            Text(
              error.detail,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
          ) {
            if (error.detail != null && !showDetail) {
              TextButton(onClick = { showDetail = true }) { Text(stringResource(R.string.playback_error_details)) }
            }
            if (error.canOpenExternally) {
              TextButton(onClick = onOpenExternally) { Text(stringResource(R.string.playback_error_open_with)) }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.generic_close)) }
            if (error.canTrySoftwareDecoding) {
              FilledTonalButton(onClick = onSoftwareDecoding) { Text(stringResource(R.string.playback_error_software_decoding)) }
            } else if (error.canRetry) {
              FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.playback_error_retry)) }
            }
          }
        }
      }
    }
  }
}

/** Black scrim drawn behind the card so the frozen frame does not compete with the message. */
val PlaybackErrorScrim = Color.Black.copy(alpha = 0.6f)
