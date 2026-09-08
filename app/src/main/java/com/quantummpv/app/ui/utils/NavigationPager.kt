/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.utils

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.quantummpv.app.preferences.PlayerPreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.player.NavigationAnimStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Page interaction is separate from the host lifecycle: swiping must not resume/rescan a library. */
internal val LocalNavigationPageActive = compositionLocalOf { true }

// A full-page horizontal gesture has one owner. Nested category tabs remain tappable.
private val LocalNavigationPagerPresent = staticCompositionLocalOf { false }
private val PassThroughPageScrollConnection = object : NestedScrollConnection {}

@Composable
internal fun NavigationBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  BackHandler(enabled = enabled && LocalNavigationPageActive.current, onBack = onBack)
}

/** Native, finger-following page scrolling; screen-transition effects never change swipe physics. */
@Composable
internal fun NavigationPager(
  state: PagerState,
  modifier: Modifier = Modifier,
  beyondViewportPageCount: Int = 1,
  userScrollEnabled: Boolean = true,
  key: ((Int) -> Any)? = null,
  content: @Composable PagerScope.(Int) -> Unit,
) {
  val isNestedPager = LocalNavigationPagerPresent.current
  val ownsHorizontalSwipes = userScrollEnabled && !isNestedPager
  val nestedScrollConnection = if (ownsHorizontalSwipes) {
    PagerDefaults.pageNestedScrollConnection(state, Orientation.Horizontal)
  } else {
    // Disabling drag alone does not disable a pager's nested-scroll/fling consumption.
    PassThroughPageScrollConnection
  }
  CompositionLocalProvider(LocalNavigationPagerPresent provides true) {
    HorizontalPager(
      state = state,
      modifier = modifier.clipToBounds(),
      // Do not multiply offscreen library composition across outer and inner pagers.
      beyondViewportPageCount = if (isNestedPager) 0 else beyondViewportPageCount,
      userScrollEnabled = ownsHorizontalSwipes,
      pageNestedScrollConnection = nestedScrollConnection,
      overscrollEffect = null,
      key = key,
      // Keep HorizontalPager's velocity-aware fling and settling defaults in both directions.
    ) { page ->
      BrowserTabPage(state, page) { content(page) }
    }
  }
}

/** A new tab request cancels the previous one instead of launching competing scrolls. */
@Composable
internal fun rememberTabNavigation(state: PagerState): (Int) -> Unit {
  val preferences = koinInject<PlayerPreferences>()
  val style by preferences.appNavStyle.collectAsState()
  val speed by preferences.animationSpeed.collectAsState()
  val scope = rememberCoroutineScope()
  var job by remember(state) { mutableStateOf<Job?>(null) }
  return { page ->
    if (page in 0 until state.pageCount) {
      val isAlreadySettled = state.currentPage == page &&
        state.currentPageOffsetFraction == 0f && !state.isScrollInProgress
      job?.cancel()
      if (!isAlreadySettled) {
        job = scope.launch {
          if (style == NavigationAnimStyle.None) {
            state.scrollToPage(page)
          } else {
            state.animateScrollToPage(page, animationSpec = tween(navigationDurationMillis(speed), easing = FastOutSlowInEasing))
          }
        }
      }
    }
  }
}

@Composable
private fun BrowserTabPage(
  pagerState: PagerState,
  page: Int,
  content: @Composable () -> Unit,
) {
  val parentActive = LocalNavigationPageActive.current
  val isActive by remember(pagerState, page, parentActive) {
    derivedStateOf { parentActive && pagerState.settledPage == page }
  }
  // Keep data collection and resume observers on the real screen lifecycle. A synthetic
  // CREATED -> RESUMED transition here used to restart them at the end of every swipe.
  CompositionLocalProvider(LocalNavigationPageActive provides isActive) {
    Box(
      Modifier
        .fillMaxSize()
        .focusProperties { canFocus = isActive }
        .semantics { if (!isActive) hideFromAccessibility() },
    ) {
      content()
    }
  }
}
