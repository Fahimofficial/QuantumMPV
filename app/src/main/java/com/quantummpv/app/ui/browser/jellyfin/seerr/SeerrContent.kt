/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.jellyfin.seerr

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantummpv.app.R
import com.quantummpv.app.domain.jellyfin.JellyfinServer
import com.quantummpv.app.domain.seerr.MediaType
import com.quantummpv.app.presentation.components.pullrefresh.PullRefreshBox
import com.quantummpv.app.ui.browser.components.BrowserTopBar
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeerrContent(
  viewModel: SeerrViewModel,
  activeJellyfinServer: JellyfinServer?,
  onBackClick: () -> Unit,
  onOpenJellyfinItem: (jellyfinId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current

  var isSearching by rememberSaveable { mutableStateOf(false) }
  val searchFocusRequester = remember { FocusRequester() }

  LaunchedEffect(uiState.actionMessage) {
    uiState.actionMessage?.let { msg ->
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      viewModel.clearActionMessage()
    }
  }

  BackHandler(enabled = isSearching || uiState.isDetailSheetOpen || uiState.isConnectionDialogOpen) {
    when {
      uiState.isDetailSheetOpen -> viewModel.closeDetail()
      uiState.isConnectionDialogOpen -> viewModel.closeConnectionDialog()
      isSearching -> {
        isSearching = false
        viewModel.clearSearch()
      }
    }
  }

  val headerContainerColor =
    if (MaterialTheme.colorScheme.background == Color.Black) Color.Black else MaterialTheme.colorScheme.surfaceContainer

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // Top Bar Container
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(headerContainerColor),
    ) {
      if (isSearching) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SearchBar(
            inputField = {
              SearchBarDefaults.InputField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                onSearch = viewModel::performSearch,
                expanded = false,
                onExpandedChange = { },
                placeholder = { Text(stringResource(R.string.seerr_search_placeholder)) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.RoundedFilled.Search,
                    contentDescription = null,
                  )
                },
                trailingIcon = {
                  IconButton(
                    onClick = {
                      if (uiState.searchQuery.isNotEmpty()) {
                        viewModel.clearSearch()
                      } else {
                        isSearching = false
                      }
                    },
                  ) {
                    Icon(
                      imageVector = Icons.RoundedFilled.Close,
                      contentDescription = stringResource(R.string.generic_cancel),
                    )
                  }
                },
                modifier = Modifier.focusRequester(searchFocusRequester),
              )
            },
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
          ) { }
        }
        LaunchedEffect(Unit) {
          searchFocusRequester.requestFocus()
        }
      } else {
        BrowserTopBar(
          title = stringResource(R.string.seerr_requests),
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onCancelSelection = { },
          onBackClick = onBackClick,
          onSearchClick = { isSearching = true },
          additionalActions = {
            IconButton(
              onClick = { viewModel.openConnectionDialog() },
              modifier = Modifier.padding(horizontal = 2.dp),
            ) {
              Icon(
                imageVector = if (uiState.isConnected) Icons.RoundedFilled.CloudDone else Icons.RoundedFilled.CloudOff,
                contentDescription = stringResource(R.string.seerr_connect_server),
                tint = if (uiState.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
              )
            }
          },
        )
      }

      // Filter Chips
      if (!isSearching && uiState.isConnected) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilterChip(
            selected = uiState.selectedTab == SeerrMainTab.ALL,
            onClick = { viewModel.setTab(SeerrMainTab.ALL) },
            label = { Text(stringResource(R.string.seerr_tab_all)) },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
          FilterChip(
            selected = uiState.selectedTab == SeerrMainTab.MOVIES,
            onClick = { viewModel.setTab(SeerrMainTab.MOVIES) },
            label = { Text(stringResource(R.string.seerr_tab_movies)) },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
          FilterChip(
            selected = uiState.selectedTab == SeerrMainTab.TV,
            onClick = { viewModel.setTab(SeerrMainTab.TV) },
            label = { Text(stringResource(R.string.seerr_tab_tv)) },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
          FilterChip(
            selected = uiState.selectedTab == SeerrMainTab.MY_REQUESTS,
            onClick = { viewModel.setTab(SeerrMainTab.MY_REQUESTS) },
            label = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.seerr_tab_my_requests))
                if (uiState.activeRequests.isNotEmpty()) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = uiState.activeRequests.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                      )
                    }
                  }
                }
              }
            },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }
    }

    // Main Content
    val isRefreshing = remember { mutableStateOf(false) }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .weight(1f),
    ) {
      if (!uiState.isConnected) {
        // Not Connected CTA
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.RoundedFilled.AddToQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                  )
                }
              }

              Text(
                text = stringResource(R.string.seerr_not_connected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
              )

              Text(
                text = stringResource(R.string.seerr_connect_cta),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )

              Button(
                onClick = { viewModel.openConnectionDialog() },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.RoundedFilled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.seerr_connect_server), fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      } else if (isSearching) {
        // Search Results View
        if (uiState.isSearching) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        } else if (uiState.searchResults.isEmpty() && uiState.searchQuery.isNotBlank()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
              text = "No results found for \"${uiState.searchQuery}\"",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(uiState.searchResults, key = { "${it.mediaType}_${it.id}" }) { item ->
              SeerrMediaCard(
                item = item,
                onClick = { viewModel.openDetail(item) },
                cardWidth = 140.dp,
              )
            }
          }
        }
      } else {
        // Dashboard with PullRefresh
        PullRefreshBox(
          isRefreshing = isRefreshing,
          onRefresh = { viewModel.loadDashboard() },
          modifier = Modifier.fillMaxSize(),
        ) {
          LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            // Active Requests Carousel
            if (uiState.selectedTab == SeerrMainTab.ALL || uiState.selectedTab == SeerrMainTab.MY_REQUESTS) {
              if (uiState.activeRequests.isNotEmpty()) {
                item {
                  Column(modifier = Modifier.fillMaxWidth()) {
                    SeerrSectionHeader(title = stringResource(R.string.seerr_active_requests))
                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      items(uiState.activeRequests, key = { it.id }) { req ->
                        SeerrRequestCard(
                          request = req,
                          baseUrl = uiState.serverUrl,
                          isAdmin = uiState.currentUser?.isAdmin() == true,
                          onClick = { viewModel.openDetailFromRequest(req) },
                          onApprove = { viewModel.approveRequest(req.id) },
                          onDecline = { viewModel.declineRequest(req.id) },
                          onDelete = { viewModel.deleteRequest(req.id, req.media.tmdbId, req.media.mediaType) },
                        )
                      }
                    }
                  }
                }
              }

              // Available Requests Carousel
              if (uiState.availableRequests.isNotEmpty()) {
                item {
                  Column(modifier = Modifier.fillMaxWidth()) {
                    SeerrSectionHeader(title = stringResource(R.string.seerr_available_requests))
                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      items(uiState.availableRequests, key = { it.id }) { req ->
                        SeerrRequestCard(
                          request = req,
                          baseUrl = uiState.serverUrl,
                          isAdmin = uiState.currentUser?.isAdmin() == true,
                          onClick = { viewModel.openDetailFromRequest(req) },
                          onApprove = { viewModel.approveRequest(req.id) },
                          onDecline = { viewModel.declineRequest(req.id) },
                          onDelete = { viewModel.deleteRequest(req.id, req.media.tmdbId, req.media.mediaType) },
                        )
                      }
                    }
                  }
                }
              }
            }

            if (uiState.selectedTab != SeerrMainTab.MY_REQUESTS) {
              // Trending
              if (uiState.selectedTab == SeerrMainTab.ALL) {
                item {
                  SeerrSliderRow(
                    title = stringResource(R.string.seerr_trending),
                    items = uiState.trendingItems,
                    onItemClick = viewModel::openDetail,
                  )
                }
              }

              // Movies Tab / All Tab
              if (uiState.selectedTab == SeerrMainTab.ALL || uiState.selectedTab == SeerrMainTab.MOVIES) {
                item {
                  SeerrSliderRow(
                    title = stringResource(R.string.seerr_popular_movies),
                    items = uiState.popularMovies,
                    onItemClick = viewModel::openDetail,
                  )
                }
                item {
                  SeerrSliderRow(
                    title = stringResource(R.string.seerr_upcoming_movies),
                    items = uiState.upcomingMovies,
                    onItemClick = viewModel::openDetail,
                  )
                }
              }

              // TV Tab / All Tab
              if (uiState.selectedTab == SeerrMainTab.ALL || uiState.selectedTab == SeerrMainTab.TV) {
                item {
                  SeerrSliderRow(
                    title = stringResource(R.string.seerr_popular_tv),
                    items = uiState.popularTv,
                    onItemClick = viewModel::openDetail,
                  )
                }
                item {
                  SeerrSliderRow(
                    title = stringResource(R.string.seerr_upcoming_tv),
                    items = uiState.upcomingTv,
                    onItemClick = viewModel::openDetail,
                  )
                }
              }
            }

            if (uiState.selectedTab == SeerrMainTab.MY_REQUESTS && uiState.activeRequests.isEmpty() && uiState.availableRequests.isEmpty()) {
              item {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "No requests found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Detail Bottom Sheet
    if (uiState.isDetailSheetOpen) {
      SeerrDetailSheet(
        searchItem = uiState.selectedSearchItem,
        details = uiState.selectedMediaDetails,
        isLoading = uiState.isDetailLoading,
        isRequesting = uiState.isRequesting,
        isAdmin = uiState.currentUser?.isAdmin() == true,
        onDismiss = viewModel::closeDetail,
        onRequest = viewModel::requestMedia,
        onApprove = viewModel::approveRequest,
        onDecline = viewModel::declineRequest,
        onDeleteRequest = { reqId ->
          val tmdbId = uiState.selectedSearchItem?.id ?: uiState.selectedMediaDetails?.id
          val type = uiState.selectedSearchItem?.mediaType ?: if (uiState.selectedMediaDetails?.seasons != null) "tv" else "movie"
          viewModel.deleteRequest(reqId, tmdbId, type)
        },
        onDeleteMedia = { mediaId ->
          val tmdbId = uiState.selectedSearchItem?.id ?: uiState.selectedMediaDetails?.id
          val type = uiState.selectedSearchItem?.mediaType ?: if (uiState.selectedMediaDetails?.seasons != null) "tv" else "movie"
          viewModel.deleteMedia(mediaId, tmdbId, type)
        },
        onOpenJellyfinItem = { id ->
          viewModel.closeDetail()
          onOpenJellyfinItem(id)
        },
      )
    }

    // Connection / Login Bottom Sheet
    SeerrConnectionDialog(
      isOpen = uiState.isConnectionDialogOpen,
      isConnected = uiState.isConnected,
      currentUser = uiState.currentUser,
      currentServerUrl = uiState.serverUrl,
      currentApiKey = uiState.apiKey,
      activeJellyfinServer = activeJellyfinServer,
      isConnecting = uiState.isConnecting,
      errorMessage = uiState.connectionError,
      onDismiss = viewModel::closeConnectionDialog,
      onConnectWithCredentials = viewModel::connectWithCredentials,
      onConnectWithApiKey = viewModel::connectWithApiKey,
      onDisconnect = viewModel::disconnect,
    )
  }
}
