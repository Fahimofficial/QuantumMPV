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
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.PlayerPreferences
import com.quantummpv.app.utils.media.PlaybackStateEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single writer for per-file playback state.
 *
 * The player activity and the background service both save resume position and track choices.
 * Every write goes through here: one lock serialises read-merge-write, one merge policy decides
 * what a partial snapshot keeps from the saved row, and writes run on an application-lifetime
 * scope so they finish after the activity or service that requested them is gone — without ever
 * blocking the main thread.
 */
internal class PlaybackStateCoordinator(
  private val repository: PlaybackStateRepository,
  private val savePositionOnQuit: () -> Boolean,
  private val watchedThreshold: () -> Int,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  constructor(
    repository: PlaybackStateRepository,
    playerPreferences: PlayerPreferences,
    browserPreferences: BrowserPreferences,
  ) : this(repository, playerPreferences.savePositionOnQuit::get, browserPreferences.watchedThreshold::get)

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val lock = Mutex()
  private val pendingLock = Any()
  private val pending = mutableMapOf<String, Job>()

  /** Writes [snapshot] now. Returns true when the row was written; cancellation propagates. */
  suspend fun persist(snapshot: PlaybackStateSnapshot): Boolean {
    val identifier = snapshot.mediaIdentifier
    if (identifier.isBlank()) return false
    return try {
      lock.withLock {
        val oldState = repository.getVideoDataByTitle(identifier)
        val entity =
          PlaybackStatePersistence.buildEntity(
            oldState = oldState,
            snapshot = snapshot,
            savePositionOnQuit = savePositionOnQuit(),
            watchedThreshold = watchedThreshold(),
          )
        repository.upsert(entity)
      }
      PlaybackStateEvents.notifyChanged(identifier)
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error saving playback state", e)
      false
    }
  }

  /**
   * Schedules [snapshot] for writing. A later submission for the same file replaces one that is
   * still waiting out its [debounceMillis]; a submission with no debounce is never replaced.
   */
  fun submit(
    snapshot: PlaybackStateSnapshot,
    debounceMillis: Long = 0L,
  ): Job {
    val identifier = snapshot.mediaIdentifier
    synchronized(pendingLock) {
      // Only debounced saves are tracked here, so this never cancels an immediate save.
      pending.remove(identifier)?.cancel()
      val job =
        scope.launch {
          if (debounceMillis > 0L) delay(debounceMillis)
          persist(snapshot)
        }
      if (debounceMillis > 0L) pending[identifier] = job
      job.invokeOnCompletion {
        synchronized(pendingLock) { if (pending[identifier] === job) pending.remove(identifier) }
      }
      return job
    }
  }

  private companion object {
    const val TAG = "PlaybackStateCoordinator"
  }
}
