package com.quantummpv.app.ui.player

import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.ui.player.TrackKeys.Descriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved subtitle/audio choices are restored by stable track keys, not by mpv's positional ids,
 * and saved subtitle visibility is respected instead of being forced on.
 */
class TrackRestoreTest {
  private fun internal(
    id: Int,
    srcId: Long,
    lang: String?,
    title: String? = null,
    codec: String = "subrip",
    type: String = "sub",
  ) = Descriptor(id, type, external = false, externalFilename = null, srcId = srcId, codec = codec, lang = lang, title = title)

  private fun external(
    id: Int,
    path: String,
    lang: String? = null,
  ) = Descriptor(id, "sub", external = true, externalFilename = path, srcId = 1, codec = "subrip", lang = lang, title = null)

  @Test
  fun externalSubtitleIsFoundAfterTracksAreReAddedInADifferentOrder() {
    val savedSession =
      listOf(internal(1, 3, "eng"), external(2, "/storage/emulated/0/Movies/a.en.srt"), external(3, "/storage/emulated/0/Movies/a.fr.srt"))
    val key = TrackKeys.keyOf(savedSession[2])!!

    // Next launch: the French file was added first, and a sidecar subtitle appeared.
    val nextSession =
      listOf(
        internal(1, 3, "eng"),
        external(2, "file:///storage/emulated/0/Movies/a.fr.srt"),
        external(3, "/storage/emulated/0/Movies/a.de.srt"),
        external(4, "/storage/emulated/0/Movies/./a.en.srt"),
      )

    assertEquals(2, TrackKeys.resolve(key, "sub", nextSession))
    assertEquals(4, TrackKeys.resolve(TrackKeys.keyOf(savedSession[1]), "sub", nextSession))
  }

  @Test
  fun internalTrackFollowsItsContainerStreamWhenIdsShift() {
    val saved = internal(id = 2, srcId = 5, lang = "jpn", title = "Signs & Songs", codec = "ass")
    val key = TrackKeys.keyOf(saved)!!
    val shifted =
      listOf(
        external(1, "/sdcard/extra.srt"),
        internal(id = 2, srcId = 4, lang = "eng", codec = "ass"),
        internal(id = 3, srcId = 5, lang = "jpn", title = "Signs & Songs", codec = "ass"),
      )
    assertEquals(3, TrackKeys.resolve(key, "sub", shifted))
  }

  @Test
  fun aMissingTrackResolvesToNothingInsteadOfAWrongLanguage() {
    val key = TrackKeys.keyOf(external(3, "/storage/emulated/0/Movies/a.fr.srt"))!!
    val withoutIt = listOf(internal(1, 3, "eng"), external(2, "/storage/emulated/0/Movies/a.en.srt"), external(3, "/x/b.de.srt"))
    assertNull(TrackKeys.resolve(key, "sub", withoutIt))
    assertNull("type must match", TrackKeys.resolve(TrackKeys.keyOf(internal(1, 3, "eng", type = "audio", codec = "aac")), "sub", withoutIt))
  }

  @Test
  fun oneShotDescriptorPathsHaveNoKeyUnlessTheirSourceIsKnown() {
    assertNull(TrackKeys.keyOf(external(5, "fd://42")))
    assertEquals("ext:/storage/emulated/0/s.srt", TrackKeys.keyOf(external(5, "/storage/emulated/0/s.srt")))
  }

  @Test
  fun restoredChoiceBlocksAutomationButNotTheUserOrAnExplicitLaunchChoice() {
    val restored = 9_001L
    SubtitleSelectionGuard.markRestored(restored)
    assertTrue(SubtitleSelectionGuard.blocksAutomaticSelection(restored))
    assertFalse("a restore is not an explicit choice", SubtitleSelectionGuard.isManualSelection(restored))

    val launchChoice = 9_002L
    SubtitleSelectionGuard.markManual(launchChoice)
    assertTrue(SubtitleSelectionGuard.isManualSelection(launchChoice))
    assertTrue(SubtitleSelectionGuard.blocksAutomaticSelection(launchChoice))
    assertFalse(SubtitleSelectionGuard.blocksAutomaticSelection(9_003L))

    SubtitleSelectionGuard.markVisibilityRestored(restored)
    assertTrue(SubtitleSelectionGuard.isVisibilityRestored(restored))
    assertFalse(SubtitleSelectionGuard.isVisibilityRestored(launchChoice))
  }

  private fun snapshot(
    sid: Int,
    sidKey: String?,
    visibility: Boolean?,
  ) = PlaybackStateSnapshot(
    mediaIdentifier = "media:v2:x",
    mediaTitle = "x",
    currentPosition = 60,
    duration = 600,
    playbackSpeed = 1.0,
    videoZoom = 0f,
    sid = sid,
    secondarySid = -1,
    subDelayMs = 0,
    subSpeed = 1.0,
    aid = 1,
    audioDelayMs = 0,
    externalSubtitles = "",
    subVisibility = visibility,
    sidKey = sidKey,
  )

  @Test
  fun hiddenSubtitlesAreSavedAndUnknownVisibilityKeepsTheSavedValue() {
    val first = PlaybackStatePersistence.buildEntity(null, snapshot(2, "ext:/a.srt", visibility = false), true, 90)
    assertEquals(false, first.subVisibility)
    assertEquals("ext:/a.srt", first.sidKey)

    // A background save cannot read keys or visibility; it keeps what the foreground saved.
    val background = PlaybackStatePersistence.buildEntity(first, snapshot(2, null, visibility = null), true, 90)
    assertEquals(false, background.subVisibility)
    assertEquals("ext:/a.srt", background.sidKey)

    // If the raw id changed without a new key, the old key no longer describes the selection.
    val changed = PlaybackStatePersistence.buildEntity(first, snapshot(3, null, visibility = true), true, 90)
    assertNull(changed.sidKey)
    assertEquals(true, changed.subVisibility)
  }

  @Test
  fun legacyRowsWithoutKeysStillRestore() {
    val legacy =
      PlaybackStateEntity(
        mediaTitle = "media:v2:x",
        lastPosition = 10,
        playbackSpeed = 1.0,
        sid = 2,
        subDelay = 0,
        subSpeed = 1.0,
        aid = 1,
        audioDelay = 0,
      )
    assertNull(legacy.sidKey)
    assertNull(legacy.subVisibility)
  }
}
