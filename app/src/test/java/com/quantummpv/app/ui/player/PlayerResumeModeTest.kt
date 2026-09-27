package com.quantummpv.app.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResumeModeTest {
  @Test
  fun testEffectivelyAtEnd() {
    val duration = 100
    val lastPosition = 96
    val timeRemaining = duration - lastPosition
    val isEffectivelyAtEnd = timeRemaining in 1..5
    assertTrue("Should be effectively at end", isEffectivelyAtEnd)
  }

  @Test
  fun testNotEffectivelyAtEnd() {
    val duration = 100
    val lastPosition = 50
    val timeRemaining = duration - lastPosition
    val isEffectivelyAtEnd = timeRemaining in 1..5
    assertFalse("Should not be effectively at end", isEffectivelyAtEnd)
  }
}
