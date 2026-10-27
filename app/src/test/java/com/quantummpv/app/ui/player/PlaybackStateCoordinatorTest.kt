package com.quantummpv.app.ui.player

import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PlaybackStateCoordinatorTest {
  private class SlowRepository : PlaybackStateRepository {
    val rows = ConcurrentHashMap<String, PlaybackStateEntity>()
    val writes = AtomicInteger()
    private val inFlight = AtomicInteger()
    var maxConcurrentWrites = 0

    override suspend fun upsert(playbackState: PlaybackStateEntity) {
      val now = inFlight.incrementAndGet()
      maxConcurrentWrites = maxOf(maxConcurrentWrites, now)
      delay(5)
      rows[playbackState.mediaTitle] = playbackState
      writes.incrementAndGet()
      inFlight.decrementAndGet()
    }

    override suspend fun getVideoDataByTitle(mediaTitle: String) = rows[mediaTitle]

    override suspend fun getAllPlaybackStates() = rows.values.toList()

    override suspend fun clearAllPlaybackStates() = rows.clear()

    override suspend fun deleteByTitle(mediaTitle: String) {
      rows.remove(mediaTitle)
    }

    override suspend fun deleteStates(identifiers: Collection<String>) {
      identifiers.forEach(rows::remove)
    }

    override suspend fun moveState(
      fromIdentifiers: Collection<String>,
      toIdentifier: String,
    ): PlaybackStateEntity? = null

    override suspend fun updateMediaTitle(
      oldTitle: String,
      newTitle: String,
    ) = Unit
  }

  private fun snapshot(
    position: Int,
    subtitles: String? = "",
  ) = PlaybackStateSnapshot(
    mediaIdentifier = "media:v2:a",
    mediaTitle = "a",
    currentPosition = position,
    duration = 1_000,
    playbackSpeed = 1.0,
    videoZoom = 0f,
    sid = 1,
    secondarySid = -1,
    subDelayMs = 0,
    subSpeed = 1.0,
    aid = 1,
    audioDelayMs = 0,
    externalSubtitles = subtitles,
  )

  private val repository = SlowRepository()
  private val coordinator = PlaybackStateCoordinator(repository, { true }, { 90 }, Dispatchers.IO)

  @Test
  fun debouncedSavesCollapseToTheLatest() =
    runBlocking {
      val jobs = (1..5).map { coordinator.submit(snapshot(position = it * 10), debounceMillis = 50) }
      jobs.forEach { it.join() }
      assertEquals(1, repository.writes.get())
      assertEquals(50, repository.rows["media:v2:a"]?.lastPosition)
    }

  @Test
  fun anImmediateSaveIsNeverDroppedAndSupersedesAPendingDebouncedOne() =
    runBlocking {
      val debounced = coordinator.submit(snapshot(position = 10), debounceMillis = 200)
      val immediate = coordinator.submit(snapshot(position = 20))
      immediate.join()
      debounced.join()
      assertTrue(debounced.isCancelled)
      assertEquals(20, repository.rows["media:v2:a"]?.lastPosition)
    }

  @Test
  fun concurrentWritersAreSerialised() =
    runBlocking {
      (1..10).map { i -> async(Dispatchers.IO) { coordinator.persist(snapshot(position = i)) } }.awaitAll()
      assertEquals(1, repository.maxConcurrentWrites)
      assertEquals(10, repository.writes.get())
    }

  @Test
  fun aWriterThatDoesNotKnowTheSubtitleListKeepsTheSavedOne() =
    runBlocking {
      coordinator.persist(snapshot(position = 10, subtitles = "content://subs/a.srt"))
      coordinator.persist(snapshot(position = 20, subtitles = null))
      assertEquals("content://subs/a.srt", repository.rows["media:v2:a"]?.externalSubtitles)

      coordinator.persist(snapshot(position = 30, subtitles = ""))
      assertEquals("an explicit empty list clears it", "", repository.rows["media:v2:a"]?.externalSubtitles)
    }
}
