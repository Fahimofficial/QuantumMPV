/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.database.repository

import androidx.room.withTransaction
import com.quantummpv.app.database.MpvRxDatabase
import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackStateRepositoryImpl(
  private val database: MpvRxDatabase,
) : PlaybackStateRepository {
  // Library screens can ask for the complete playback-state set several times while navigating or
  // refreshing. Keep one process-local snapshot after the first unavoidable Room load and maintain
  // it incrementally on writes instead of allocating/querying the full table on every request.
  private val stateCache = ConcurrentHashMap<String, PlaybackStateEntity>()
  private val allStatesLoaded = AtomicBoolean(false)
  private val fullLoadMutex = Mutex()

  override suspend fun upsert(playbackState: PlaybackStateEntity) {
    database.videoDataDao().upsert(playbackState)
    stateCache[playbackState.mediaTitle] = playbackState
  }

  override suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity? {
    stateCache[mediaTitle]?.let { return it }
    return database.videoDataDao().getVideoDataByTitle(mediaTitle)?.also { state ->
      stateCache[state.mediaTitle] = state
    }
  }

  override suspend fun getAllPlaybackStates(): List<PlaybackStateEntity> {
    if (allStatesLoaded.get()) return stateCache.values.toList()

    return fullLoadMutex.withLock {
      if (!allStatesLoaded.get()) {
        val loaded = database.videoDataDao().getAllPlaybackStates()
        stateCache.clear()
        loaded.forEach { state -> stateCache[state.mediaTitle] = state }
        allStatesLoaded.set(true)
      }
      stateCache.values.toList()
    }
  }

  override suspend fun clearAllPlaybackStates() {
    database.videoDataDao().clearAllPlaybackStates()
    stateCache.clear()
    allStatesLoaded.set(true)
  }

  override suspend fun deleteByTitle(mediaTitle: String) {
    database.videoDataDao().deleteByTitle(mediaTitle)
    stateCache.remove(mediaTitle)
  }

  override suspend fun deleteStates(identifiers: Collection<String>) {
    val keys = identifiers.distinct()
    if (keys.isEmpty()) return
    database.videoDataDao().deleteByTitles(keys)
    keys.forEach(stateCache::remove)
  }

  override suspend fun moveState(
    fromIdentifiers: Collection<String>,
    toIdentifier: String,
  ): PlaybackStateEntity? {
    val sources = fromIdentifiers.distinct()
    val moved =
      database.withTransaction {
        val dao = database.videoDataDao()
        val source = sources.firstNotNullOfOrNull { dao.getVideoDataByTitle(it) } ?: return@withTransaction null
        val stale = sources.filter { it != toIdentifier }
        if (stale.isNotEmpty()) dao.deleteByTitles(stale)
        source.copy(mediaTitle = toIdentifier).also { dao.upsert(it) }
      }
    if (moved != null) {
      sources.forEach(stateCache::remove)
      stateCache[toIdentifier] = moved
    }
    return moved
  }

  override suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  ) {
    database.videoDataDao().updateMediaTitle(oldTitle, newTitle)
    stateCache.remove(oldTitle)?.let { state ->
      stateCache[newTitle] = state.copy(mediaTitle = newTitle)
    }
  }
}
