package com.quantummpv.app.ui.player.media3

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class MpvMedia3PlayerTest {

  @Test
  fun testPlaybackStateFor_Error() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.ERROR, false)
    assertEquals(Player.STATE_IDLE, result)
  }

  @Test
  fun testPlaybackStateFor_Ready() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.READY, true)
    assertEquals(Player.STATE_READY, result)
  }

  @Test
  fun testPlaybackStateFor_Buffering() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.BUFFERING, true)
    assertEquals(Player.STATE_BUFFERING, result)
  }

  @Test
  fun testPlaybackStateFor_Ended_WithPlaylist() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.ENDED, true)
    assertEquals(Player.STATE_ENDED, result)
  }

  @Test
  fun testPlaybackStateFor_Ended_NoPlaylist() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.ENDED, false)
    assertEquals(Player.STATE_ENDED, result)
  }

  @Test
  fun testPlaybackStateFor_Idle() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.IDLE, true)
    assertEquals(Player.STATE_IDLE, result)
  }
}
