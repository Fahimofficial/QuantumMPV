/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

/**
 * Stable identities for audio and subtitle tracks.
 *
 * mpv numbers tracks in the order they appear, so the raw `sid`/`aid` saved for a file can point
 * at a different track after external subtitles are re-added in another order, a sidecar file is
 * added or removed, or a new demuxer build reorders streams. A key names the track itself:
 * external tracks by their source location, internal tracks by their container stream id plus
 * codec, language and title.
 */
object TrackKeys {
  data class Descriptor(
    val id: Int,
    val type: String,
    val external: Boolean,
    val externalFilename: String?,
    val srcId: Long?,
    val codec: String?,
    val lang: String?,
    val title: String?,
  )

  private const val EXTERNAL = "ext:"
  private const val INTERNAL = "int:"
  private const val SEPARATOR = '\u001f'

  /**
   * Key for [track], or null when it has no stable identity (an external track loaded through a
   * one-shot `fd://` descriptor whose source URI is unknown).
   */
  fun keyOf(track: Descriptor): String? =
    if (track.external && !track.externalFilename.isNullOrBlank()) {
      track.externalFilename.takeUnless(::isEphemeralLocation)?.let { EXTERNAL + normalizeLocation(it) }
    } else {
      INTERNAL +
        listOf(track.srcId?.toString().orEmpty(), track.codec.orEmpty(), track.lang.orEmpty(), track.title.orEmpty())
          .joinToString(SEPARATOR.toString())
    }

  /**
   * Finds the track [key] refers to among [tracks] of [type], or null when it no longer exists.
   * A key is never matched to a track of a different kind (external vs internal).
   */
  fun resolve(
    key: String?,
    type: String,
    tracks: List<Descriptor>,
  ): Int? {
    if (key.isNullOrBlank()) return null
    val candidates = tracks.filter { it.type == type }
    candidates.firstOrNull { keyOf(it) == key }?.let { return it.id }

    return when {
      key.startsWith(EXTERNAL) -> {
        val location = key.removePrefix(EXTERNAL)
        val name = location.substringAfterLast('/')
        candidates
          .filter { it.external && !it.externalFilename.isNullOrBlank() && !isEphemeralLocation(it.externalFilename) }
          .singleOrNull { normalizeLocation(it.externalFilename!!).substringAfterLast('/') == name }
          ?.id
      }
      key.startsWith(INTERNAL) -> {
        val parts = key.removePrefix(INTERNAL).split(SEPARATOR)
        if (parts.size != 4) return null
        val (srcId, codec, lang, title) = parts
        val internal = candidates.filterNot { it.external }
        // Same stream in the container, possibly with refreshed metadata.
        internal.singleOrNull { it.srcId?.toString() == srcId && it.codec.orEmpty() == codec }?.id
          // Same language/title/codec, e.g. after the container was remuxed.
          ?: internal
            .filter { it.codec.orEmpty() == codec && it.lang.orEmpty() == lang && it.title.orEmpty() == title }
            .singleOrNull()
            ?.id
      }
      else -> null
    }
  }

  private fun isEphemeralLocation(location: String): Boolean =
    location.startsWith("fd://", ignoreCase = true) || location.startsWith("fdclose://", ignoreCase = true)

  /** `file:///a/b.srt`, `/a/b.srt` and `/a/./b.srt` all name the same subtitle file. */
  internal fun normalizeLocation(location: String): String {
    val trimmed = location.trim()
    val path = if (trimmed.startsWith("file://", ignoreCase = true)) trimmed.substring(7) else trimmed
    if (!path.startsWith("/")) return trimmed
    return path.split('/').fold(mutableListOf<String>()) { parts, part ->
      when (part) {
        "", "." -> Unit
        ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
        else -> parts += part
      }
      parts
    }.joinToString("/", prefix = "/")
  }

  // ---------------------------------------------------------------------------------------------
  // mpv adapters (JNI reads; call off the main thread when possible)
  // ---------------------------------------------------------------------------------------------

  /**
   * Key of the track currently selected in [slot] (`sub`, `sub2` or `audio`), or null if none.
   * [sourceOf] maps the path handed to mpv (which may be a one-shot `fd://`) back to the URI the
   * track was loaded from.
   */
  fun currentKey(
    slot: String,
    sourceOf: (String) -> String? = { null },
  ): String? {
    val base = "current-tracks/$slot"
    val id = PlaybackSession.getPropertyInt("$base/id") ?: return null
    return keyOf(
      Descriptor(
        id = id,
        type = PlaybackSession.getPropertyString("$base/type") ?: return null,
        external = PlaybackSession.getPropertyBoolean("$base/external") == true,
        externalFilename = PlaybackSession.getPropertyString("$base/external-filename")?.let { sourceOf(it) ?: it },
        srcId = PlaybackSession.getPropertyInt("$base/src-id")?.toLong(),
        codec = PlaybackSession.getPropertyString("$base/codec"),
        lang = PlaybackSession.getPropertyString("$base/lang"),
        title = PlaybackSession.getPropertyString("$base/title"),
      ),
    )
  }

  /** The current track list for [type] (`sub` or `audio`); see [currentKey] for [sourceOf]. */
  fun tracks(
    type: String,
    sourceOf: (String) -> String? = { null },
  ): List<Descriptor> {
    val count = PlaybackSession.getPropertyInt("track-list/count") ?: return emptyList()
    return (0 until count).mapNotNull { index ->
      val base = "track-list/$index"
      val trackType = PlaybackSession.getPropertyString("$base/type") ?: return@mapNotNull null
      if (trackType != type) return@mapNotNull null
      Descriptor(
        id = PlaybackSession.getPropertyInt("$base/id") ?: return@mapNotNull null,
        type = trackType,
        external = PlaybackSession.getPropertyBoolean("$base/external") == true,
        externalFilename = PlaybackSession.getPropertyString("$base/external-filename")?.let { sourceOf(it) ?: it },
        srcId = PlaybackSession.getPropertyInt("$base/src-id")?.toLong(),
        codec = PlaybackSession.getPropertyString("$base/codec"),
        lang = PlaybackSession.getPropertyString("$base/lang"),
        title = PlaybackSession.getPropertyString("$base/title"),
      )
    }
  }
}
