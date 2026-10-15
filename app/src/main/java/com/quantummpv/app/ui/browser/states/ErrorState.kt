/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.states

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.AppShapeScale

/**
 * Shared failure state for browser screens. Visually related to [EmptyState] but uses the error
 * container colour and always offers a recovery action, so "failed to load" is never mistaken
 * for "nothing here". Announced politely to screen readers when it appears.
 */
@Composable
fun ErrorState(
  title: String,
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  icon: AppIcon = Icons.RoundedFilled.Warning,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 48.dp)
          .padding(bottom = 80.dp)
          .semantics { liveRegion = LiveRegionMode.Polite },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        modifier = Modifier.size(96.dp),
        shape = AppShapeScale.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.padding(24.dp),
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(24.dp))
      FilledTonalButton(onClick = onRetry) {
        Text(stringResource(R.string.error_state_retry))
      }
    }
  }
}
