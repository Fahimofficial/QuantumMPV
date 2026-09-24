package com.quantummpv.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionTest {
  // Pure function logic from PlaybackSession for parsing MPV errors
  private fun parseEndFileError(data: String): String? {
    if (data.isBlank()) return null
    return data.trim()
  }

  @Test
  fun testParseEndFileError_ErrorParsing() {
    val errorString = "End of file error"
    assertEquals("End of file error", parseEndFileError(errorString))
  }

  @Test
  fun testParseEndFileError_Empty() {
    assertNull(parseEndFileError(""))
  }
}
