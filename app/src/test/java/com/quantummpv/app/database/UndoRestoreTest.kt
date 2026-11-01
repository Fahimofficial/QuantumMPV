package com.quantummpv.app.database

import androidx.room.Room
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.database.entities.PlaylistItemEntity
import com.quantummpv.app.database.entities.RecentlyPlayedEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Undo for playlist-item and history removals restores exactly what was removed, in order. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class UndoRestoreTest {
  private lateinit var db: MpvRxDatabase
  private var playlistId = 0

  @Before
  fun setUp() =
    runBlocking {
      db =
        Room
          .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MpvRxDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      playlistId = db.playlistDao().insertPlaylist(PlaylistEntity(name = "p", createdAt = 0, updatedAt = 0)).toInt()
    }

  @After
  fun tearDown() = db.close()

  private suspend fun add(vararg paths: String) =
    paths.forEach { path ->
      val next = (db.playlistDao().getMaxPosition(playlistId) ?: -1) + 1
      db.playlistDao().insertPlaylistItem(
        PlaylistItemEntity(playlistId = playlistId, filePath = path, fileName = path, position = next, addedAt = 0),
      )
    }

  private suspend fun rows() = db.playlistDao().getPlaylistItems(playlistId)

  private suspend fun order() = rows().map { it.filePath }

  /** Mirrors PlaylistDetailViewModel.removePlaylistItems: returns removed rows and the id watermark. */
  private suspend fun remove(vararg paths: String): Pair<List<PlaylistItemEntity>, Int> {
    val all = rows()
    val removed = paths.map { p -> all.first { it.filePath == p } }
    db.playlistDao().deletePlaylistItems(removed)
    return removed to all.maxOf { it.id }
  }

  private suspend fun undo(removed: Pair<List<PlaylistItemEntity>, Int>) =
    db.playlistDao().restorePlaylistItems(playlistId, removed.first, removed.second)

  @Test
  fun restoresAtOriginalSlotAndRenumbers() =
    runBlocking {
      add("A", "B", "C")
      undo(remove("B"))
      assertEquals(listOf("A", "B", "C"), order())
      assertEquals(listOf(0, 1, 2), rows().map { it.position })
    }

  @Test
  fun positionGapsFromEarlierRemovalsKeepRelativeOrder() =
    runBlocking {
      add("A", "B", "C", "D")
      remove("A") // leaves B:1, C:2, D:3 (gap at 0)
      undo(remove("C"))
      assertEquals(listOf("B", "C", "D"), order())
    }

  @Test
  fun reorderBeforeUndoNeverDuplicatesPositions() =
    runBlocking {
      add("A", "B", "C")
      val removed = remove("B")
      val r = rows()
      db.playlistDao().reorderPlaylistItems(playlistId, listOf(r[1].id, r[0].id)) // C, A
      undo(removed)
      val positions = rows().map { it.position }
      assertEquals(positions.distinct().size, positions.size)
      assertEquals(3, positions.size)
    }

  @Test
  fun duplicatePathsRestoreTheRemovedCopyWithItsMetadata() =
    runBlocking {
      add("A", "A")
      db.playlistDao().updatePlaylistItem(rows()[1].copy(lastPosition = 4242))
      val removedRow = rows()[1]
      val watermark = rows().maxOf { it.id }
      db.playlistDao().deletePlaylistItems(listOf(removedRow))
      undo(listOf(removedRow) to watermark)
      assertEquals(2, rows().size)
      assertEquals(4242L, rows().first { it.id == removedRow.id }.lastPosition)
    }

  @Test
  fun rowRecreatedAfterRemovalIsNotDuplicated() =
    runBlocking {
      add("A", "B")
      val removed = remove("B")
      add("B") // e.g. an M3U refresh re-added the stream with a new id
      undo(removed)
      assertEquals(listOf("A", "B"), order())
    }

  @Test
  fun undoTwiceIsIdempotent() =
    runBlocking {
      add("A", "B")
      val removed = remove("B")
      undo(removed)
      undo(removed)
      assertEquals(listOf("A", "B"), order())
    }

  @Test
  fun historyRestoreKeepsNewerPlaybackRowForTheSameFile() =
    runBlocking {
      val dao = db.recentlyPlayedDao()
      dao.insert(RecentlyPlayedEntity(filePath = "/a", fileName = "a", timestamp = 1))
      dao.insert(RecentlyPlayedEntity(filePath = "/b", fileName = "b", timestamp = 2))
      val snapshot = dao.getByFilePaths(listOf("/a", "/b"))
      dao.deleteByFilePath("/a")
      dao.deleteByFilePath("/b")
      dao.insert(RecentlyPlayedEntity(filePath = "/a", fileName = "a", timestamp = 99)) // played again
      dao.restoreEntries(snapshot)
      val all = dao.getAllRecentlyPlayed()
      assertEquals(listOf("/a", "/b"), all.map { it.filePath }.sorted())
      assertEquals(99L, all.first { it.filePath == "/a" }.timestamp)
    }
}
