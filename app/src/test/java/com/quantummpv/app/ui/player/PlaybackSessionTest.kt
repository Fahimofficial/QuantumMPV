package com.quantummpv.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.quantummpv.app.ui.player.PlaybackSessionStateMapper

class PlaybackSessionTest {

  @Test
  fun testResolveEndFileState_EOF() {
    val result = PlaybackSessionStateMapper.resolveEndFileState(
      reason = PlaybackSession.EndFileReason.EOF,
      loadedGeneration = 1L,
      currentGeneration = 1L,
      activeGeneration = 1L,
      parsedError = null
    )
    assertEquals(PlaybackPhase.IDLE, result.first)
    assertNull(result.second)
  }

  @Test
  fun testResolveEndFileState_Stop() {
    val result = PlaybackSessionStateMapper.resolveEndFileState(
      reason = PlaybackSession.EndFileReason.STOP,
      loadedGeneration = 1L,
      currentGeneration = 1L,
      activeGeneration = 1L,
      parsedError = null
    )
    assertEquals(PlaybackPhase.IDLE, result.first)
    assertNull(result.second)
  }

  @Test
  fun testResolveEndFileState_Error() {
    val result = PlaybackSessionStateMapper.resolveEndFileState(
      reason = PlaybackSession.EndFileReason.ERROR,
      loadedGeneration = 1L,
      currentGeneration = 1L,
      activeGeneration = 1L,
      parsedError = "Failed to open stream"
    )
    assertEquals(PlaybackPhase.ERROR, result.first)
    assertEquals("Failed to open stream", result.second)
  }
}
