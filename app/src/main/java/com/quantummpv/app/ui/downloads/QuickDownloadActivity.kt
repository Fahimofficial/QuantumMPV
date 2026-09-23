/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.quantummpv.app.ui.downloads

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quantummpv.app.domain.download.DownloadLocations
import com.quantummpv.app.domain.download.YtdlpDownloadEngine
import com.quantummpv.app.ui.theme.MpvrxTheme
import org.koin.compose.koinInject

class QuickDownloadActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val sharedUrl = extractUrl(intent)
    if (sharedUrl.isNullOrBlank()) {
      finish()
      return
    }
    setContent {
      MpvrxTheme {
        QuickDownloadScreen(url = sharedUrl, onFinished = ::finish)
      }
    }
  }

  private fun extractUrl(intent: Intent): String? =
    intent.data?.toString()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
      ?: intent
        .getStringExtra(Intent.EXTRA_TEXT)
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

  companion object {
    const val BEST = "bestvideo*+bestaudio/best"
    const val P1080 = "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
    const val P720 = "bestvideo[height<=720]+bestaudio/best[height<=720]"
    const val P480 = "bestvideo[height<=480]+bestaudio/best[height<=480]"
    const val AUDIO = "bestaudio/best"
  }
}

private data class DownloadQuality(
  val label: String,
  val description: String,
  val selector: String,
)

private val downloadQualities =
  listOf(
    DownloadQuality("Best available", "Highest quality supported by the source", QuickDownloadActivity.BEST),
    DownloadQuality("Up to 1080p", "Good quality for most phones and tablets", QuickDownloadActivity.P1080),
    DownloadQuality("Up to 720p", "Smaller downloads with clear picture quality", QuickDownloadActivity.P720),
    DownloadQuality("Up to 480p", "Lower data use and faster downloads", QuickDownloadActivity.P480),
    DownloadQuality("Audio only", "Download the best available audio stream", QuickDownloadActivity.AUDIO),
  )

@Composable
private fun QuickDownloadScreen(
  url: String,
  onFinished: () -> Unit,
) {
  val engine = koinInject<YtdlpDownloadEngine>()
  val locations = koinInject<DownloadLocations>()
  var selected by remember { mutableStateOf(downloadQualities.first()) }
  val context = LocalContext.current

  Scaffold(topBar = { TopAppBar(title = { Text("Quick download") }) }) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Choose download quality", style = MaterialTheme.typography.headlineSmall)
      Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
      Spacer(Modifier.height(4.dp))
      downloadQualities.forEach { quality ->
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .selectable(
                selected = selected == quality,
                onClick = { selected = quality },
              ).padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = selected == quality, onClick = null)
          Column(Modifier.padding(start = 12.dp)) {
            Text(quality.label, style = MaterialTheme.typography.titleMedium)
            Text(quality.description, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      Spacer(Modifier.weight(1f))
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          val title = url.substringAfterLast('/').substringBefore('?').ifBlank { "shared-download" }
          engine.enqueue(url, title, locations.linksDir(), selected.selector)
          android.widget.Toast
            .makeText(context, "Download added to queue", android.widget.Toast.LENGTH_SHORT)
            .show()
          onFinished()
        },
      ) { Text("Start download") }
    }
  }
}
