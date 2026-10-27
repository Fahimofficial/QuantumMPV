/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.utils.media

import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import com.quantummpv.app.ui.player.PlaybackIdentity
import java.net.URI

/**
 * Keeps saved playback state attached to a file when the app renames, moves or deletes it.
 *
 * Playback state rows are keyed by [PlaybackIdentity] digests of the media location, never by bare
 * file names. Every way a location can be expressed (normalized local path, `file://` URI, the raw
 * path read as a URI, and the MediaStore/SAF content URI) is a separate digest, so reconciliation
 * has to consider all of them. Legacy rows keyed by a bare file name are deliberately left alone:
 * a bare name is ambiguous across folders, and touching it could move or erase another file's state.
 */
class PlaybackStateReconciler(
  private val repository: PlaybackStateRepository,
) {
  /** Moves the state saved for [oldLocation] to [newLocation]. Returns true when a row was moved. */
  suspend fun onRenamedOrMoved(
    oldLocation: String,
    newLocation: String,
    oldContentUri: String? = null,
  ): Boolean {
    if (oldLocation.isBlank() || newLocation.isBlank() || oldLocation == newLocation) return false
    val target = primaryIdentifier(newLocation)
    val sources = identifiersFor(oldLocation, oldContentUri)
    return repository.moveState(sources, target) != null
  }

  /** Removes every row that can belong to [location]. */
  suspend fun onDeleted(
    location: String,
    contentUri: String? = null,
  ) {
    if (location.isBlank()) return
    repository.deleteStates(identifiersFor(location, contentUri))
  }

  companion object {
    /**
     * Every playback-state key that can refer to [location]. The primary key comes first so it wins
     * when several historical rows exist for the same file.
     */
    fun identifiersFor(
      location: String,
      contentUri: String? = null,
    ): LinkedHashSet<String> {
      val ids = linkedSetOf<String>()
      val localPath = localPathOf(location)
      if (localPath != null) {
        ids += PlaybackIdentity.forLocalPath(localPath)
        ids += PlaybackIdentity.forUri(localPath)
        ids += PlaybackIdentity.forUri("file://$localPath")
      }
      ids += PlaybackIdentity.forUri(location)
      contentUri?.takeIf { it.isNotBlank() }?.let { ids += PlaybackIdentity.forUri(it) }
      return ids
    }

    /** The key the player writes for [location] after it has been renamed or moved. */
    fun primaryIdentifier(location: String): String =
      localPathOf(location)?.let(PlaybackIdentity::forLocalPath) ?: PlaybackIdentity.forUri(location)

    private fun localPathOf(location: String): String? =
      when {
        location.startsWith("/") -> location
        location.startsWith("file:", ignoreCase = true) ->
          runCatching { URI(location).path }.getOrNull()?.takeIf { it.startsWith("/") }
        else -> null
      }
  }
}
