package com.quantummpv.app.ui.browser.recentlyplayed

import android.net.Uri
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.domain.media.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RecentlyPlayedSearchTest {
  private val video =
    Video(
      id = 1L,
      title = "Astra",
      displayName = "Astronomy lecture.mp4",
      path = "/media/lectures/astronomy.mp4",
      uri = Uri.parse("file:///media/lectures/astronomy.mp4"),
      duration = 1L,
      durationFormatted = "0:01",
      size = 1L,
      sizeFormatted = "1 B",
      dateModified = 0L,
      dateAdded = 0L,
      mimeType = "video/mp4",
      bucketId = "1",
      bucketDisplayName = "Lectures",
      width = 1,
      height = 1,
      fps = 1f,
      resolution = "1x1",
    )
  private val playlist =
    PlaylistEntity(
      id = 2,
      name = "Astronomy favorites",
      createdAt = 0L,
      updatedAt = 0L,
    )
  private val items =
    listOf(
      RecentlyPlayedItem.VideoItem(video, timestamp = 2L),
      RecentlyPlayedItem.PlaylistItem(playlist, videoCount = 1, mostRecentVideoPath = video.path, timestamp = 1L),
    )

  @Test
  fun inactiveSearchKeepsTheCurrentTabItems() {
    assertEquals(items, filterRecentlyPlayedItems(items, isSearching = false, query = "astra"))
  }

  @Test
  fun queryMatchesVideoDisplayNameTitleAndPath() {
    assertEquals(listOf(items[0]), filterRecentlyPlayedItems(items, isSearching = true, query = "lecture"))
    assertEquals(listOf(items[0]), filterRecentlyPlayedItems(items, isSearching = true, query = "astra"))
    assertEquals(listOf(items[0]), filterRecentlyPlayedItems(items, isSearching = true, query = "media/lectures"))
  }

  @Test
  fun queryMatchesPlaylistNameAndDoesNotReturnUnrelatedItems() {
    assertEquals(listOf(items[1]), filterRecentlyPlayedItems(items, isSearching = true, query = "favorites"))
    assertEquals(emptyList<RecentlyPlayedItem>(), filterRecentlyPlayedItems(items, isSearching = true, query = "missing"))
  }
}
