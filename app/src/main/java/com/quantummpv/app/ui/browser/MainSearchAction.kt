/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.quantummpv.app.ui.theme.AppMotion
import com.quantummpv.app.ui.utils.LocalInlineSearchMorphModifier
import com.quantummpv.app.ui.utils.LocalSharedTransitionScope
import com.quantummpv.app.ui.utils.inlineSearchMorphKey
import com.quantummpv.app.ui.utils.rememberSearchMorphModifier

/**
 * Routes the floating Search button to the existing search UI owned by each main tab.
 * Registrations are keyed by tab because adjacent pager pages remain composed offscreen.
 */
@Stable
internal class MainSearchActionController {
  private data class Registration(
    val owner: Any,
    val isSearchActive: Boolean,
    val opensScreen: Boolean,
    val action: () -> Unit,
  )

  private val registrations = mutableStateMapOf<MainScreen.MainTab, Registration>()

  // Set in the same snapshot as the tab's own "search open" state, so the dock button starts its
  // exit in the very frame the inline bar starts entering. Without it the button would only hide
  // after the tab re-registers (one frame later) and the shared-element morph would see two
  // visible ends. Cleared as soon as the tab registers its new state.
  private val pendingInlineOpen = mutableStateMapOf<MainScreen.MainTab, Boolean>()

  /** True when [tab] currently has a composed search implementation. */
  fun isAvailable(tab: MainScreen.MainTab): Boolean = registrations.containsKey(tab)

  /** True when [tab]'s inline search field is already visible. */
  fun isSearchActive(tab: MainScreen.MainTab): Boolean =
    registrations[tab]?.isSearchActive == true || pendingInlineOpen[tab] == true

  /**
   * True when [tab]'s search is a separate destination. The dock button then morphs into that
   * screen's search bar through a shared element instead of flying its icon into an inline field.
   */
  fun opensScreen(tab: MainScreen.MainTab): Boolean = registrations[tab]?.opensScreen == true

  /** Invokes [tab]'s current search action, returning false if the tab has no registered search. */
  fun request(tab: MainScreen.MainTab): Boolean {
    val registration = registrations[tab] ?: return false
    if (!registration.opensScreen) pendingInlineOpen[tab] = true
    registration.action()
    return true
  }

  /** Replaces the current action for [tab]; [owner] prevents an old page from clearing a newer one. */
  fun register(
    tab: MainScreen.MainTab,
    owner: Any,
    isSearchActive: Boolean,
    opensScreen: Boolean = false,
    action: () -> Unit,
  ) {
    registrations[tab] = Registration(owner, isSearchActive, opensScreen, action)
    pendingInlineOpen.remove(tab)
  }

  /** Removes [tab]'s action only when it still belongs to [owner]. */
  fun unregister(
    tab: MainScreen.MainTab,
    owner: Any,
  ) {
    if (registrations[tab]?.owner === owner) {
      registrations.remove(tab)
      pendingInlineOpen.remove(tab)
    }
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
 * original action is returned unchanged. Set [opensScreen] when [onSearch] navigates to a search
 * destination rather than revealing an inline field.
 */
@Composable
internal fun mainTabSearchAction(
  isSearchActive: Boolean,
  enabled: Boolean = true,
  opensScreen: Boolean = false,
  onSearch: () -> Unit,
): (() -> Unit)? {
  val controller = LocalMainSearchActionController.current
  val tab = LocalMainSearchTab.current
  val dockVisible = LocalMainSearchDockVisible.current
  val latestAction = rememberUpdatedState(onSearch)
  val owner = remember { Any() }
  val dockOwnsSearch = controller != null && tab != null && dockVisible && enabled

  DisposableEffect(controller, tab, owner, isSearchActive, opensScreen, dockOwnsSearch) {
    if (controller != null && tab != null && dockVisible && enabled) {
      controller.register(tab, owner, isSearchActive, opensScreen) { latestAction.value() }
    }
    onDispose {
      if (controller != null && tab != null) controller.unregister(tab, owner)
    }
  }

  return if (dockOwnsSearch) null else onSearch
}

/**
 * Swaps a main tab's top bar for its inline search bar. Inside MainScreen, while the dock owns
 * Search, the bar morphs out of (and back into) the dock's Search button through the same
 * shared-element transition Home uses for MediaSearchScreen, so every tab opens Search the same way.
 * Elsewhere it is a plain swap, exactly as before. The InlineSearchBar inside [searchBar] picks the
 * morph up through [LocalInlineSearchMorphModifier].
 */
@Composable
internal fun DockSearchTopBarSwitcher(
  searchActive: Boolean,
  topBar: @Composable () -> Unit,
  searchBar: @Composable () -> Unit,
) {
  val tab = LocalMainSearchTab.current
  val sharedTransitionScope = LocalSharedTransitionScope.current
  val dockOwnsSearch = tab != null && LocalMainSearchDockVisible.current && LocalMainSearchActionController.current != null
  if (!dockOwnsSearch || sharedTransitionScope == null || AppMotion.shouldDisableAnimations()) {
    if (searchActive) searchBar() else topBar()
    return
  }
  val fadeSpec = AppMotion.spatial(AppMotion.Effect.Alpha, AppMotion.ReducedAlpha)
  AnimatedContent(
    targetState = searchActive,
    transitionSpec = {
      val transform =
        if (targetState) {
          // The bar arrives through the morph; only the outgoing title fades.
          EnterTransition.None togetherWith fadeOut(fadeSpec)
        } else {
          // Keep the bar composed while it morphs back into the button.
          fadeIn(fadeSpec) togetherWith ExitTransition.KeepUntilTransitionsFinished
        }
      transform using SizeTransform(clip = false)
    },
    label = "dock_search_top_bar",
  ) { active ->
    if (active) {
      val morph = rememberSearchMorphModifier(sharedTransitionScope, this, inlineSearchMorphKey(tab.name))
      CompositionLocalProvider(LocalInlineSearchMorphModifier provides morph, content = searchBar)
    } else {
      topBar()
    }
  }
}
