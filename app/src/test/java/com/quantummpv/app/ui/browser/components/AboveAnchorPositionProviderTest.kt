package com.quantummpv.app.ui.browser.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class AboveAnchorPositionProviderTest {
  private val provider = AboveAnchorPositionProvider(gapPx = 10, marginPx = 16)
  private val window = IntSize(1000, 2000)
  private val popup = IntSize(300, 200)

  private fun position(
    anchor: IntRect,
    direction: LayoutDirection = LayoutDirection.Ltr,
    content: IntSize = popup,
    windowSize: IntSize = window,
  ): IntOffset = provider.calculatePosition(anchor, windowSize, direction, content)

  @Test
  fun opensAboveTheAnchorAndEndAlignsWithIt() {
    val anchor = IntRect(left = 700, top = 1500, right = 800, bottom = 1600)

    val result = position(anchor)

    assertEquals(IntOffset(x = 800 - 300, y = 1500 - 200 - 10), result)
  }

  @Test
  fun flipsBelowTheAnchorWhenThereIsNoRoomAbove() {
    val anchor = IntRect(left = 700, top = 100, right = 800, bottom = 200)

    val result = position(anchor)

    assertEquals(200 + 10, result.y)
  }

  @Test
  fun staysInsideTheLeftEdgeWhenTheAnchorIsNearIt() {
    val anchor = IntRect(left = 0, top = 1500, right = 100, bottom = 1600)

    val result = position(anchor)

    assertEquals(16, result.x)
  }

  @Test
  fun staysInsideTheRightEdgeWhenTheAnchorOverhangsIt() {
    val anchor = IntRect(left = 950, top = 1500, right = 1100, bottom = 1600)

    val result = position(anchor)

    assertEquals(1000 - 300 - 16, result.x)
  }

  @Test
  fun startAlignsInRightToLeftLayouts() {
    val anchor = IntRect(left = 200, top = 1500, right = 300, bottom = 1600)

    val result = position(anchor, direction = LayoutDirection.Rtl)

    assertEquals(200, result.x)
  }

  @Test
  fun clampsToTheBottomMarginWhenNeitherAboveNorBelowFits() {
    // A tall anchor leaves no room above; opening below would overflow the window bottom.
    val anchor = IntRect(left = 700, top = 100, right = 800, bottom = 1990)

    val result = position(anchor, content = IntSize(300, 300))

    assertEquals(2000 - 300 - 16, result.y)
  }

  @Test
  fun aPopupLargerThanTheWindowPinsToTheMarginInsteadOfGoingNegative() {
    val anchor = IntRect(left = 10, top = 10, right = 60, bottom = 60)

    val result = position(anchor, content = IntSize(2000, 3000))

    assertEquals(IntOffset(16, 16), result)
  }
}
