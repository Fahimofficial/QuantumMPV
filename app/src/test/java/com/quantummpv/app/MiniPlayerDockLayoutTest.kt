package com.quantummpv.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerDockLayoutTest {
  @Test
  fun keepsSideBySideLayoutWhenThePlayerRetainsItsMinimumWidth() {
    assertFalse(
      shouldStackMiniPlayerAboveDock(
        isNavBarOnScreen = true,
        screenWidth = 600.dp,
        navBarLeft = 16.dp,
        navBarWidth = 332.dp,
      ),
    )
  }

  @Test
  fun stacksAboveDockWhenCompactLandscapeCannotFitControls() {
    assertTrue(
      shouldStackMiniPlayerAboveDock(
        isNavBarOnScreen = true,
        screenWidth = 360.dp,
        navBarLeft = 16.dp,
        navBarWidth = 328.dp,
      ),
    )
  }

  @Test
  fun doesNotStackWhenThereIsNoNavigationDock() {
    assertFalse(
      shouldStackMiniPlayerAboveDock(
        isNavBarOnScreen = false,
        screenWidth = 360.dp,
        navBarLeft = 16.dp,
        navBarWidth = 328.dp,
      ),
    )
  }
}
