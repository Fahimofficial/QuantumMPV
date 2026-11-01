/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.browser.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.theme.AppShapeScale

/** A labelled action for [ServerSetupCard]. */
internal data class ServerSetupAction(
  val label: String,
  val onClick: () -> Unit,
)

/**
 * Centered card shown on the Music tab when a media-server source needs attention (no server, or no
 * music library). [primary] is the filled tonal button; [secondary] the quieter text button below.
 */
@Composable
internal fun ServerSetupCard(
  @DrawableRes icon: Int,
  title: String,
  message: String,
  primary: ServerSetupAction,
  secondary: ServerSetupAction,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      shape = AppShapeScale.extraLarge,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(
          painter = painterResource(icon),
          contentDescription = null,
          modifier = Modifier.size(56.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = primary.onClick) { Text(primary.label) }
        TextButton(onClick = secondary.onClick) { Text(secondary.label) }
      }
    }
  }
}
