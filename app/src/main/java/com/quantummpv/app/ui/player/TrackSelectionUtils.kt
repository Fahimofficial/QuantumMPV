/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import android.util.Log

/** Identifies whether a track write is user intent, automatic policy, or internal plumbing. */
enum class SubtitleSelectionSource {
  USER,
  AUTO,
  INTERNAL,
}

/** Prevents an automatic selection from overwriting a user choice in the same media generation. */
internal object SubtitleSelectionGuard {
  private var manualGeneration: Long = Long.MIN_VALUE

  @Synchronized
  fun markManual(generation: Long) {
    manualGeneration = generation
  }

  @Synchronized
  fun isManualSelection(generation: Long): Boolean = manualGeneration == generation
}

internal fun getTrackSelectionId(property: String): Int =
  runCatching {
    PlaybackSession.getPropertyInt(property)
      ?: PlaybackSession.getPropertyString(property)?.toIntOrNull()
      ?: 0
  }.getOrDefault(0)

@Synchronized
internal fun setTrackSelectionId(
  property: String,
  id: Int?,
  restoreSubtitleVisibility: Boolean = false,
  source: SubtitleSelectionSource = SubtitleSelectionSource.INTERNAL,
  generation: Long = PlaybackSession.state.value.generation,
): Boolean {
  if (source == SubtitleSelectionSource.AUTO && !PlaybackSession.isCurrentGeneration(generation)) {
    return false
  }
  if (property == "sid" || property == "secondary-sid") {
    if (source == SubtitleSelectionSource.AUTO &&
      SubtitleSelectionGuard.isManualSelection(generation)
    ) {
      return false
    }
  }

  val selectedId = id?.takeIf { it > 0 }
  if (selectedId != null) {
    if ((property == "sid" || property == "secondary-sid") && !trackExists(selectedId)) return false
    PlaybackSession.setPropertyInt(property, selectedId)
    if (getTrackSelectionId(property) != selectedId) {
      // Compatibility fallback for older/variant libmpv builds whose string bridge coerces IDs.
      PlaybackSession.setPropertyString(property, selectedId.toString())
    }
    if (getTrackSelectionId(property) != selectedId) return false

    if (source == SubtitleSelectionSource.USER &&
      (property == "sid" || property == "secondary-sid")
    ) {
      // The ID write is the authoritative selection result. Visibility may be owned by mpv.conf,
      // so record the user choice before performing the best-effort visibility restore.
      SubtitleSelectionGuard.markManual(generation)
    }
    if (restoreSubtitleVisibility && (property == "sid" || property == "secondary-sid")) {
      PlaybackSession.setPropertyBoolean("sub-visibility", true)
      if (PlaybackSession.getPropertyBoolean("sub-visibility") != true) {
        Log.w("TrackSelectionUtils", "MPV did not confirm sub-visibility=true after selecting $selectedId")
      }
    }
    return true
  }

  PlaybackSession.setPropertyString(property, "no")
  val cleared = getTrackSelectionId(property) <= 0
  if (cleared && source == SubtitleSelectionSource.USER &&
    (property == "sid" || property == "secondary-sid")
  ) {
    SubtitleSelectionGuard.markManual(generation)
  }
  return cleared
}

private fun trackExists(id: Int): Boolean {
  val count = PlaybackSession.getPropertyInt("track-list/count") ?: return false
  for (index in 0 until count) {
    if (PlaybackSession.getPropertyInt("track-list/$index/id") == id &&
      PlaybackSession.getPropertyString("track-list/$index/type") == "sub"
    ) {
      return true
    }
  }
  return false
}
