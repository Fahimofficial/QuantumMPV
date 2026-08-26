/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.controls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.quantummpv.app.ui.player.controls.components.LocalForceDarkPlayerButtonsBackground

/**
 * Applies a fixed dark palette to the complete player-button subtree.
 *
 * A number of player controls are compound buttons rather than [ControlsButton] instances. Those
 * controls still read Material surface/content colors directly, so only styling ControlsButton
 * leaves titles, expanded frame controls, A-B loop controls, cast/chapter surfaces and other
 * compound controls inconsistent in light themes. Keeping the override scoped to button groups
 * makes the preference comprehensive without turning the rest of the player UI into a dark theme.
 *
 * The palette is intentionally theme-independent: Light/System/Dark app themes cannot recolor the
 * player-button surfaces or make their icons dark. Enabled content stays light and disabled states
 * are derived from that same light content via alpha.
 */
@Composable
internal fun PlayerButtonTheme(
  hideBackground: Boolean,
  content: @Composable () -> Unit,
) {
  if (hideBackground || !LocalForceDarkPlayerButtonsBackground.current) {
    content()
    return
  }

  val colors = MaterialTheme.colorScheme
  val darkSurface = Color.Black
  val lightContent = Color.White

  MaterialTheme(
    colorScheme =
      colors.copy(
        surface = darkSurface,
        surfaceVariant = darkSurface,
        surfaceContainerLowest = darkSurface,
        surfaceContainerLow = darkSurface,
        surfaceContainer = darkSurface,
        surfaceContainerHigh = darkSurface,
        surfaceContainerHighest = darkSurface,
        onSurface = lightContent,
        onSurfaceVariant = lightContent.copy(alpha = 0.82f),
        outline = lightContent.copy(alpha = 0.30f),
        outlineVariant = lightContent.copy(alpha = 0.20f),
        primary = lightContent,
        onPrimary = darkSurface,
        primaryContainer = darkSurface,
        onPrimaryContainer = lightContent,
        secondary = lightContent,
        onSecondary = darkSurface,
        secondaryContainer = darkSurface,
        onSecondaryContainer = lightContent,
        tertiary = lightContent,
        onTertiary = darkSurface,
        tertiaryContainer = darkSurface,
        onTertiaryContainer = lightContent,
      ),
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    content = content,
  )
}
