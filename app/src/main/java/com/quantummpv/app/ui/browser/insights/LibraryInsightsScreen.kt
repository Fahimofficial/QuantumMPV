/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.insights

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quantummpv.app.R
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.components.BrowserTopBar
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.GlassComponents
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

/**
 * Library Insights screen — displays user's actual viewing activity and statistics
 * derived from existing playback history tracking.
 *
 * Metrics shown:
 * - Total watch time (from successfully tracked sessions)
 * - Number of unique videos watched
 * - Number of completed videos (where progress >= 90%)
 * - Recently watched videos with metadata
 * - Most watched videos (by play count)
 *
 * Data limitations:
 * - Only reflects videos added to history since tracking was enabled
 * - Opening a file does not equal watching it (tracks actual playback sessions)
 * - Watch time accuracy depends on mpv's duration metadata
 * - Deleted files are excluded from current statistics
 * - Empty state shown for new users with no history
 */
@Serializable
object LibraryInsightsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: LibraryInsightsViewModel =
      viewModel(factory = LibraryInsightsViewModel.factory(context.applicationContext as android.app.Application))

    val insightsState by viewModel.insightsState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.library_insights_title),
          scrollBehavior = scrollBehavior,
        )
      },
    ) { padding ->
      when {
        insightsState.isLoading -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(48.dp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        insightsState.isEmpty -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            EmptyInsightsState()
          }
        }

        else -> {
          InsightsContent(
            insightsState = insightsState,
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyInsightsState() {
  Column(
    modifier = Modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(
      Icons.RoundedFilled.Insights,
      contentDescription = null,
      modifier = Modifier.size(72.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Text(
      text = stringResource(R.string.library_insights_empty_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
    Text(
      text = stringResource(R.string.library_insights_empty_message),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun InsightsContent(
  insightsState: LibraryInsightsState,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Overview statistics cards
    item {
      Text(
        text = stringResource(R.string.library_insights_overview),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        StatCard(
          title = stringResource(R.string.library_insights_watch_time),
          value = formatWatchTime(insightsState.totalWatchTimeMs),
          icon = Icons.RoundedFilled.Schedule,
          modifier = Modifier.weight(1f),
        )
        StatCard(
          title = stringResource(R.string.library_insights_videos_watched),
          value = insightsState.uniqueVideosWatched.toString(),
          icon = Icons.RoundedFilled.VideoLibrary,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        StatCard(
          title = stringResource(R.string.library_insights_completed),
          value = insightsState.completedVideos.toString(),
          icon = Icons.RoundedFilled.CheckCircle,
          modifier = Modifier.weight(1f),
        )
        StatCard(
          title = stringResource(R.string.library_insights_total_items),
          value = insightsState.totalHistoryItems.toString(),
          icon = Icons.RoundedFilled.History,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Recently watched section
    if (insightsState.recentlyWatched.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.library_insights_recently_watched),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 8.dp),
        )
      }

      items(insightsState.recentlyWatched.take(5)) { video ->
        RecentVideoCard(video = video)
      }
    }

    // Most watched section
    if (insightsState.mostWatched.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.library_insights_most_watched),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 8.dp),
        )
      }

      items(insightsState.mostWatched.take(5)) { video ->
        MostWatchedCard(video = video)
      }
    }
  }
}

@Composable
private fun StatCard(
  title: String,
  value: String,
  icon: com.quantummpv.app.ui.icons.AppIcon,
  modifier: Modifier = Modifier,
) {
  GlassComponents.GlassSurface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(32.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun RecentVideoCard(video: InsightVideo) {
  GlassComponents.GlassSurface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.RoundedFilled.Movie,
          contentDescription = null,
          modifier = Modifier.size(28.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = video.displayName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
        )
        Text(
          text = formatTimestamp(video.lastPlayedTimestamp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun MostWatchedCard(video: InsightVideo) {
  GlassComponents.GlassSurface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = video.playCount.toString(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = video.displayName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
        )
        Text(
          text = stringResource(R.string.library_insights_times_watched, video.playCount),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun formatWatchTime(milliseconds: Long): String {
  val duration = milliseconds.milliseconds
  val hours = duration.inWholeHours
  val minutes = (duration.inWholeMinutes % 60)

  return when {
    hours > 0 -> "${hours}h ${minutes}m"
    minutes > 0 -> "${minutes}m"
    else -> "< 1m"
  }
}

private fun formatTimestamp(timestamp: Long): String {
  val now = System.currentTimeMillis()
  val diff = now - timestamp
  val days = diff / (1000 * 60 * 60 * 24)
  val hours = diff / (1000 * 60 * 60)
  val minutes = diff / (1000 * 60)

  return when {
    days > 0 -> "$days day${if (days != 1L) "s" else ""} ago"
    hours > 0 -> "$hours hour${if (hours != 1L) "s" else ""} ago"
    minutes > 0 -> "$minutes minute${if (minutes != 1L) "s" else ""} ago"
    else -> "Just now"
  }
}
