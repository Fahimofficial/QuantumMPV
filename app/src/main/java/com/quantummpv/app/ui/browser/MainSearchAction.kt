/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Routes the floating Search button to the existing search UI owned by each main tab.
 * Registrations are keyed by tab because adjacent pager pages remain composed offscreen.
 */
@Stable
internal class MainSearchActionController {
  private data class Registration(
    val owner: Any,
    val isSearchActive: Boolean,
    val action: () -> Unit,
  )

  private val registrations = mutableStateMapOf<MainScreen.MainTab, Registration>()

  /** True when [tab] currently has a composed search implementation. */
  fun isAvailable(tab: MainScreen.MainTab): Boolean = registrations.containsKey(tab)

  /** True when [tab]'s inline search field is already visible. */
  fun isSearchActive(tab: MainScreen.MainTab): Boolean = registrations[tab]?.isSearchActive == true

  /** Invokes [tab]'s current search action, returning false if the tab has no registered search. */
  fun request(tab: MainScreen.MainTab): Boolean = registrations[tab]?.action?.let {
    it()
    true
  } ?: false

  /** Replaces the current action for [tab]; [owner] prevents an old page from clearing a newer one. */
  fun register(
    tab: MainScreen.MainTab,
    owner: Any,
    isSearchActive: Boolean,
    action: () -> Unit,
  ) {
    registrations[tab] = Registration(owner, isSearchActive, action)
  }

  /** Removes [tab]'s action only when it still belongs to [owner]. */
  fun unregister(
    tab: MainScreen.MainTab,
    owner: Any,
  ) {
    if (registrations[tab]?.owner === owner) registrations.remove(tab)
  }
}

/** Present only around pages hosted by [MainScreen]. */
internal val LocalMainSearchActionController = staticCompositionLocalOf<MainSearchActionController?> { null }

/** Identifies the main tab an offscreen pager page belongs to. */
internal val LocalMainSearchTab = staticCompositionLocalOf<MainScreen.MainTab?> { null }

/** True only while MainScreen renders the floating dock that owns contextual Search. */
internal val LocalMainSearchDockVisible = staticCompositionLocalOf { false }

/**
 * Registers [onSearch] with the floating Search button when this screen is inside [MainScreen].
 * [isSearchActive] hides that button while the screen's own search field is visible. Returns null
 * inside MainScreen so the top bar does not show a duplicate Search action; outside MainScreen the
 * original action is returned unchanged.
 */
@Composable
internal fun mainTabSearchAction(
  isSearchActive: Boolean,
  enabled: Boolean = true,
  onSearch: () -> Unit,
): (() -> Unit)? {
  val controller = LocalMainSearchActionController.current
  val tab = LocalMainSearchTab.current
  val dockVisible = LocalMainSearchDockVisible.current
  val latestAction = rememberUpdatedState(onSearch)
  val owner = remember { Any() }
  val dockOwnsSearch = controller != null && tab != null && dockVisible && enabled

  DisposableEffect(controller, tab, owner, isSearchActive, dockOwnsSearch) {
    if (controller != null && tab != null && dockVisible && enabled) {
      controller.register(tab, owner, isSearchActive) { latestAction.value() }
    }
    onDispose {
      if (controller != null && tab != null) controller.unregister(tab, owner)
    }
  }

  return if (dockOwnsSearch) null else onSearch
}
