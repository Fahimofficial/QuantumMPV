package com.quantummpv.app.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressSavePolicyTest {
  private fun shouldSave(
    mpvInitialized: Boolean = true,
    isExiting: Boolean = false,
    ownsSession: Boolean = true,
    isForeground: Boolean = true,
    mediaTitle: String = "movie.mkv",
  ) = PlaybackProgressSavePolicy.shouldSave(mpvInitialized, isExiting, ownsSession, isForeground, mediaTitle)

  @Test
  fun savesOnlyForAnOwnedForegroundSessionWithMedia() {
    assertTrue(shouldSave())
    assertFalse(shouldSave(mpvInitialized = false))
    assertFalse(shouldSave(isExiting = true))
    assertFalse(shouldSave(ownsSession = false))
    assertFalse("the background service saves while stopped", shouldSave(isForeground = false))
    assertFalse(shouldSave(mediaTitle = " "))
  }

  @Test
  fun intervalBoundsLostProgress() {
    assertTrue(PlaybackProgressSavePolicy.INTERVAL_MS in 5_000L..30_000L)
  }
}
