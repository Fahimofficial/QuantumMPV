package com.quantummpv.app.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.preferences.AiPreferences
import com.quantummpv.app.repository.ai.AiService
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BulkAiRenameDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (Map<Video, String>) -> Unit,
  selectedVideos: List<Video>,
) {
  if (!isOpen) return

  val scope = rememberCoroutineScope()
  val aiService = koinInject<AiService>()
  val aiPreferences = koinInject<AiPreferences>()
  
  var isAiLoading by remember { mutableStateOf(false) }
  var isError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  
  // We don't want to show the dialog if AI is not enabled or AI rename is disabled
  val canUseAi = aiPreferences.enabled.get() && aiPreferences.renameWithAi.get()

  LaunchedEffect(canUseAi) {
    if (!canUseAi) {
      isError = true
      errorMessage = "AI renaming is currently disabled in settings."
    }
  }

  fun performBulkAiRename() {
    scope.launch {
      isAiLoading = true
      isError = false
      errorMessage = ""

      val updates = mutableMapOf<Video, String>()
      var failureCount = 0

      // Use coroutineScope to await all parallel AI calls
      runCatching {
        coroutineScope {
          val semaphore = Semaphore(3)
          val deferredResults = selectedVideos.map { video ->
            async {
              semaphore.withPermit {
                val currentName = video.displayName.substringBeforeLast('.')
                val extension = "." + video.displayName.substringAfterLast('.', "")
                val extToUse = if (extension != ".") extension else null
                
                val result: Result<String> = aiService.renameWithAi(currentName, extToUse)
                Pair(video, result)
              }
            }
          }

          val results = deferredResults.awaitAll()
          
          results.forEach { pair ->
            val video = pair.first
            val result = pair.second
            result.onSuccess { aiName ->
              updates[video] = aiName
            }.onFailure {
              failureCount++
            }
          }
        }
      }.onFailure {
        isError = true
        errorMessage = "An unexpected error occurred during AI processing."
        isAiLoading = false
        return@launch
      }

      isAiLoading = false

      if (failureCount > 0 && updates.isEmpty()) {
        isError = true
        errorMessage = "AI rename failed for all $failureCount items. Please check your API key."
      } else {
        onConfirm(updates)
        onDismiss()
      }
    }
  }

  AlertDialog(
    onDismissRequest = {
      if (!isAiLoading) onDismiss()
    },
    title = {
      Text(
        text = "Bulk AI Rename",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (!canUseAi) {
          Text(
            text = "AI Rename is disabled. Please enable it in Settings -> Advanced -> AI Integration.",
            color = MaterialTheme.colorScheme.error
          )
        } else {
          Text(
            text = "Use AI to automatically rename ${selectedVideos.size} selected videos?",
            style = MaterialTheme.typography.bodyLarge
          )
          
          if (isAiLoading) {
            Column(modifier = Modifier.fillMaxWidth()) {
              CircularProgressIndicator(
                modifier = Modifier.padding(bottom = 16.dp)
              )
              Text("AI is processing ${selectedVideos.size} files. Please wait...")
            }
          }
          
          if (isError) {
            Text(
              text = errorMessage,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { performBulkAiRename() },
        enabled = canUseAi && !isAiLoading && selectedVideos.isNotEmpty(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
          ),
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        if (isAiLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
          )
          Spacer(modifier = Modifier.width(8.dp))
        } else {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
          text = if (isAiLoading) "Thinking..." else "AI Rename All",
          fontWeight = FontWeight.Bold,
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isAiLoading,
        shape = MaterialTheme.shapes.extraLarge,
      ) {
        Text("Cancel", fontWeight = FontWeight.Medium)
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}
