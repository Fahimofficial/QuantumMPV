/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.quantummpv.app.R
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.IconSize
import com.quantummpv.app.ui.theme.spacing

/**
 * Header shared by every player panel: a heading title and a labelled close action. The close icon
 * is drawn at [IconSize.Standard] inside the IconButton's 48 dp target, matching sheet actions.
 */
@Composable
fun PlayerPanelHeader(
  title: String,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
    modifier =
      modifier
        .fillMaxWidth()
        .padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.extraSmall)
        .padding(top = MaterialTheme.spacing.extraSmall),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f).semantics { heading() },
    )
    IconButton(onClick = onClose) {
      Icon(
        Icons.RoundedFilled.Close,
        contentDescription = stringResource(R.string.ui_close),
        modifier = Modifier.size(IconSize.Standard),
      )
    }
  }
}
