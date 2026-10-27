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

  /**
   * The choice the user made last time for this file, restored from saved state. Automatic
   * policies may not replace it; a new user choice can. Never forces subtitles visible.
   */
  RESTORED,
}

/**
 * Per-generation subtitle ownership: prevents an automatic selection from overwriting a user (or
 * restored) choice, and keeps a restored visibility from being flipped back on by automation.
 */
internal object SubtitleSelectionGuard {
  private var manualGeneration: Long = Long.MIN_VALUE
  private var restoredGeneration: Long = Long.MIN_VALUE
  private var visibilityGeneration: Long = Long.MIN_VALUE

  /** An explicit choice (the user, or an external app's "enable this subtitle") was applied. */
  @Synchronized
  fun markManual(generation: Long) {
    manualGeneration = generation
  }

  @Synchronized
  fun isManualSelection(generation: Long): Boolean = manualGeneration == generation

  /** The saved choice for this file was applied. */
  @Synchronized
  fun markRestored(generation: Long) {
    restoredGeneration = generation
  }

  /** Automatic policies may not replace an explicit or restored choice. */
  @Synchronized
  fun blocksAutomaticSelection(generation: Long): Boolean = manualGeneration == generation || restoredGeneration == generation

  /** The saved visibility for this file was applied; only the user may change it from now on. */
  @Synchronized
  fun markVisibilityRestored(generation: Long) {
    visibilityGeneration = generation
  }

  @Synchronized
  fun isVisibilityRestored(generation: Long): Boolean = visibilityGeneration == generation
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
    if (source == SubtitleSelectionSource.AUTO && SubtitleSelectionGuard.blocksAutomaticSelection(generation)) {
      return false
    }
    // A choice made for this launch (by the user, or an app that asked to enable a subtitle)
    // outranks what was saved last time.
    if (source == SubtitleSelectionSource.RESTORED && SubtitleSelectionGuard.isManualSelection(generation)) {
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

    if (property == "sid" || property == "secondary-sid") {
      // The ID write is the authoritative selection result. Visibility may be owned by mpv.conf,
      // so record the choice before performing the best-effort visibility restore.
      when (source) {
        SubtitleSelectionSource.USER -> SubtitleSelectionGuard.markManual(generation)
        SubtitleSelectionSource.RESTORED -> SubtitleSelectionGuard.markRestored(generation)
        else -> Unit
      }
    }
    val mayShowSubtitles =
      when (source) {
        SubtitleSelectionSource.USER -> true
        SubtitleSelectionSource.RESTORED -> false
        else -> !SubtitleSelectionGuard.isVisibilityRestored(generation)
      }
    if (restoreSubtitleVisibility && mayShowSubtitles && (property == "sid" || property == "secondary-sid")) {
      PlaybackSession.setPropertyBoolean("sub-visibility", true)
      if (PlaybackSession.getPropertyBoolean("sub-visibility") != true) {
        Log.w("TrackSelectionUtils", "MPV did not confirm sub-visibility=true after selecting $selectedId")
      }
    }
    return true
  }

  PlaybackSession.setPropertyString(property, "no")
  val cleared = getTrackSelectionId(property) <= 0
  if (cleared && (property == "sid" || property == "secondary-sid")) {
    when (source) {
      SubtitleSelectionSource.USER -> SubtitleSelectionGuard.markManual(generation)
      SubtitleSelectionSource.RESTORED -> SubtitleSelectionGuard.markRestored(generation)
      else -> Unit
    }
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
