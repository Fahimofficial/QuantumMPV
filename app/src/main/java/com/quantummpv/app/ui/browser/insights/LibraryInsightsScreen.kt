/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.insights

import android.app.Application
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quantummpv.app.R
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.states.EmptyState
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.theme.LocalEmphasizedTypography
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.serialization.Serializable
import java.text.NumberFormat

/**
 * Library Insights: figures about what the user has actually played, built only from the existing
 * Recently Played history and saved playback positions. Nothing is shown when there is no history,
 * and nothing is estimated from a video's full length.
 */
@Serializable
object LibraryInsightsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val emphasizedTypography = LocalEmphasizedTypography.current
    val viewModel: LibraryInsightsViewModel =
      viewModel(factory = LibraryInsightsViewModel.factory(context.applicationContext as Application))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Saved positions are not observed, so refresh whenever the screen becomes visible again.
    LifecycleResumeEffect(viewModel) {
      viewModel.refresh()
      onPauseOrDispose { }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.library_insights_title),
              style = emphasizedTypography.headlineSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) {
              Icon(
                Icons.RoundedFilled.ArrowBack,
                contentDescription = stringResource(R.string.ui_go_back),
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when (val current = state) {
          LibraryInsightsUiState.Loading ->
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

          LibraryInsightsUiState.HistoryDisabled ->
            EmptyState(
              icon = Icons.RoundedFilled.History,
              title = stringResource(R.string.library_insights_disabled_title),
              message = stringResource(R.string.library_insights_disabled_message),
              modifier = Modifier.align(Alignment.Center),
            )

          LibraryInsightsUiState.Empty ->
            EmptyState(
              icon = Icons.RoundedFilled.Insights,
              title = stringResource(R.string.library_insights_empty_title),
              message = stringResource(R.string.library_insights_empty_message),
              modifier = Modifier.align(Alignment.Center),
            )

          is LibraryInsightsUiState.Content -> InsightsContent(current.insights)
        }
      }
    }
  }
}

/** Overview tiles followed by the "continue watching" and "recently played" lists. */
@Composable
private fun InsightsContent(insights: LibraryInsights) {
  // Fixed per result so relative times don't tick while the list is being scrolled.
  val now = remember(insights) { System.currentTimeMillis() }
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + bottomInset),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "overview") {
      SectionTitle(stringResource(R.string.library_insights_overview))
    }
    item(key = "tiles_primary") {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(
          label = stringResource(R.string.library_insights_videos_played),
          value = insights.videosPlayed,
          modifier = Modifier.weight(1f),
        )
        StatTile(
          label = stringResource(R.string.library_insights_completed),
          value = insights.completed,
          modifier = Modifier.weight(1f),
        )
      }
    }
    item(key = "tiles_secondary") {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(
          label = stringResource(R.string.library_insights_in_progress),
          value = insights.inProgress,
          modifier = Modifier.weight(1f),
        )
        StatTile(
          label = stringResource(R.string.library_insights_last_7_days),
          value = insights.playedLast7Days,
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (insights.continueWatching.isNotEmpty()) {
      item(key = "continue_title") { SectionTitle(stringResource(R.string.library_insights_continue_watching)) }
      items(insights.continueWatching, key = { "continue:${it.filePath}" }) { VideoRow(it, now) }
    }

    if (insights.recentlyPlayed.isNotEmpty()) {
      item(key = "recent_title") { SectionTitle(stringResource(R.string.library_insights_recently_played)) }
      items(insights.recentlyPlayed, key = { "recent:${it.filePath}" }) { VideoRow(it, now) }
    }

    item(key = "footnote") {
      Text(
        text = stringResource(R.string.library_insights_footnote),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Heading above a group of tiles or rows. */
@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.semantics { heading() },
  )
}

/** A single count with its label, read by accessibility services as one item. */
@Composable
private fun StatTile(
  label: String,
  value: Int,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.semantics(mergeDescendants = true) { },
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = NumberFormat.getInstance().format(value),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** One video: title, localized "how long ago", and either a progress bar or a watched marker. */
@Composable
private fun VideoRow(
  video: InsightVideo,
  now: Long,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = video.displayName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = DateUtils.getRelativeTimeSpanString(video.lastPlayedAt, now, DateUtils.MINUTE_IN_MILLIS).toString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      VideoProgress(video)
    }
  }
}

/** A "Watched" marker for completed videos, a bar plus percentage when progress is known, else nothing. */
@Composable
private fun VideoProgress(video: InsightVideo) {
  val progress = video.progress
  when {
    video.isCompleted ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
          Icons.RoundedFilled.CheckCircle,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.video_label_watched),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }

    progress != null ->
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
          text = stringResource(R.string.library_insights_progress_percent, (progress * PERCENT).toInt()),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
  }
}

private const val PERCENT = 100
