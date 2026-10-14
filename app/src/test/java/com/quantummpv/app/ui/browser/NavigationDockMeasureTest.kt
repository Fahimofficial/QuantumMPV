package com.quantummpv.app.ui.browser

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDockMeasureTest {
  private val allTabs = MainScreen.MainTab.entries

  @Test
  fun narrowPhoneUsesScrollableCompactTabsAndFitsMargins() {
    val measure = measureNavigationDock(containerWidth = 360.dp, tabs = allTabs)

    assertFalse(measure.showLabels)
    assertTrue(measure.dockWidth <= 360.dp - 32.dp)
    assertTrue(measure.navWidth < 48.dp * allTabs.size)
  }

  @Test
  fun widePhoneOrTabletKeepsTheSelectedLabelTreatment() {
    val measure = measureNavigationDock(containerWidth = 600.dp, tabs = allTabs)

    assertTrue(measure.showLabels)
    assertTrue(measure.dockWidth <= 600.dp - 32.dp)
  }

  @Test
  fun userHiddenTabsShrinkThePillWithoutRemovingSearch() {
    val tabs = listOf(MainScreen.MainTab.HOME, MainScreen.MainTab.RECENTS)

    val measure = measureNavigationDock(containerWidth = 360.dp, tabs = tabs)

    assertTrue(measure.showLabels)
    assertTrue(measure.navWidth > 0.dp)
    assertEquals(measure.navWidth + 56.dp, measure.dockWidth)
  }

  @Test
  fun extremeSplitScreenShrinksMarginsBeforeTouchTargets() {
    val measure = measureNavigationDock(containerWidth = 180.dp, tabs = allTabs)

    assertFalse(measure.showLabels)
    assertEquals(14.dp, measure.horizontalMargin)
    assertEquals(96.dp, measure.navWidth)
    assertEquals(152.dp, measure.dockWidth)
    assertEquals(180.dp, measure.dockWidth + measure.horizontalMargin * 2)
  }

  @Test
  fun menuClearsPortraitOrStackedMiniPlayer() {
    assertEquals(76.dp, menuPanelBottomPadding(miniPlayerAboveDock = false))
    assertEquals(172.dp, menuPanelBottomPadding(miniPlayerAboveDock = true))
  }

  @Test
  fun menuClampsInsideTheSupported180DpSplitScreen() {
    val menu =
      measureNavigationMenu(
        containerWidth = 180.dp,
        dockStart = 14.dp,
        navigationWidth = 96.dp,
        horizontalMargin = 14.dp,
      )

    assertEquals(14.dp, menu.start)
    assertEquals(152.dp, menu.width)
    assertEquals(180.dp, menu.start + menu.width + 14.dp)
  }

  @Test
  fun landscapeMiniPlayerCapUsesCompactDockBeforePlayerSpaceIsLost() {
    val measure = measureNavigationDock(
      containerWidth = 600.dp,
      tabs = allTabs,
      maximumDockWidth = 293.dp,
    )

    assertFalse(measure.showLabels)
    assertTrue(measure.dockWidth <= 293.dp)
    assertTrue(600.dp - (16.dp + measure.dockWidth + 12.dp) - 12.dp >= 267.dp)
  }

  @Test
  fun compactAndExpandedWidthsStayStableAcrossSelectedDestinations() {
    val first = measureNavigationDock(containerWidth = 420.dp, tabs = allTabs)
    val reordered = measureNavigationDock(containerWidth = 420.dp, tabs = allTabs.reversed())

    assertEquals(first, reordered)
  }
}
