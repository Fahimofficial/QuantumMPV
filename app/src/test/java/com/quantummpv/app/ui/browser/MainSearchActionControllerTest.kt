package com.quantummpv.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainSearchActionControllerTest {
  @Test
  fun routesRequestsToTheRequestedTabOnly() {
    val controller = MainSearchActionController()
    val homeOwner = Any()
    val musicOwner = Any()
    var homeRequests = 0
    var musicRequests = 0

    controller.register(MainScreen.MainTab.HOME, homeOwner, isSearchActive = false) { homeRequests++ }
    controller.register(MainScreen.MainTab.MUSIC, musicOwner, isSearchActive = false) { musicRequests++ }

    assertTrue(controller.request(MainScreen.MainTab.MUSIC))
    assertEquals(0, homeRequests)
    assertEquals(1, musicRequests)
  }

  @Test
  fun reportsAvailabilityAndInlineSearchVisibility() {
    val controller = MainSearchActionController()
    val owner = Any()

    assertFalse(controller.isAvailable(MainScreen.MainTab.PROFILE))
    controller.register(MainScreen.MainTab.PROFILE, owner, isSearchActive = true) { }

    assertTrue(controller.isAvailable(MainScreen.MainTab.PROFILE))
    assertTrue(controller.isSearchActive(MainScreen.MainTab.PROFILE))
  }

  @Test
  fun stalePageCannotUnregisterNewerActionForTheSameTab() {
    val controller = MainSearchActionController()
    val oldOwner = Any()
    val newOwner = Any()
    var requests = 0

    controller.register(MainScreen.MainTab.HOME, oldOwner, isSearchActive = false) { requests = -1 }
    controller.register(MainScreen.MainTab.HOME, newOwner, isSearchActive = false) { requests++ }
    controller.unregister(MainScreen.MainTab.HOME, oldOwner)

    assertTrue(controller.request(MainScreen.MainTab.HOME))
    assertEquals(1, requests)
    assertTrue(controller.isAvailable(MainScreen.MainTab.HOME))
  }

  @Test
  fun currentOwnerCanUnregisterItsAction() {
    val controller = MainSearchActionController()
    val owner = Any()

    controller.register(MainScreen.MainTab.NETWORK, owner, isSearchActive = false) { }
    controller.unregister(MainScreen.MainTab.NETWORK, owner)

    assertFalse(controller.isAvailable(MainScreen.MainTab.NETWORK))
    assertFalse(controller.request(MainScreen.MainTab.NETWORK))
  }

  @Test
  fun inlineRequestHidesTheButtonUntilTheTabReportsItsSearch() {
    val controller = MainSearchActionController()
    val owner = Any()
    controller.register(MainScreen.MainTab.MUSIC, owner, isSearchActive = false) { }

    // Hidden in the same snapshot as the request, so the morph never sees two visible ends.
    assertTrue(controller.request(MainScreen.MainTab.MUSIC))
    assertTrue(controller.isSearchActive(MainScreen.MainTab.MUSIC))

    controller.register(MainScreen.MainTab.MUSIC, owner, isSearchActive = true) { }
    assertTrue(controller.isSearchActive(MainScreen.MainTab.MUSIC))
    controller.register(MainScreen.MainTab.MUSIC, owner, isSearchActive = false) { }
    assertFalse(controller.isSearchActive(MainScreen.MainTab.MUSIC))
  }

  @Test
  fun screenRequestKeepsTheButtonForTheNavigationMorph() {
    val controller = MainSearchActionController()
    controller.register(MainScreen.MainTab.HOME, Any(), isSearchActive = false, opensScreen = true) { }

    assertTrue(controller.request(MainScreen.MainTab.HOME))
    assertFalse(controller.isSearchActive(MainScreen.MainTab.HOME))
  }
}
