/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.utils.rememberAppHaptics

@Composable
fun IconSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: SwitchColors = SwitchDefaults.colors(),
) {
  val haptics = rememberAppHaptics()
  Switch(
    checked = checked,
    onCheckedChange =
      onCheckedChange?.let { change ->
        { value ->
          if (enabled && value != checked) {
            change(value)
            haptics.selection(value)
          }
        }
      },
    modifier = modifier,
    enabled = enabled,
    colors = colors,
    thumbContent = {
      Crossfade(
        targetState = checked,
        animationSpec = AppMotion.spatial(AppMotion.Effect.Alpha, snap()),
        label = "SwitchIconAnimation",
      ) { isChecked ->
        if (isChecked) {
          Icon(
            Icons.RoundedFilled.Check,
            contentDescription = null,
            modifier = Modifier.size(SwitchDefaults.IconSize),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        } else {
          Icon(
            Icons.RoundedFilled.Close,
            contentDescription = null,
            modifier = Modifier.size(SwitchDefaults.IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
  )
}
