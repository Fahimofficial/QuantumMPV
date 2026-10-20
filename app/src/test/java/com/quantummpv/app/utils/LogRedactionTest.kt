package com.quantummpv.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactionTest {
  @Test
  fun `strips query credentials and fragment`() {
    assertEquals(
      "https://jelly.lan:8096/Videos/abc/stream",
      redactUriForLog("https://jelly.lan:8096/Videos/abc/stream?api_key=SECRET&static=true#t=10"),
    )
  }

  @Test
  fun `strips user info from authority`() {
    assertEquals("smb://nas.local/share/movie.mkv", redactUriForLog("smb://user:pa%40ss@nas.local/share/movie.mkv"))
  }

  @Test
  fun `keeps plain paths and handles empty input`() {
    assertEquals("/storage/emulated/0/Movies/a.mkv", redactUriForLog("/storage/emulated/0/Movies/a.mkv"))
    assertEquals("https://host", redactUriForLog("https://u:p@host?x=1"))
    assertEquals("<none>", redactUriForLog(null))
  }
}
