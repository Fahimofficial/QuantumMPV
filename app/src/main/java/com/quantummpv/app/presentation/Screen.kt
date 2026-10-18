/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.presentation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

interface Screen : NavKey {
  @Composable
  fun Content()
}

/** Marks screens that need the full window height (e.g. search with the keyboard up). */
interface HidesMiniPlayer
