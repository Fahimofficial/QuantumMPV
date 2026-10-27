/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.domain.playbackstate.repository

import com.quantummpv.app.database.entities.PlaybackStateEntity

interface PlaybackStateRepository {
  suspend fun upsert(playbackState: PlaybackStateEntity)

  suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity?

  suspend fun getAllPlaybackStates(): List<PlaybackStateEntity>

  suspend fun clearAllPlaybackStates()

  suspend fun deleteByTitle(mediaTitle: String)

  /** Deletes every row whose key is one of [identifiers]. */
  suspend fun deleteStates(identifiers: Collection<String>)

  /**
   * Atomically re-keys the first existing row found under [fromIdentifiers] (in iteration order) to
   * [toIdentifier] and removes every other [fromIdentifiers] row. Returns the moved row, or null
   * when none of the source keys has saved state.
   */
  suspend fun moveState(
    fromIdentifiers: Collection<String>,
    toIdentifier: String,
  ): PlaybackStateEntity?

  suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  )
}
