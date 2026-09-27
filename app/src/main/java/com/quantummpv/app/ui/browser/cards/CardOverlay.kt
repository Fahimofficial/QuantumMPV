/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.cards

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.theme.AppShapeScale

internal fun Modifier.cardOverlay(
  shape: Shape = AppShapeScale.extraSmall,
  containerColor: Color = Color.Black.copy(alpha = 0.72f),
): Modifier =
  shadow(elevation = 3.dp, shape = shape, clip = false)
    .clip(shape)
    .background(containerColor)
