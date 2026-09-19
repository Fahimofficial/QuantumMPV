/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.quantummpv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Stable, lightweight glass treatment used only by the optional bottom navigation. */
@Composable
fun glassContainerColor(base: Color): Color = base.copy(alpha = 0.78f)

@Composable
fun glassBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
