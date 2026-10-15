package com.quantummpv.app

import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.browser.MainScreen
import com.quantummpv.app.ui.browser.measureNavigationDock
import com.quantummpv.app.ui.browser.shouldStackMiniPlayerAboveDock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerDockLayoutTest {
  @Test
  fun keepsSideBySideLayoutWhenThePlayerRetainsItsMinimumWidth() {
    assertFalse(shouldStackMiniPlayerAboveDock(containerWidth = 600.dp))
    assertFalse(shouldStackMiniPlayerAboveDock(containerWidth = 467.dp))
  }

  @Test
  fun stacksAboveDockWhenCompactLandscapeCannotFitControls() {
    assertTrue(shouldStackMiniPlayerAboveDock(containerWidth = 360.dp))
    assertTrue(shouldStackMiniPlayerAboveDock(containerWidth = 466.dp))
  }

  @Test
  fun narrowestSideBySideWindowStillReservesTheLargestPlayerLayout() {
    val containerWidth = 467.dp
    val dock =
      measureNavigationDock(
        containerWidth = containerWidth,
        tabs = MainScreen.MainTab.entries,
        maximumDockWidth = containerWidth - 16.dp - 12.dp - 12.dp - 267.dp,
      )

    // A resting landscape dock starts at its margin; the player keeps a 12 dp gap and end padding.
    val playerWidth = containerWidth - (dock.horizontalMargin + dock.dockWidth + 12.dp) - 12.dp
    assertTrue(playerWidth >= 267.dp)
  }
}
