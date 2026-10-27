package com.quantummpv.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Callbacks that can fire after the player activity is gone (Syncplay remote pause/resume, frame
 * stepping, subtitle search) must not dereference the strict `host` getter, which throws once the
 * host is detached. This guards the source against regressions without needing a device.
 */
class PlayerViewModelHostTest {
  private val source =
    File("src/main/java/com/quantummpv/app/ui/player/PlayerViewModel.kt").readText()

  @Test
  fun deferredAudioFocusCallsUseTheNullableHost() {
    val strictDeferred = Regex("""withContext\(Dispatchers\.Main[^)]*\)\s*\{\s*host\.""").findAll(source).count()
    assertEquals("coroutine blocks must use hostOrNull", 0, strictDeferred)
  }
}
