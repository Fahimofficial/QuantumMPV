/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.LocalPlayerButtonsClickEvent
import com.quantummpv.app.ui.theme.spacing
import org.koin.compose.koinInject

@Suppress("CompositionLocalAllowlist")
internal val LocalForceDarkPlayerButtonsBackground = staticCompositionLocalOf { false }

@Composable
internal fun playerButtonContainerColor(
  forceDark: Boolean = LocalForceDarkPlayerButtonsBackground.current,
): Color =
  if (forceDark) {
    Color.Black.copy(alpha = 0.72f)
  } else {
    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
  }

@Composable
internal fun playerButtonContentColor(
  forceDark: Boolean = LocalForceDarkPlayerButtonsBackground.current,
): Color = if (forceDark) Color.White else MaterialTheme.colorScheme.onSurface

@Composable
internal fun playerButtonBorderColor(
  forceDark: Boolean = LocalForceDarkPlayerButtonsBackground.current,
): Color =
  if (forceDark) {
    Color.White.copy(alpha = 0.20f)
  } else {
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
  }

@Suppress("ModifierClickableOrder")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsButton(
  icon: AppIcon,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: () -> Unit = {},
  title: String? = null,
  color: Color? = null,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val appearancePreferences = koinInject<AppearancePreferences>()
  val hideBackground by appearancePreferences.hidePlayerButtonsBackground.collectAsState()
  val forceDarkBackground by appearancePreferences.forceDarkPlayerButtonsBackground.collectAsState()
  val useDarkBackground = forceDarkBackground || LocalForceDarkPlayerButtonsBackground.current
  val resolvedColor =
    if (useDarkBackground && !hideBackground) {
      playerButtonContentColor(forceDark = true)
    } else {
      color ?: playerButtonContentColor(useDarkBackground)
    }

  val clickEvent = LocalPlayerButtonsClickEvent.current
  Surface(
    modifier =
      modifier
        .clip(CircleShape)
        .combinedClickable(
          enabled = enabled,
          onClick = {
            clickEvent()
            onClick()
          },
          onLongClick = onLongClick,
          interactionSource = interactionSource,
          indication = ripple(),
        ),
    shape = CircleShape,
    color = if (hideBackground) Color.Transparent else playerButtonContainerColor(useDarkBackground),
    contentColor = resolvedColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border =
      if (hideBackground) {
        null
      } else {
        BorderStroke(
          1.dp,
          playerButtonBorderColor(useDarkBackground),
        )
      },
  ) {
    Icon(
      imageVector = icon,
      contentDescription = title,
      tint = if (enabled) resolvedColor else resolvedColor.copy(alpha = 0.38f),
      modifier =
        Modifier
          .padding(MaterialTheme.spacing.small)
          .size(20.dp),
    )
  }
}

@Composable
fun ControlsGroup(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  val spacing = MaterialTheme.spacing

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement =
      androidx.compose.foundation.layout.Arrangement
        .spacedBy(spacing.extraSmall),
    content = content,
  )
}

@Preview
@Composable
private fun PreviewControlsButton() {
  ControlsButton(
    Icons.RoundedFilled.CatchingPokemon,
    onClick = {},
  )
}
