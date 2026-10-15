/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.MediaServerPreferences
import com.quantummpv.app.preferences.MusicSourceProvider
import com.quantummpv.app.preferences.PlayerPreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.components.FloatingSearchButtonSize
import com.quantummpv.app.ui.browser.folderlist.FolderListScreen
import com.quantummpv.app.ui.browser.music.MusicLibraryContent
import com.quantummpv.app.ui.browser.networkstreaming.NetworkStreamingScreen
import com.quantummpv.app.ui.browser.playlist.PlaylistScreen
import com.quantummpv.app.ui.browser.recentlyplayed.RecentlyPlayedScreen
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.NavigationAnimStyle
import com.quantummpv.app.ui.player.controls.components.rememberTvInitialFocusRequester
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.player.controls.components.tvInitialFocus
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.NavigationPager
import com.quantummpv.app.ui.utils.navigateTo
import com.quantummpv.app.ui.utils.navigationDurationMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private val DockHorizontalMargin = 16.dp
private val DockItemGap = 8.dp
private val DockBottomPadding = 12.dp
private val NavigationDockHeight = 56.dp
private val NavigationItemSize = 48.dp
private val NavigationItemSpacing = 4.dp
private val NavigationContentPadding = 4.dp
private val NavigationVerticalPadding = 4.dp
private val SideBySideMiniPlayerGap = 12.dp
private val SideBySideMiniPlayerEndPadding = 12.dp
private val PreferredMenuWidth = 208.dp
private val MinimumCompactDockWidth =
  NavigationItemSize +
    com.quantummpv.app.ui.browser.components.FloatingMenuButtonSize +
    FloatingSearchButtonSize +
    DockItemGap
private val MinimumSideBySideMiniPlayerWindowWidth =
  DockHorizontalMargin +
    MinimumCompactDockWidth +
    SideBySideMiniPlayerGap +
    SideBySideMiniPlayerEndPadding +
    NavigationBarState.landscapeMiniPlayerMinimumWidth

@Serializable
object MainScreen : Screen {
  internal enum class MainTab {
    HOME,
    MUSIC,
    RECENTS,
    PLAYLISTS,
    NETWORK,
    JELLYFIN,
  }

  /**
   * Update selection state and navigation bar visibility
   * This method should be called whenever selection changes
   */
  fun updateSelectionState(
    isInSelectionMode: Boolean,
    isOnlyVideosSelected: Boolean,
    selectionManager: Any?,
  ) {
    NavigationBarState.updateSelectionState(
      inSelectionMode = isInSelectionMode,
      onlyVideos = isOnlyVideosSelected,
    )
  }

  /**
   * Update permission state to control FAB visibility
   */
  fun updatePermissionState(isDenied: Boolean) {
    NavigationBarState.updatePermissionState(isDenied)
  }

  /**
   * Get current permission denied state
   */
  fun getPermissionDeniedState(): Boolean = NavigationBarState.isPermissionDenied

  /**
   * Update bottom navigation bar visibility based on floating bottom bar state
   */
  fun updateBottomBarVisibility(shouldShow: Boolean) {
    NavigationBarState.updateBottomBarVisibility(shouldShow)
  }

  /**
   * Displays the main browser tabs and navigation using the current appearance preferences.
   * Enables glass navigation for glass surface styles or the legacy glass navigation toggle.
   */
  @SuppressLint("ComposableNaming")
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val appearancePreferences = koinInject<AppearancePreferences>()
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerPreferences = koinInject<PlayerPreferences>()
    val navStyle by playerPreferences.appNavStyle.collectAsState()
    val animSpeed by playerPreferences.animationSpeed.collectAsState()
    val duration = navigationDurationMillis(animSpeed)
    val mainSearchController = remember { MainSearchActionController() }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var persistentSelectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val mediaServerPreferences = koinInject<MediaServerPreferences>()
    val musicSourceProvider by mediaServerPreferences.musicSourceProvider.collectAsState()
    val showMusicTab by appearancePreferences.showMusicTab.collectAsState()
    val showRecentsTab by appearancePreferences.showRecentsTab.collectAsState()
    val showPlaylistsTab by appearancePreferences.showPlaylistsTab.collectAsState()
    val showNetworkTab by appearancePreferences.showNetworkTab.collectAsState()
    val showJellyfinTab by appearancePreferences.showJellyfinTab.collectAsState()
    val legacyGlassBottomNavigation by appearancePreferences.legacyGlassBottomNavigation.collectAsState()
    val surfaceStyle = LocalGlassConfig.current.style
    val glassBottomNavigation =
      if (appearancePreferences.hasLegacyGlassBottomNavigation && !appearancePreferences.hasStoredSurfaceStyle) {
        legacyGlassBottomNavigation
      } else {
        surfaceStyle != SurfaceStyle.CLASSIC && surfaceStyle != SurfaceStyle.MINIMAL
      }
    val hideNavigationBar = NavigationBarState.shouldHideNavigationBar
    val isPermissionDenied = NavigationBarState.isPermissionDenied
    val isDualPaneFolderSelected = NavigationBarState.isDualPaneFolderSelected
    val isMiniPlayerVisible = NavigationBarState.isMiniPlayerVisible

    val visibleTabs =
      remember(
        showMusicTab,
        showRecentsTab,
        showPlaylistsTab,
        showNetworkTab,
        showJellyfinTab,
      ) {
        buildList {
          // Home is the permanent root so Back never exits directly from another tab.
          add(MainTab.HOME)
          if (showMusicTab) add(MainTab.MUSIC)
          if (showRecentsTab) add(MainTab.RECENTS)
          if (showPlaylistsTab) add(MainTab.PLAYLISTS)
          if (showNetworkTab) add(MainTab.NETWORK)
          if (showJellyfinTab) add(MainTab.JELLYFIN)
        }
      }

    val showDock = !hideNavigationBar && visibleTabs.isNotEmpty() && !isPermissionDenied

    // Track whether the floating pill nav bar is on screen so the mini player can
    // sit at the very bottom when navigating to screens without it.
    DisposableEffect(Unit) {
      onDispose {
        NavigationBarState.isNavBarVisible = false
        NavigationBarState.isMiniPlayerStackedAboveDock = false
      }
    }
    SideEffect {
      NavigationBarState.isNavBarVisible =
        backStack.lastOrNull() == MainScreen && showDock
    }

    val coroutineScope = rememberCoroutineScope()
    val disablePagerMotion = com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations()

    val initialPageIndex =
      remember(visibleTabs) {
        visibleTabs.indexOf(persistentSelectedTab).coerceAtLeast(0)
      }

    val pagerState =
      rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { visibleTabs.size },
      )
    var tabNavigationJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(pagerState, visibleTabs) {
      tabNavigationJob?.cancelAndJoin()
      tabNavigationJob = null
      if (visibleTabs.isEmpty()) {
        persistentSelectedTab = MainTab.HOME
        return@LaunchedEffect
      }

      val restorePage = visibleTabs.indexOf(persistentSelectedTab).takeIf { it >= 0 } ?: 0
      val isRestorePageSettled =
        pagerState.settledPage == restorePage &&
          pagerState.currentPage == restorePage &&
          pagerState.currentPageOffsetFraction == 0f
      if (!isRestorePageSettled) {
        pagerState.scrollToPage(restorePage)
      }

      snapshotFlow { pagerState.settledPage }
        .collect { page ->
          visibleTabs.getOrNull(page)?.let { settledTab ->
            persistentSelectedTab = settledTab
            if (settledTab != MainTab.HOME) {
              NavigationBarState.isDualPaneFolderSelected = false
            }
          }
        }
    }

    val targetPage = pagerState.targetPage.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
    val selectedTab = visibleTabs.getOrNull(targetPage) ?: visibleTabs.firstOrNull() ?: MainTab.HOME

    val onTabSelected: (MainScreen.MainTab) -> Unit = { tab ->
      val targetIndex = visibleTabs.indexOf(tab)
      val isAlreadySettled =
        targetIndex >= 0 &&
          pagerState.settledPage == targetIndex &&
          !pagerState.isScrollInProgress &&
          pagerState.currentPageOffsetFraction == 0f
      if (targetIndex >= 0) {
        tabNavigationJob?.cancel()
        if (!isAlreadySettled) {
          // Distinguish adjacent from non-adjacent tab changes to avoid jitter through
          // intermediate pages. Adjacent changes use the animated pager transition so
          // the moving indicator follows the swipe naturally. Non-adjacent jumps use
          // instant scrollToPage so the indicator animates directly to the destination
          // while the pager content changes without traversing intermediate browser pages.
          val isAdjacent = kotlin.math.abs(pagerState.settledPage - targetIndex) <= 1
          tabNavigationJob =
            coroutineScope.launch {
              if (navStyle == NavigationAnimStyle.None || !isAdjacent || disablePagerMotion) {
                pagerState.scrollToPage(targetIndex)
              } else {
                pagerState.animateScrollToPage(
                  page = targetIndex,
                  animationSpec = tween(duration, easing = FastOutSlowInEasing),
                )
              }
            }
        }
      }
    }

    val shouldReturnHome by remember(pagerState) {
      derivedStateOf {
        pagerState.settledPage != 0 || pagerState.currentPage != 0 || pagerState.isScrollInProgress
      }
    }
    // Register before page content: selection, search and nested navigation handle Back first.
    BackHandler(enabled = backStack.lastOrNull() == MainScreen && shouldReturnHome) {
      onTabSelected(MainTab.HOME)
    }

    val mainNavBar = @Composable { modifier: Modifier ->
      ExpressivePillNavigationBar(
        visibleTabs = visibleTabs,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        pagerState = pagerState,
        glass = glassBottomNavigation,
        modifier = modifier,
      )
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val jellyfinViewModel: com.quantummpv.app.ui.browser.jellyfin.JellyfinViewModel =
      androidx.lifecycle.viewmodel.compose.viewModel(
        factory =
          com.quantummpv.app.ui.browser.jellyfin.JellyfinViewModel.factory(
            context.applicationContext as android.app.Application,
          ),
      )
    val navidromeViewModel: com.quantummpv.app.ui.browser.navidrome.NavidromeViewModel =
      androidx.lifecycle.viewmodel.compose.viewModel(
        factory =
          com.quantummpv.app.ui.browser.navidrome.NavidromeViewModel.factory(
            context.applicationContext as android.app.Application,
          ),
      )

    // Scaffold with bottom navigation bar
    Scaffold(
      modifier = Modifier.fillMaxSize(),
    ) { _ ->
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // One width source decides whether the mini player sits beside or above the dock. The
        // content clearance, overflow menu and MainActivity's mini player all reuse this result.
        val containerWidth = maxWidth
        val miniPlayerStacksAboveDock =
          (isLandscape || isTablet) &&
            !isDualPaneFolderSelected &&
            shouldStackMiniPlayerAboveDock(containerWidth)
        val stackedMiniPlayer = isMiniPlayerVisible && miniPlayerStacksAboveDock
        val portraitMiniPlayerAboveDock = isMiniPlayerVisible && isPortrait && !isTablet
        SideEffect {
          NavigationBarState.isMiniPlayerStackedAboveDock = miniPlayerStacksAboveDock
        }
        // Portrait and stacked mini players sit above the pill nav bar, so screens/FABs must clear them.
        val miniPlayerNavClearance = if (portraitMiniPlayerAboveDock || stackedMiniPlayer) 96.dp else 0.dp
        val contentBottomPadding = (if (visibleTabs.isEmpty()) 0.dp else 88.dp) + miniPlayerNavClearance

        if (visibleTabs.isEmpty()) {
          CompositionLocalProvider(
            LocalNavigationBarHeight provides contentBottomPadding,
            LocalMainNavigationBar provides mainNavBar,
            LocalMainSearchActionController provides mainSearchController,
            LocalMainSearchDockVisible provides showDock,
            LocalMainSearchTab provides MainTab.HOME,
          ) {
            FolderListScreen.Content()
          }
        } else {
          CompositionLocalProvider(
            LocalNavigationBarHeight provides contentBottomPadding,
            LocalMainNavigationBar provides mainNavBar,
            LocalMainSearchActionController provides mainSearchController,
            LocalMainSearchDockVisible provides showDock,
          ) {
            NavigationPager(
              state = pagerState,
              modifier = Modifier.fillMaxSize().clipToBounds(),
              key = { page -> visibleTabs[page].name },
              beyondViewportPageCount = 1,
              userScrollEnabled = !isPermissionDenied,
            ) { page ->
              val tab = visibleTabs.getOrNull(page) ?: return@NavigationPager
              CompositionLocalProvider(LocalMainSearchTab provides tab) {
                when (tab) {
                MainTab.HOME -> FolderListScreen.Content()
                MainTab.MUSIC -> {
                  if (musicSourceProvider == MusicSourceProvider.JELLYFIN) {
                    val jellyfinUiState by jellyfinViewModel.uiState.collectAsStateWithLifecycle()
                    if (jellyfinUiState.activeServer == null) {
                      Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                      ) {
                        androidx.compose.material3.Card(
                          shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                          colors =
                            androidx.compose.material3.CardDefaults.cardColors(
                              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                          Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                          ) {
                            androidx.compose.material3.Icon(
                              painter = painterResource(R.drawable.ic_jellyfin),
                              contentDescription = null,
                              modifier = Modifier.size(56.dp),
                              tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                              text = stringResource(R.string.music_source_jellyfin),
                              style = MaterialTheme.typography.titleLarge,
                              fontWeight = FontWeight.Bold,
                            )
                            Text(
                              text = stringResource(R.string.pref_jellyfin_no_server),
                              style = MaterialTheme.typography.bodyMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            androidx.compose.material3.FilledTonalButton(
                              onClick = {
                                backStack.navigateTo(com.quantummpv.app.ui.preferences.MediaServersPreferencesScreen)
                              },
                            ) {
                              Text(stringResource(R.string.generic_configure))
                            }
                            androidx.compose.material3.TextButton(
                              onClick = {
                                mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.LOCAL)
                              },
                            ) {
                              Text(stringResource(R.string.music_source_local))
                            }
                          }
                        }
                      }
                    } else if (!jellyfinUiState.isLoading && !jellyfinUiState.hasMusicLibrary && jellyfinUiState.libraries.isNotEmpty()) {
                      Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                      ) {
                        androidx.compose.material3.Card(
                          shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                          colors =
                            androidx.compose.material3.CardDefaults.cardColors(
                              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                          Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                          ) {
                            androidx.compose.material3.Icon(
                              painter = painterResource(R.drawable.ic_jellyfin),
                              contentDescription = null,
                              modifier = Modifier.size(56.dp),
                              tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                              text = stringResource(R.string.music_source_jellyfin),
                              style = MaterialTheme.typography.titleLarge,
                              fontWeight = FontWeight.Bold,
                            )
                            Text(
                              text = stringResource(R.string.jellyfin_no_music_library),
                              style = MaterialTheme.typography.bodyMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            androidx.compose.material3.FilledTonalButton(
                              onClick = {
                                mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.LOCAL)
                              },
                            ) {
                              Text(stringResource(R.string.music_source_local))
                            }
                            androidx.compose.material3.TextButton(
                              onClick = {
                                backStack.navigateTo(com.quantummpv.app.ui.preferences.MediaServersPreferencesScreen)
                              },
                            ) {
                              Text(stringResource(R.string.generic_configure))
                            }
                          }
                        }
                      }
                    } else {
                      LaunchedEffect(jellyfinUiState.libraries) {
                        jellyfinViewModel.ensureMusicLibraryOpened()
                      }
                      com.quantummpv.app.ui.browser.jellyfin.JellyfinContent(
                        viewModel = jellyfinViewModel,
                        isMusicOnlyMode = true,
                      )
                    }
                  } else if (musicSourceProvider == MusicSourceProvider.NAVIDROME) {
                    val navidromeUiState by navidromeViewModel.uiState.collectAsStateWithLifecycle()
                    if (navidromeUiState.activeServer == null) {
                      Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                      ) {
                        androidx.compose.material3.Card(
                          shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                          colors =
                            androidx.compose.material3.CardDefaults.cardColors(
                              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                          Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                          ) {
                            androidx.compose.material3.Icon(
                              painter = painterResource(R.drawable.ic_navidrome),
                              contentDescription = null,
                              modifier = Modifier.size(56.dp),
                              tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                              text = stringResource(R.string.music_source_navidrome),
                              style = MaterialTheme.typography.titleLarge,
                              fontWeight = FontWeight.Bold,
                            )
                            Text(
                              text = stringResource(R.string.pref_navidrome_no_server),
                              style = MaterialTheme.typography.bodyMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            androidx.compose.material3.FilledTonalButton(
                              onClick = {
                                backStack.navigateTo(com.quantummpv.app.ui.preferences.MediaServersPreferencesScreen)
                              },
                            ) {
                              Text(stringResource(R.string.generic_configure))
                            }
                            androidx.compose.material3.TextButton(
                              onClick = {
                                mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.LOCAL)
                              },
                            ) {
                              Text(stringResource(R.string.music_source_local))
                            }
                          }
                        }
                      }
                    } else {
                      com.quantummpv.app.ui.browser.navidrome.NavidromeContent(
                        viewModel = navidromeViewModel,
                        isMusicOnlyMode = true,
                      )
                    }
                  } else {
                    MusicLibraryContent(
                      jellyfinViewModel = jellyfinViewModel,
                      navidromeViewModel = navidromeViewModel,
                    )
                  }
                }
                MainTab.RECENTS -> RecentlyPlayedScreen.Content()
                MainTab.PLAYLISTS -> PlaylistScreen.Content()
                MainTab.NETWORK -> NetworkStreamingScreen.Content()
                MainTab.JELLYFIN -> com.quantummpv.app.ui.browser.jellyfin.JellyfinContent(viewModel = jellyfinViewModel)
              }
              }
            }
          }
        }

        val sideBySideDockLimit =
          maxWidth -
            DockHorizontalMargin -
            SideBySideMiniPlayerGap -
            SideBySideMiniPlayerEndPadding -
            NavigationBarState.landscapeMiniPlayerMinimumWidth
        val reserveLandscapeMiniPlayer =
          isMiniPlayerVisible &&
            (isLandscape || isTablet) &&
            !isDualPaneFolderSelected &&
            !miniPlayerStacksAboveDock
        val dockMeasure =
          measureNavigationDock(
            containerWidth = maxWidth,
            tabs = visibleTabs,
            maximumDockWidth = if (reserveLandscapeMiniPlayer) sideBySideDockLimit else maxWidth,
          )
        val navWidth = dockMeasure.navWidth
        val dockWidth = dockMeasure.dockWidth
        val horizontalMargin = dockMeasure.horizontalMargin
        val showNavLabels = dockMeasure.showLabels
        val centerFraction by animateFloatAsState(
          targetValue = when {
            isDualPaneFolderSelected && selectedTab == MainTab.HOME -> 0.2f
            isMiniPlayerVisible && (isLandscape || isTablet) -> 0f
            else -> 0.5f
          },
          animationSpec =
            if (navStyle == NavigationAnimStyle.None) {
              snap()
            } else {
              com.quantummpv.app.ui.theme.AppMotion.spatial(
                com.quantummpv.app.ui.theme.AppMotion.Spatial.Standard,
                com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha,
              )
            },
          label = "dock_alignment",
        )
        val maximumStart = (containerWidth - dockWidth - horizontalMargin).coerceAtLeast(horizontalMargin)
        val dockStart =
          (containerWidth * centerFraction - dockWidth / 2).coerceIn(horizontalMargin, maximumStart)
        val menuNeedsMiniPlayerClearance = stackedMiniPlayer || portraitMiniPlayerAboveDock
        val menuPanelBottomPadding = menuPanelBottomPadding(menuNeedsMiniPlayerClearance)
        val menuMeasure =
          measureNavigationMenu(
            containerWidth = containerWidth,
            dockStart = dockStart,
            navigationWidth = navWidth,
            horizontalMargin = horizontalMargin,
          )
        val showContextualSearch =
          mainSearchController.isAvailable(selectedTab) && !mainSearchController.isSearchActive(selectedTab)

        LaunchedEffect(selectedTab, showDock) {
          menuExpanded = false
        }
        BackHandler(enabled = menuExpanded && showDock) { menuExpanded = false }

        // Dismissal/scrim is below the dock and its in-window menu, so menu rows remain tappable.
        AnimatedVisibility(
          visible = menuExpanded && showDock,
          enter = menuScrimEnter(),
          exit = menuScrimExit(),
          modifier = Modifier.matchParentSize(),
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = { menuExpanded = false },
                ),
          )
        }

        com.quantummpv.app.ui.browser.components.FloatingGlassMenuPanel(
          visible = menuExpanded && showDock,
          onSettingsClick = {
            menuExpanded = false
            backStack.navigateTo(com.quantummpv.app.ui.preferences.PreferencesScreen)
          },
          onLibraryInsightsClick = {
            menuExpanded = false
            backStack.navigateTo(com.quantummpv.app.ui.browser.insights.LibraryInsightsScreen)
          },
          glass = glassBottomNavigation,
          // The top safe-area inset and bottom clearance bound the panel's height, so short windows
          // shrink it to the space left above the dock or stacked mini player and its rows scroll.
          modifier =
            Modifier
              .align(Alignment.BottomStart)
              .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
              .navigationBarsPadding()
              .padding(bottom = menuPanelBottomPadding)
              .offset(x = menuMeasure.start)
              .width(menuMeasure.width),
        )

        // One responsive dock: scrollable navigation pill + embedded overflow, then Search.
        AnimatedVisibility(
          visible = showDock,
          enter = navigationDockEnter(navStyle, duration),
          exit = navigationDockExit(navStyle, duration),
          modifier =
            Modifier
              .fillMaxWidth()
              .align(Alignment.BottomStart)
              .navigationBarsPadding()
              .padding(bottom = DockBottomPadding),
        ) {
          Box(
              modifier =
                Modifier
                  .offset(x = dockStart)
                  .width(dockWidth)
                  .height(NavigationDockHeight)
                  .onGloballyPositioned {
                    if (!pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction == 0f) {
                      NavigationBarState.navbarLeftOffset = dockStart
                      NavigationBarState.navbarWidth = dockWidth
                    }
                  },
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(DockItemGap),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                ExpressivePillNavigationBar(
                  visibleTabs = visibleTabs,
                  selectedTab = selectedTab,
                  onTabSelected = {
                    menuExpanded = false
                    onTabSelected(it)
                  },
                  pagerState = pagerState,
                  glass = glassBottomNavigation,
                  showLabels = showNavLabels,
                  menuExpanded = menuExpanded,
                  onMenuExpandedChange = { menuExpanded = it },
                  modifier = Modifier.width(navWidth),
                )
                Box(
                  modifier = Modifier.size(FloatingSearchButtonSize),
                  contentAlignment = Alignment.Center,
                ) {
                  ContextualSearchSlot(
                    visible = showContextualSearch,
                    glass = glassBottomNavigation,
                    onSearchClick = {
                      menuExpanded = false
                      mainSearchController.request(selectedTab)
                    },
                  )
                }
              }
            }
        }
      }
    }
  }
}

/**
 * Responsive floating navigation pill. Labels expand when space permits; otherwise the 48dp tab
 * targets remain horizontally scrollable while the overflow trigger stays pinned at the end.
 * [pagerState] is the single source of truth for the travelling selected indicator.
 */
@Composable
private fun ExpressivePillNavigationBar(
  visibleTabs: List<MainScreen.MainTab>,
  selectedTab: MainScreen.MainTab,
  onTabSelected: (MainScreen.MainTab) -> Unit,
  showLabels: Boolean = true,
  menuExpanded: Boolean = false,
  onMenuExpandedChange: (Boolean) -> Unit = { },
  modifier: Modifier = Modifier,
  pagerState: PagerState? = null,
  glass: Boolean = false,
) {
  if (visibleTabs.isEmpty()) return
  val haptics = LocalHapticFeedback.current
  val initialFocusRequester = rememberTvInitialFocusRequester(visibleTabs.isNotEmpty())
  val scrollState = rememberScrollState()
  val reduceMotion = com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion()
  val disableMotion = com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations()
  val selectionScaleSpec =
    com.quantummpv.app.ui.theme.AppMotion.spatial(
      com.quantummpv.app.ui.theme.AppMotion.Spatial.Snappy,
      com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha,
    )
  val scrollSpec =
    com.quantummpv.app.ui.theme.AppMotion.spatial(
      com.quantummpv.app.ui.theme.AppMotion.Spatial.Standard,
      com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha,
    )

  val position =
    if (pagerState != null) {
      (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(
        0f,
        (visibleTabs.size - 1).toFloat(),
      )
    } else {
      visibleTabs.indexOf(selectedTab).coerceAtLeast(0).toFloat()
    }
  val tabWidths =
    visibleTabs.mapIndexed { index, tab ->
      if (showLabels) {
        val fraction = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
        androidx.compose.ui.unit.lerp(NavigationItemSize, activeTabWidth(tab), fraction)
      } else {
        NavigationItemSize
      }
    }
  val tabOffsets =
    buildList {
      var offset = NavigationContentPadding
      tabWidths.forEach { width ->
        add(offset)
        offset += width + NavigationItemSpacing
      }
    }
  val tabsContentWidth =
    tabWidths.fold(0.dp) { total, width -> total + width } +
      NavigationItemSpacing * (visibleTabs.size - 1).coerceAtLeast(0) +
      NavigationContentPadding * 2
  val pageFloor = position.toInt().coerceIn(visibleTabs.indices)
  val pageCeil = (pageFloor + 1).coerceIn(visibleTabs.indices)
  val pageFraction = (position - pageFloor).coerceIn(0f, 1f)
  val indicatorLeft = androidx.compose.ui.unit.lerp(tabOffsets[pageFloor], tabOffsets[pageCeil], pageFraction)
  val indicatorWidth = androidx.compose.ui.unit.lerp(tabWidths[pageFloor], tabWidths[pageCeil], pageFraction)

  val navigationContainer: @Composable (@Composable () -> Unit) -> Unit = { content ->
    com.quantummpv.app.ui.browser.components.NavigationDockSurface(
      glass = glass,
      shape = CircleShape,
      modifier = modifier.height(NavigationDockHeight),
      content = content,
    )
  }

  navigationContainer {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val density = LocalDensity.current
      val tabViewportWidth =
        (maxWidth - com.quantummpv.app.ui.browser.components.FloatingMenuButtonSize).coerceAtLeast(0.dp)
      val maxScroll = scrollState.maxValue
      val settledPage = pagerState?.settledPage ?: visibleTabs.indexOf(selectedTab).coerceAtLeast(0)

      LaunchedEffect(settledPage, showLabels, maxScroll, tabViewportWidth) {
        val index = settledPage.coerceIn(visibleTabs.indices)
        val selectedCenter = tabOffsets[index] + tabWidths[index] / 2
        val target =
          with(density) {
            (selectedCenter - tabViewportWidth / 2).roundToPx().coerceIn(0, maxScroll)
          }
        if (disableMotion) scrollState.scrollTo(target) else scrollState.animateScrollTo(target, scrollSpec)
      }

      Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier
              .width(tabViewportWidth)
              .height(NavigationDockHeight)
              .horizontalScroll(scrollState),
        ) {
          Box(modifier = Modifier.width(tabsContentWidth).height(NavigationDockHeight)) {
            Box(
              modifier =
                Modifier
                  .offset(x = indicatorLeft, y = NavigationVerticalPadding)
                  .width(indicatorWidth)
                  .height(NavigationItemSize)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                  .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
            )

            Row(
              modifier =
                Modifier
                  .selectableGroup()
                  .padding(
                    start = NavigationContentPadding,
                    end = NavigationContentPadding,
                    top = NavigationVerticalPadding,
                    bottom = NavigationVerticalPadding,
                  ),
              horizontalArrangement = Arrangement.spacedBy(NavigationItemSpacing),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              visibleTabs.forEachIndexed { index, tab ->
                key(tab) {
                  val activeFraction = (1f - kotlin.math.abs(position - index)).coerceIn(0f, 1f)
                  val selected = tab == selectedTab
                  val navBounceScale by animateFloatAsState(
                    targetValue = if (reduceMotion || !selected) 1f else 1.03f,
                    animationSpec = selectionScaleSpec,
                    label = "bottom_navigation_selection_scale",
                  )
                  val label = mainTabLabel(tab)
                  val contentColor =
                    androidx.compose.ui.graphics.lerp(
                      MaterialTheme.colorScheme.onSurfaceVariant,
                      MaterialTheme.colorScheme.primary,
                      activeFraction,
                    )

                  Box(
                    modifier =
                      Modifier
                        .width(tabWidths[index])
                        .height(NavigationItemSize)
                        .then(if (selected) Modifier.tvInitialFocus(initialFocusRequester) else Modifier)
                        .tvFocusHighlight(CircleShape, focusedScale = 1.06f)
                        .clip(CircleShape)
                        .selectable(
                          selected = selected,
                          role = Role.Tab,
                          interactionSource = remember { MutableInteractionSource() },
                          indication = ripple(bounded = true),
                        ) {
                          if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                          onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center,
                  ) {
                    Row(
                      modifier =
                        Modifier
                          .graphicsLayer {
                            scaleX = navBounceScale
                            scaleY = navBounceScale
                          }.padding(horizontal = 8.dp),
                      horizontalArrangement = Arrangement.Center,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      MainTabIcon(tab, contentColor, label)
                      if (showLabels && activeFraction > 0.05f) {
                        Spacer(modifier = Modifier.width(androidx.compose.ui.unit.lerp(0.dp, 6.dp, activeFraction)))
                        Text(
                          text = label,
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.Bold,
                          color = contentColor,
                          maxLines = 1,
                          softWrap = false,
                          overflow = TextOverflow.Clip,
                          modifier =
                            Modifier.graphicsLayer {
                              alpha = ((activeFraction - 0.25f) / 0.75f).coerceIn(0f, 1f)
                            },
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        com.quantummpv.app.ui.browser.components.FloatingMenuButton(
          expanded = menuExpanded,
          onExpandedChange = onMenuExpandedChange,
        )
      }
    }
  }
}

@Composable
private fun mainTabLabel(tab: MainScreen.MainTab): String =
  when (tab) {
    MainScreen.MainTab.HOME -> stringResource(R.string.ui_home)
    MainScreen.MainTab.MUSIC -> stringResource(R.string.ui_music)
    MainScreen.MainTab.RECENTS -> stringResource(R.string.ui_recents)
    MainScreen.MainTab.PLAYLISTS -> stringResource(R.string.ui_playlists)
    MainScreen.MainTab.NETWORK -> stringResource(R.string.ui_network)
    MainScreen.MainTab.JELLYFIN -> stringResource(R.string.ui_jellyfin)
  }

internal data class NavigationDockMeasure(
  val horizontalMargin: androidx.compose.ui.unit.Dp,
  val navWidth: androidx.compose.ui.unit.Dp,
  val dockWidth: androidx.compose.ui.unit.Dp,
  val showLabels: Boolean,
)

/**
 * Chooses expanded labels when possible and a scrollable compact viewport otherwise. Margins shrink
 * before a 48 dp tab target, the overflow button, or the separate Search action are compromised.
 */
internal fun measureNavigationDock(
  containerWidth: androidx.compose.ui.unit.Dp,
  tabs: List<MainScreen.MainTab>,
  maximumDockWidth: androidx.compose.ui.unit.Dp = containerWidth,
): NavigationDockMeasure {
  val layoutWidth = minOf(containerWidth, maximumDockWidth).coerceAtLeast(0.dp)
  val horizontalMargin =
    minOf(
      DockHorizontalMargin,
      (layoutWidth - MinimumCompactDockWidth).coerceAtLeast(0.dp) / 2,
    )
  val availableDockWidth = (layoutWidth - horizontalMargin * 2).coerceAtLeast(0.dp)
  val maximumNavWidth = (availableDockWidth - FloatingSearchButtonSize - DockItemGap).coerceAtLeast(0.dp)
  val expandedNavWidth = expandedNavigationWidth(tabs)
  val compactNavWidth = compactNavigationWidth(tabs)
  val showLabels = expandedNavWidth <= maximumNavWidth
  val navWidth = if (showLabels) expandedNavWidth else minOf(compactNavWidth, maximumNavWidth)
  return NavigationDockMeasure(
    horizontalMargin = horizontalMargin,
    navWidth = navWidth,
    dockWidth = navWidth + DockItemGap + FloatingSearchButtonSize,
    showLabels = showLabels,
  )
}

/**
 * True when a landscape or tablet window is too narrow to keep the largest mini-player layout beside
 * even the compact dock, so the mini player stacks above the dock instead. Wider windows cap the
 * dock in [measureNavigationDock] so the side-by-side mini player keeps its full width.
 */
internal fun shouldStackMiniPlayerAboveDock(containerWidth: androidx.compose.ui.unit.Dp): Boolean =
  containerWidth < MinimumSideBySideMiniPlayerWindowWidth

/** Bottom offset that keeps the overflow menu above the dock and any mini player stacked over it. */
internal fun menuPanelBottomPadding(miniPlayerAboveDock: Boolean): androidx.compose.ui.unit.Dp =
  if (miniPlayerAboveDock) {
    maxOf(
      DockBottomPadding + NavigationDockHeight + DockItemGap,
      NavigationBarState.navigationBarClearance +
        NavigationBarState.stackedMiniPlayerMaximumHeight +
        DockItemGap,
    )
  } else {
    DockBottomPadding + NavigationDockHeight + DockItemGap
  }

internal data class NavigationMenuMeasure(
  val start: androidx.compose.ui.unit.Dp,
  val width: androidx.compose.ui.unit.Dp,
)

/** Keeps the in-window menu fully reachable even when the dock is narrower than the panel. */
internal fun measureNavigationMenu(
  containerWidth: androidx.compose.ui.unit.Dp,
  dockStart: androidx.compose.ui.unit.Dp,
  navigationWidth: androidx.compose.ui.unit.Dp,
  horizontalMargin: androidx.compose.ui.unit.Dp,
): NavigationMenuMeasure {
  val maximumWidth = (containerWidth - horizontalMargin * 2).coerceAtLeast(0.dp)
  val width = minOf(PreferredMenuWidth, maximumWidth)
  val maximumStart = (containerWidth - horizontalMargin - width).coerceAtLeast(horizontalMargin)
  val start = (dockStart + navigationWidth - width).coerceIn(horizontalMargin, maximumStart)
  return NavigationMenuMeasure(start = start, width = width)
}

private fun activeTabWidth(tab: MainScreen.MainTab) =
  when (tab) {
    MainScreen.MainTab.HOME -> 92.dp
    MainScreen.MainTab.MUSIC -> 92.dp
    MainScreen.MainTab.RECENTS -> 104.dp
    MainScreen.MainTab.PLAYLISTS -> 108.dp
    MainScreen.MainTab.NETWORK -> 106.dp
    MainScreen.MainTab.JELLYFIN -> 100.dp
  }

/** Stable width budget using the widest active label, so selecting a tab never resizes the dock. */
private fun expandedNavigationWidth(tabs: List<MainScreen.MainTab>) =
  (tabs.maxOfOrNull { activeTabWidth(it) } ?: 0.dp) +
    NavigationItemSize * (tabs.size - 1).coerceAtLeast(0) +
    NavigationItemSpacing * (tabs.size - 1).coerceAtLeast(0) +
    NavigationContentPadding * 2 +
    com.quantummpv.app.ui.browser.components.FloatingMenuButtonSize

private fun compactNavigationWidth(tabs: List<MainScreen.MainTab>) =
  NavigationItemSize * tabs.size +
    NavigationItemSpacing * (tabs.size - 1).coerceAtLeast(0) +
    NavigationContentPadding * 2 +
    com.quantummpv.app.ui.browser.components.FloatingMenuButtonSize

@Composable
private fun menuScrimEnter(): EnterTransition =
  when {
    com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations() -> EnterTransition.None
    com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion() ->
      fadeIn(animationSpec = com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha)
    else -> fadeIn(animationSpec = com.quantummpv.app.ui.theme.AppMotion.Effect.Alpha)
  }

@Composable
private fun menuScrimExit(): ExitTransition =
  when {
    com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations() -> ExitTransition.None
    com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion() ->
      fadeOut(animationSpec = com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha)
    else -> fadeOut(animationSpec = com.quantummpv.app.ui.theme.AppMotion.Effect.Alpha)
  }

@Composable
private fun navigationDockEnter(
  navStyle: NavigationAnimStyle,
  duration: Int,
): EnterTransition =
  when {
    navStyle == NavigationAnimStyle.None || com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations() ->
      EnterTransition.None
    com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion() ->
      fadeIn(animationSpec = com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha)
    else ->
      slideInVertically(
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        initialOffsetY = { fullHeight -> fullHeight * 2 },
      ) + fadeIn(tween(duration))
  }

@Composable
private fun navigationDockExit(
  navStyle: NavigationAnimStyle,
  duration: Int,
): ExitTransition =
  when {
    navStyle == NavigationAnimStyle.None || com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations() ->
      ExitTransition.None
    com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion() ->
      fadeOut(animationSpec = com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha)
    else ->
      slideOutVertically(
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        targetOffsetY = { fullHeight -> fullHeight * 2 },
      ) + fadeOut(tween(duration))
  }

@Composable
private fun ContextualSearchSlot(
  visible: Boolean,
  glass: Boolean,
  onSearchClick: () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter = contextualSearchEnter(),
    exit = contextualSearchExit(),
  ) {
    com.quantummpv.app.ui.browser.components.FloatingSearchButton(onSearchClick = onSearchClick, glass = glass)
  }
}

@Composable
private fun contextualSearchEnter(): EnterTransition =
  when {
    com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations() -> EnterTransition.None
    com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion() ->
      fadeIn(animationSpec = com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha)
    else ->
      scaleIn(animationSpec = com.quantummpv.app.ui.theme.AppMotion.Spatial.Snappy, initialScale = 0.9f) +
        fadeIn(animationSpec = com.quantummpv.app.ui.theme.AppMotion.Effect.Alpha)
  }

@Composable
private fun contextualSearchExit(): ExitTransition =
  when {
    com.quantummpv.app.ui.theme.AppMotion.shouldDisableAnimations() -> ExitTransition.None
    com.quantummpv.app.ui.theme.AppMotion.shouldReduceMotion() ->
      fadeOut(animationSpec = com.quantummpv.app.ui.theme.AppMotion.ReducedAlpha)
    else ->
      scaleOut(animationSpec = com.quantummpv.app.ui.theme.AppMotion.Spatial.Snappy, targetScale = 0.9f) +
        fadeOut(animationSpec = com.quantummpv.app.ui.theme.AppMotion.Effect.Alpha)
  }

@Composable
private fun MainTabIcon(
  tab: MainScreen.MainTab,
  tint: Color,
  contentDescription: String?,
) {
  val icon = when (tab) {
    MainScreen.MainTab.HOME -> Icons.RoundedFilled.Home
    MainScreen.MainTab.MUSIC -> Icons.RoundedFilled.Audiotrack
    MainScreen.MainTab.RECENTS -> Icons.RoundedFilled.History
    MainScreen.MainTab.PLAYLISTS -> Icons.RoundedFilled.PlaylistPlay
    MainScreen.MainTab.NETWORK -> Icons.RoundedFilled.BringYourOwnIp
    MainScreen.MainTab.JELLYFIN -> null
  }
  if (icon == null) {
    androidx.compose.material3.Icon(
      painter = painterResource(R.drawable.ic_jellyfin),
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
  } else {
    Icon(
      icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
  }
}

val LocalNavigationBarHeight = compositionLocalOf { 0.dp }

// CompositionLocal for main navigation bar
val LocalMainNavigationBar =
  compositionLocalOf<@Composable (Modifier) -> Unit> {
    { }
  }
