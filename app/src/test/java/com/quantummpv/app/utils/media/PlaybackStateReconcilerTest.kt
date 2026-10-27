package com.quantummpv.app.utils.media

import android.content.Context
import androidx.room.Room
import com.quantummpv.app.database.MpvRxDatabase
import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.database.repository.PlaybackStateRepositoryImpl
import com.quantummpv.app.ui.player.PlaybackIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Saved playback state is keyed by hashed playback identities. Renames, moves and deletes made by
 * the app must follow those identities instead of bare file names, otherwise resume positions and
 * watched flags are lost and orphaned rows accumulate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlaybackStateReconcilerTest {
  private lateinit var database: MpvRxDatabase
  private lateinit var repository: PlaybackStateRepositoryImpl
  private lateinit var reconciler: PlaybackStateReconciler

  @Before
  fun setUp() {
    val context: Context = RuntimeEnvironment.getApplication()
    database = Room.inMemoryDatabaseBuilder(context, MpvRxDatabase::class.java).allowMainThreadQueries().build()
    repository = PlaybackStateRepositoryImpl(database)
    reconciler = PlaybackStateReconciler(repository)
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun state(
    key: String,
    position: Int = 120,
    watched: Boolean = false,
  ) = PlaybackStateEntity(
    mediaTitle = key,
    lastPosition = position,
    playbackSpeed = 1.25,
    sid = 3,
    subDelay = 0,
    subSpeed = 1.0,
    aid = 2,
    audioDelay = 0,
    timeRemaining = 600,
    hasBeenWatched = watched,
  )

  @Test
  fun renameCarriesResumeStateToTheNewPathIdentity() =
    runBlocking {
      val old = "/storage/emulated/0/Movies/Show/episode 1.mkv"
      val new = "/storage/emulated/0/Movies/Show/Episode 01.mkv"
      repository.upsert(state(PlaybackIdentity.forLocalPath(old), position = 754, watched = true))

      assertTrue(reconciler.onRenamedOrMoved(old, new))

      assertNull(repository.getVideoDataByTitle(PlaybackIdentity.forLocalPath(old)))
      val moved = repository.getVideoDataByTitle(PlaybackIdentity.forLocalPath(new))
      assertNotNull(moved)
      assertEquals(754, moved!!.lastPosition)
      assertTrue(moved.hasBeenWatched)
      assertEquals(3, moved.sid)
      assertEquals(1.25, moved.playbackSpeed, 0.0)
    }

  @Test
  fun moveToAnotherFolderWithTheSameFileNameStillMovesTheState() =
    runBlocking {
      val old = "/storage/emulated/0/Download/movie.mkv"
      val new = "/storage/emulated/0/Movies/movie.mkv"
      repository.upsert(state(PlaybackIdentity.forLocalPath(old), position = 3_000))

      assertTrue(reconciler.onRenamedOrMoved(old, new))

      assertEquals(3_000, repository.getVideoDataByTitle(PlaybackIdentity.forLocalPath(new))?.lastPosition)
      assertNull(repository.getVideoDataByTitle(PlaybackIdentity.forLocalPath(old)))
    }

  @Test
  fun stateSavedUnderTheContentUriIsFoundAndAllOldKeysAreRemoved() =
    runBlocking {
      val old = "/storage/emulated/0/Movies/clip.mp4"
      val contentUri = "content://media/external/video/media/42"
      repository.upsert(state(PlaybackIdentity.forUri(contentUri), position = 90))
      repository.upsert(state(PlaybackIdentity.forUri("file://$old"), position = 10))

      assertTrue(reconciler.onRenamedOrMoved(old, "/storage/emulated/0/Movies/renamed.mp4", contentUri))

      val moved = repository.getVideoDataByTitle(PlaybackIdentity.forLocalPath("/storage/emulated/0/Movies/renamed.mp4"))
      assertEquals("the file:// row is checked before the content URI row", 10, moved?.lastPosition)
      assertNull(repository.getVideoDataByTitle(PlaybackIdentity.forUri(contentUri)))
      assertNull(repository.getVideoDataByTitle(PlaybackIdentity.forUri("file://$old")))
      assertEquals(1, repository.getAllPlaybackStates().size)
    }

  @Test
  fun moveIntoATreeUriKeysTheStateByThatUri() =
    runBlocking {
      val old = "/storage/emulated/0/Movies/clip.mp4"
      val newUri = "content://com.android.externalstorage.documents/tree/primary%3AArchive/document/primary%3AArchive%2Fclip.mp4"
      repository.upsert(state(PlaybackIdentity.forLocalPath(old)))

      assertTrue(reconciler.onRenamedOrMoved(old, newUri))

      assertNotNull(repository.getVideoDataByTitle(PlaybackIdentity.forUri(newUri)))
    }

  @Test
  fun deleteRemovesEveryIdentityOfTheFileButNothingElse() =
    runBlocking {
      val path = "/storage/emulated/0/Movies/gone.mkv"
      val contentUri = "content://media/external/video/media/7"
      val other = PlaybackIdentity.forLocalPath("/storage/emulated/0/Movies/kept.mkv")
      repository.upsert(state(PlaybackIdentity.forLocalPath(path)))
      repository.upsert(state(PlaybackIdentity.forUri(contentUri)))
      repository.upsert(state(other))

      reconciler.onDeleted(path, contentUri)

      assertEquals(listOf(other), repository.getAllPlaybackStates().map { it.mediaTitle })
    }

  @Test
  fun legacyBareFileNameRowsBelongingToOtherFoldersAreNeverTouched() =
    runBlocking {
      // Pre-identity versions keyed rows by bare file name, which is ambiguous across folders.
      repository.upsert(state("movie.mkv", position = 4_200))

      assertFalse(reconciler.onRenamedOrMoved("/storage/emulated/0/A/movie.mkv", "/storage/emulated/0/A/film.mkv"))
      reconciler.onDeleted("/storage/emulated/0/B/movie.mkv")

      assertEquals(4_200, repository.getVideoDataByTitle("movie.mkv")?.lastPosition)
      assertNull(repository.getVideoDataByTitle("film.mkv"))
    }

  @Test
  fun renameWithoutSavedStateDoesNothing() =
    runBlocking {
      assertFalse(reconciler.onRenamedOrMoved("/storage/emulated/0/a.mkv", "/storage/emulated/0/b.mkv"))
      assertTrue(repository.getAllPlaybackStates().isEmpty())
    }

  @Test
  fun identifiersMatchTheKeysTheBrowserProbes() {
    val path = "/storage/emulated/0/Movies/x.mkv"
    val ids = PlaybackStateReconciler.identifiersFor(path, "content://media/external/video/media/1")
    assertEquals(PlaybackIdentity.forLocalPath(path), ids.first())
    assertTrue(PlaybackIdentity.forUri(path) in ids)
    assertTrue(PlaybackIdentity.forUri("file://$path") in ids)
    assertTrue(PlaybackIdentity.forUri("content://media/external/video/media/1") in ids)
    assertEquals(PlaybackIdentity.forLocalPath(path), PlaybackStateReconciler.primaryIdentifier("file://$path"))
  }
}
