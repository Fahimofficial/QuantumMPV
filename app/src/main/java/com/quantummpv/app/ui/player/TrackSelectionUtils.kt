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
  restoreSubtitleVisibility: Boolean = false,
) {
  val selectedId = id?.takeIf { it > 0 }
  if (selectedId == null) {
    PlaybackSession.setPropertyString(property, "no")
  } else {
    // Track properties accept both numeric IDs and the symbolic value "no". Use the same
    // string-property path as MPVView.TrackDelegate and older libmpv builds; mixing typed and
    // string writes caused the sheet to report a selection while the active core retained the
    // previous track on some devices.
    PlaybackSession.setPropertyString(property, selectedId.toString())
    if (restoreSubtitleVisibility && (property == "sid" || property == "secondary-sid")) {
      PlaybackSession.setPropertyBoolean("sub-visibility", true)
    }
  }
}
