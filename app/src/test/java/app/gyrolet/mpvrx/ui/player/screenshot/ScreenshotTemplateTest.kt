package com.quantummpv.app.ui.player.screenshot

import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenshotTemplateTest {
  @Test
  fun `buildFileName expands mpv-style tokens and sanitizes invalid characters`() {
    val name = ScreenshotTemplate.buildFileName(
      template = "%f-%wH.%wM.%wS.%wT",
      extension = "webp",
      now = LocalDateTime.of(2026, 5, 28, 10, 15, 20, 123_000_000),
      mediaTitle = "Movie: A/B?C",
      positionSeconds = 90,
    )

    assertEquals("Movie_ A_B_C-10.15.20.123.webp", name)
  }

  @Test
  fun `buildFileName falls back when template becomes blank`() {
    val name = ScreenshotTemplate.buildFileName(
      template = "::::",
      extension = "png",
      now = LocalDateTime.of(2026, 1, 2, 3, 4, 5),
    )

    assertEquals("mpv_snapshot.png", name)
  }

  @Test
  fun `buildFileName caps very long names`() {
    val name = ScreenshotTemplate.buildFileName(
      template = "%f",
      extension = "jpg",
      mediaTitle = "a".repeat(300),
    )

    assertTrue(name.length <= 124)
    assertTrue(name.endsWith(".jpg"))
  }
}
