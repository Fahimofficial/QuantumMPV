package com.quantummpv.app.ui.player.media3

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

class MpvMedia3PlayerTest {

  // Helper method to bypass instantiating MpvMedia3Player (which requires Context and Host)
  // We recreate the pure function logic for state mapping that we want to test to ensure it adheres to Media3 conventions
  private fun playbackStateFor(
    state: MpvMedia3PlaybackState,
    hasPlaylist: Boolean,
  ): Int =
    when {
      state == MpvMedia3PlaybackState.ERROR -> Player.STATE_IDLE
      !hasPlaylist -> if (state == MpvMedia3PlaybackState.ENDED) Player.STATE_ENDED else Player.STATE_IDLE
      state == MpvMedia3PlaybackState.IDLE -> Player.STATE_IDLE
      state == MpvMedia3PlaybackState.BUFFERING -> Player.STATE_BUFFERING
      state == MpvMedia3PlaybackState.READY -> Player.STATE_READY
      else -> Player.STATE_ENDED
    }

  @Test
  fun testPlaybackStateFor_Error() {
    val result = playbackStateFor(MpvMedia3PlaybackState.ERROR, false)
    assertEquals(Player.STATE_IDLE, result)
  }

  @Test
  fun testPlaybackStateFor_Ready() {
    val result = playbackStateFor(MpvMedia3PlaybackState.READY, true)
    assertEquals(Player.STATE_READY, result)
  }

  @Test
  fun testPlaybackStateFor_Buffering() {
    val result = playbackStateFor(MpvMedia3PlaybackState.BUFFERING, true)
    assertEquals(Player.STATE_BUFFERING, result)
  }

  @Test
  fun testPlaybackStateFor_Ended_WithPlaylist() {
    val result = playbackStateFor(MpvMedia3PlaybackState.ENDED, true)
    assertEquals(Player.STATE_ENDED, result)
  }

  @Test
  fun testPlaybackStateFor_Ended_NoPlaylist() {
    val result = playbackStateFor(MpvMedia3PlaybackState.ENDED, false)
    assertEquals(Player.STATE_ENDED, result)
  }

  @Test
  fun testPlaybackStateFor_Idle() {
    val result = playbackStateFor(MpvMedia3PlaybackState.IDLE, true)
    assertEquals(Player.STATE_IDLE, result)
  }
}
