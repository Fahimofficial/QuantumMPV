/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

internal fun getTrackSelectionId(property: String): Int =
  runCatching {
    PlaybackSession.getPropertyInt(property)
      ?: PlaybackSession.getPropertyString(property)?.toIntOrNull()
      ?: 0
  }.getOrDefault(0)

internal fun setTrackSelectionId(
  property: String,
  id: Int?,
) {
  val selectedId = id?.takeIf { it > 0 }
  if (selectedId == null) {
    PlaybackSession.setPropertyString(property, "no")
  } else {
    // sid/secondary-sid/aid are integer MPV properties. Use the typed setter so selection is
    // applied reliably across libmpv versions instead of relying on string coercion.
    PlaybackSession.setPropertyInt(property, selectedId)
  }
}
