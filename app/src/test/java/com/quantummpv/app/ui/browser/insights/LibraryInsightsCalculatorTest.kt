package com.quantummpv.app.ui.browser.insights

import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.database.entities.RecentlyPlayedEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryInsightsCalculatorTest {
  private val now = 1_000_000_000_000L
  private val day = 24L * 60 * 60 * 1000

  private fun entry(
    path: String,
    at: Long = now,
    durationSeconds: Long = 100,
    title: String? = null,
    name: String = path.substringAfterLast('/'),
  ) = RecentlyPlayedEntity(
    filePath = path,
    fileName = name,
    videoTitle = title,
    duration = durationSeconds * 1000,
    timestamp = at,
  )

  private fun state(
    timeRemaining: Int,
    watched: Boolean = false,
  ) = PlaybackStateEntity(
    mediaTitle = "unused",
    lastPosition = 0,
    playbackSpeed = 1.0,
    sid = 0,
    subDelay = 0,
    subSpeed = 1.0,
    aid = 0,
    audioDelay = 0,
    timeRemaining = timeRemaining,
    hasBeenWatched = watched,
  )

  private fun calculate(
    history: List<RecentlyPlayedEntity>,
    states: Map<String, PlaybackStateEntity> = emptyMap(),
    threshold: Int = 95,
    exists: (String) -> Boolean = { true },
  ) = LibraryInsightsCalculator.calculate(history, threshold, now, exists) { states[it] }

  @Test
  fun emptyHistoryProducesNothing() {
    val insights = calculate(emptyList())

    assertEquals(0, insights.videosPlayed)
    assertEquals(0, insights.completed)
    assertEquals(0, insights.inProgress)
    assertEquals(0, insights.playedLast7Days)
    assertTrue(insights.continueWatching.isEmpty())
    assertTrue(insights.recentlyPlayed.isEmpty())
  }

  @Test
  fun openingAFileWithoutSavedPositionIsNotCountedAsWatched() {
    val insights = calculate(listOf(entry("/v/a.mp4")))

    assertEquals(1, insights.videosPlayed)
    assertEquals(0, insights.completed)
    assertEquals(0, insights.inProgress)
    assertNull(insights.recentlyPlayed.single().progress)
  }

  @Test
  fun savedPositionBecomesProgressAndAppearsUnderContinueWatching() {
    val insights = calculate(listOf(entry("/v/a.mp4")), mapOf("/v/a.mp4" to state(timeRemaining = 40)))

    assertEquals(0.6f, insights.recentlyPlayed.single().progress!!, 0.0001f)
    assertEquals(1, insights.inProgress)
    assertEquals(listOf("/v/a.mp4"), insights.continueWatching.map { it.filePath })
    assertEquals(0, insights.completed)
  }

  @Test
  fun reachingTheWatchedThresholdCompletesTheVideo() {
    val states = mapOf("/v/a.mp4" to state(timeRemaining = 4))

    val insights = calculate(listOf(entry("/v/a.mp4")), states, threshold = 95)

    assertEquals(1, insights.completed)
    assertEquals(0, insights.inProgress)
    assertTrue(insights.continueWatching.isEmpty())
  }

  @Test
  fun thresholdZeroNeverAutoCompletes() {
    val states = mapOf("/v/a.mp4" to state(timeRemaining = 4))

    val insights = calculate(listOf(entry("/v/a.mp4")), states, threshold = 0)

    assertEquals(0, insights.completed)
    assertEquals(1, insights.inProgress)
  }

  @Test
  fun explicitlyMarkedWatchedCountsEvenWithoutReliableProgress() {
    val states = mapOf("/v/a.mp4" to state(timeRemaining = 90, watched = true))

    val insights = calculate(listOf(entry("/v/a.mp4")), states)

    assertEquals(1, insights.completed)
    assertEquals(0, insights.inProgress)
  }

  @Test
  fun unknownDurationLeavesProgressUnknownInsteadOfGuessing() {
    val history = listOf(entry("https://example.com/s.mp4", durationSeconds = 0))
    val states = mapOf("https://example.com/s.mp4" to state(timeRemaining = 0))

    val insights = calculate(history, states)

    assertEquals(1, insights.videosPlayed)
    assertNull(insights.recentlyPlayed.single().progress)
    assertEquals(0, insights.completed)
    assertEquals(0, insights.inProgress)
  }

  @Test
  fun interruptedPlaybackBelowOnePercentIsNotInProgress() {
    val states = mapOf("/v/a.mp4" to state(timeRemaining = 100))

    val insights = calculate(listOf(entry("/v/a.mp4")), states)

    assertEquals(0f, insights.recentlyPlayed.single().progress!!, 0f)
    assertEquals(0, insights.inProgress)
  }

  @Test
  fun staleMetadataCannotProduceNegativeOrOverflowingProgress() {
    val longRemaining = mapOf("/v/a.mp4" to state(timeRemaining = 500))
    val negativeRemaining = mapOf("/v/a.mp4" to state(timeRemaining = -50))

    assertEquals(0f, calculate(listOf(entry("/v/a.mp4")), longRemaining).recentlyPlayed.single().progress!!, 0f)
    assertEquals(1f, calculate(listOf(entry("/v/a.mp4")), negativeRemaining).recentlyPlayed.single().progress!!, 0f)
  }

  @Test
  fun deletedFilesAreExcludedFromEveryFigure() {
    val history = listOf(entry("/v/keep.mp4"), entry("/v/gone.mp4"))
    val states = mapOf("/v/gone.mp4" to state(timeRemaining = 0, watched = true))

    val insights = calculate(history, states, exists = { it != "/v/gone.mp4" })

    assertEquals(1, insights.videosPlayed)
    assertEquals(0, insights.completed)
    assertEquals(listOf("/v/keep.mp4"), insights.recentlyPlayed.map { it.filePath })
  }

  @Test
  fun duplicateRowsForOneFileCollapseToTheNewestAndCountOnce() {
    val history = listOf(entry("/v/a.mp4", at = now - 5 * day), entry("/v/a.mp4", at = now - day))
    val states = mapOf("/v/a.mp4" to state(timeRemaining = 0, watched = true))

    val insights = calculate(history, states)

    assertEquals(1, insights.videosPlayed)
    assertEquals(1, insights.completed)
    assertEquals(now - day, insights.recentlyPlayed.single().lastPlayedAt)
  }

  @Test
  fun lastSevenDaysWindowIncludesItsStartAndExcludesOlderPlays() {
    val history =
      listOf(
        entry("/v/edge.mp4", at = now - 7 * day),
        entry("/v/old.mp4", at = now - 7 * day - 1),
        entry("/v/new.mp4", at = now),
      )

    val insights = calculate(history)

    assertEquals(3, insights.videosPlayed)
    assertEquals(2, insights.playedLast7Days)
  }

  @Test
  fun listsAreNewestFirstAndLimited() {
    val history = (1..25).map { entry("/v/$it.mp4", at = now - it * 1000L) }

    val insights = calculate(history)

    assertEquals(25, insights.videosPlayed)
    assertEquals(LibraryInsightsCalculator.LIST_LIMIT, insights.recentlyPlayed.size)
    assertEquals("/v/1.mp4", insights.recentlyPlayed.first().filePath)
    assertEquals("/v/10.mp4", insights.recentlyPlayed.last().filePath)
  }

  @Test
  fun displayNamePrefersTitleThenFileNameThenPath() {
    val history =
      listOf(
        entry("/v/a.mp4", at = now, title = "Title"),
        entry("/v/b.mp4", at = now - 1, title = "  ", name = "b-name.mp4"),
        entry("/v/c.mp4", at = now - 2, title = null, name = ""),
      )

    val names = calculate(history).recentlyPlayed.map { it.displayName }

    assertEquals(listOf("Title", "b-name.mp4", "c.mp4"), names)
  }

  @Test
  fun completedVideosAreNotInContinueWatching() {
    val history = listOf(entry("/v/done.mp4"), entry("/v/part.mp4", at = now - 1))
    val states =
      mapOf(
        "/v/done.mp4" to state(timeRemaining = 0, watched = true),
        "/v/part.mp4" to state(timeRemaining = 50),
      )

    val insights = calculate(history, states)

    assertEquals(listOf("/v/part.mp4"), insights.continueWatching.map { it.filePath })
    assertTrue(insights.recentlyPlayed.first { it.filePath == "/v/done.mp4" }.isCompleted)
    assertFalse(insights.recentlyPlayed.first { it.filePath == "/v/part.mp4" }.isCompleted)
  }
}
