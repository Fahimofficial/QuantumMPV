/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.quantummpv.app.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.database.MpvRxDatabase
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.popSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object LibraryInsightsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val database = koinInject<MpvRxDatabase>()
    var insights by remember { mutableStateOf<LibraryInsights?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(database, refreshKey) {
      insights = withContext(Dispatchers.IO) {
        val rows = database.recentlyPlayedDao().getAllRecentlyPlayed()
        val cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        LibraryInsights(
          historyCount = rows.size,
          uniqueItems = rows.map { it.filePath }.distinct().size,
          recentCount = rows.count { it.timestamp >= cutoff },
          missingLocalFiles = rows.count { row ->
            row.filePath.isNotBlank() &&
              !row.filePath.startsWith("http://", true) &&
              !row.filePath.startsWith("https://", true) &&
              !File(row.filePath).exists()
          },
          networkItems = rows.count { row ->
            row.filePath.startsWith("http://", true) || row.filePath.startsWith("https://", true)
          },
        )
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Library insights") },
          navigationIcon = { androidx.compose.material3.IconButton(onClick = { backStack.popSafely() }) {
            androidx.compose.material3.Icon(
              com.quantummpv.app.ui.icons.Icons.RoundedFilled.ArrowBack,
              contentDescription = null,
            )
          } },
          actions = {
            Button(onClick = { refreshKey++ }, contentPadding = PaddingValues(horizontal = 12.dp)) {
              Text("Refresh")
            }
          },
        )
      },
    ) { padding ->
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Text(
            text = "Optional, on-device diagnostics based on your existing playback history. Nothing is uploaded.",
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }
        insights?.let { value ->
          items(
            listOf(
              "History entries" to value.historyCount,
              "Unique items" to value.uniqueItems,
              "Played in the last 7 days" to value.recentCount,
              "Missing local files" to value.missingLocalFiles,
              "Network items" to value.networkItems,
            ),
          ) { (label, count) ->
            Card(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(label)
                Text(count.toString())
              }
            }
          }
        }
      }
    }
  }
}

private data class LibraryInsights(
  val historyCount: Int,
  val uniqueItems: Int,
  val recentCount: Int,
  val missingLocalFiles: Int,
  val networkItems: Int,
)
