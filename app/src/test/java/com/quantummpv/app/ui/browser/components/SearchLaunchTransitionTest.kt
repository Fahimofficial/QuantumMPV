package com.quantummpv.app.ui.browser.components

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLaunchTransitionTest {
  /** Virtual 60 Hz clock so animations finish instantly in wall-clock time. */
  private class TestFrameClock : MonotonicFrameClock {
    private var nanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
      yield()
      nanos += 16_666_667L
      return onFrame(nanos)
    }
  }

  private val button = Offset(400f, 1800f)
  private val center = Offset(540f, 1000f)
  private val field = Offset(90f, 160f)

  private fun runFlight(block: suspend CoroutineScope.(scope: CoroutineScope, flight: SearchLaunchTransition<String>) -> Unit) =
    runBlocking {
      val job = Job()
      val scope = CoroutineScope(coroutineContext + job + TestFrameClock())
      try {
        block(scope, SearchLaunchTransition())
      } finally {
        job.cancel()
      }
    }

  @Test
  fun launchFliesToCenterOpensSearchThenSettlesIntoTheField() =
    runFlight { scope, flight ->
      var opened = 0
      assertTrue(flight.launch(scope, "home", button, center) {
        opened++
        flight.anchorFor("home").onLeadingIconPositioned(field)
      })
      assertEquals(SearchFlightPhase.ToCenter, flight.phase)
      assertTrue("button hides its icon while the copy travels", flight.hidesButtonIcon)
      assertTrue("field icon stays hidden until the copy lands", flight.anchorFor("home").hidesLeadingIcon)
      assertFalse("other tabs are untouched", flight.anchorFor("music").hidesLeadingIcon)
      assertFalse("repeated taps don't start a second flight", flight.launch(scope, "home", button, center) {})

      while (flight.isRunning) yield()

      assertEquals(1, opened)
      assertEquals(field.x, flight.x.value, 0.01f)
      assertEquals(field.y, flight.y.value, 0.01f)
      assertEquals(1f, flight.scale.value, 0.001f)
      assertFalse(flight.anchorFor("home").hidesLeadingIcon)
      assertEquals(1f, flight.anchorFor("home").fieldAlpha(), 0f)
    }

  @Test
  fun searchUiWithoutAnInlineBarStillCompletes() =
    runFlight { scope, flight ->
      var opened = false
      flight.launch(scope, "network", button, center) { opened = true }
      while (flight.phase != SearchFlightPhase.ToField) yield()
      assertFalse("return requires a known field icon position", flight.returnTo(scope, "network", button))
      while (flight.isRunning) yield()
      assertTrue(opened)
      assertEquals(SearchFlightPhase.Idle, flight.phase)
    }

  @Test
  fun returnDuringFieldFlightReversesFromTheCurrentIconPosition() =
    runFlight { scope, flight ->
      flight.launch(scope, "home", button, center) {
        flight.anchorFor("home").onLeadingIconPositioned(field)
      }
      while (flight.phase != SearchFlightPhase.ToField) yield()
      repeat(3) { yield() }
      val positionBeforeReturn = Offset(flight.x.value, flight.y.value)

      assertTrue(flight.returnTo(scope, "home", button))
      assertEquals(SearchFlightPhase.Returning, flight.phase)
      assertEquals(positionBeforeReturn.x, flight.x.value, 0.01f)
      assertEquals(positionBeforeReturn.y, flight.y.value, 0.01f)
      repeat(3) {
        yield()
        assertEquals(SearchFlightPhase.Returning, flight.phase)
      }
      while (flight.isRunning) yield()
      assertEquals(button.x, flight.x.value, 0.01f)
      assertEquals(button.y, flight.y.value, 0.01f)
    }

  @Test
  fun cancelRestoresEveryIconWithoutOpeningSearch() =
    runFlight { scope, flight ->
      var opened = false
      flight.launch(scope, "home", button, center) { opened = true }
      flight.cancel()
      assertEquals(SearchFlightPhase.Idle, flight.phase)
      assertFalse(flight.hidesButtonIcon)
      assertFalse(flight.anchorFor("home").hidesLeadingIcon)
      repeat(5) { yield() }
      assertFalse(opened)
    }

  @Test
  fun returnFlightNeedsAKnownFieldPositionAndEndsAtTheButton() =
    runFlight { scope, flight ->
      assertFalse(flight.returnTo(scope, "home", button))

      flight.anchorFor("home").onLeadingIconPositioned(field)
      assertTrue(flight.returnTo(scope, "home", button))
      assertEquals(SearchFlightPhase.Returning, flight.phase)
      while (flight.isRunning) yield()
      assertEquals(button.x, flight.x.value, 0.01f)
      assertEquals(button.y, flight.y.value, 0.01f)
    }
}
