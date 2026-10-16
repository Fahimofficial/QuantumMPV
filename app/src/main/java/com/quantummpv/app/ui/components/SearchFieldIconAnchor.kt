/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

/**
 * Lets an [InlineSearchBar] take part in the floating Search button's launch transition: the bar
 * reports where its leading magnifier sits, hides that icon while the travelling icon is still on
 * its way, and fades itself in during the hand-off. Absent (null) everywhere outside MainScreen, so
 * other search bars behave exactly as before.
 */
@Stable
interface SearchFieldIconAnchor {
  /** True while the travelling icon stands in for this bar's own magnifier. */
  val hidesLeadingIcon: Boolean

  /** Opacity for the whole bar; below 1 only while it is being revealed. */
  fun fieldAlpha(): Float

  /** Centre of the bar's leading magnifier, in root coordinates. */
  fun onLeadingIconPositioned(centerInRoot: Offset)
}

val LocalSearchFieldIconAnchor = staticCompositionLocalOf<SearchFieldIconAnchor?> { null }
