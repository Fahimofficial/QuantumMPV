package com.quantummpv.app.ui.browser.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.database.repository.VideoMetadataCacheRepository
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.media.model.VideoFolder
import com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.VideoSwipeAction
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.repository.MediaFileRepository
import com.quantummpv.app.ui.browser.dialogs.AddToPlaylistDialog
import com.quantummpv.app.ui.browser.videolist.VideoWithPlaybackInfo
import com.quantummpv.app.ui.browser.videolist.buildVideoWithPlaybackInfo
import com.quantummpv.app.ui.browser.videolist.videoPlaybackIdentifiers
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.utils.rememberAppHaptics
import com.quantummpv.app.utils.media.PlaybackStateEvents
import com.quantummpv.app.utils.media.PlaybackStateOps
import com.quantummpv.app.utils.permission.PermissionUtils.StorageOps
import com.quantummpv.app.utils.storage.FileTypeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.nio.file.Files

internal class VideoSwipeActions(
  val video: (Video, Boolean, VideoSwipeAction) -> Unit,
  val folder: (VideoFolder, VideoSwipeAction) -> Unit,
)

private data class SwipeTarget(val videos: List<Video>, val folderName: String? = null)

@Composable
internal fun rememberSwipePlaybackInfo(videos: List<Video>): Map<String, VideoWithPlaybackInfo> {
  val preferences = koinInject<BrowserPreferences>()
  val appearance = koinInject<AppearancePreferences>()
  val repository = koinInject<PlaybackStateRepository>()
  val threshold by preferences.watchedThreshold.collectAsState()
  val newDays by appearance.unplayedOldVideoDays.collectAsState()
  var playbackInfo by remember { mutableStateOf(emptyMap<String, VideoWithPlaybackInfo>()) }
  LaunchedEffect(videos, threshold, newDays) {
    PlaybackStateEvents.changes.onStart { emit("") }.collectLatest {
      playbackInfo = withContext(Dispatchers.IO) {
        val states = repository.getAllPlaybackStates().associateBy { it.mediaTitle }
        val now = System.currentTimeMillis()
        videos.associate { video ->
          video.path to buildVideoWithPlaybackInfo(
            video = video,
            playbackState = videoPlaybackIdentifiers(video).firstNotNullOfOrNull(states::get),
            currentTimeMillis = now,
            newLabelDays = newDays,
            watchedThreshold = threshold,
          )
        }
      }
    }
  }
  return playbackInfo
}

@Composable
internal fun rememberVideoSwipeActions(
  onDeleted: suspend (List<Video>) -> Unit = {},
  onChanged: () -> Unit,
): VideoSwipeActions {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val haptics = rememberAppHaptics()
  val metadataCache = koinInject<VideoMetadataCacheRepository>()
  val preferences = koinInject<BrowserPreferences>()
  val currentOnChanged by rememberUpdatedState(onChanged)
  val currentOnDeleted by rememberUpdatedState(onDeleted)
  var deleteTarget by remember { mutableStateOf<SwipeTarget?>(null) }
  var playlistTarget by remember { mutableStateOf<SwipeTarget?>(null) }
  var busy by remember { mutableStateOf(false) }
  var collecting by remember { mutableStateOf(false) }
  var scanJob by remember { mutableStateOf<Job?>(null) }

  if (collecting) {
    AlertDialog(
      onDismissRequest = { scanJob?.cancel() },
      title = { Text(stringResource(R.string.video_swipe_loading)) },
      text = {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(Modifier.size(24.dp))
          Text(stringResource(R.string.video_swipe_folder_videos))
        }
      },
      confirmButton = {
        TextButton(onClick = { scanJob?.cancel() }) { Text(stringResource(R.string.generic_cancel)) }
      },
    )
  }

  deleteTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { if (!busy) deleteTarget = null },
      icon = { Icon(Icons.RoundedFilled.Delete, null, tint = MaterialTheme.colorScheme.error) },
      title = { Text(stringResource(R.string.video_swipe_delete_title)) },
      text = {
        Text(
          if (target.folderName != null) {
            stringResource(R.string.video_swipe_delete_folder_message, target.videos.size, target.folderName)
          } else {
            stringResource(R.string.video_swipe_delete_message, target.videos.single().displayName)
          },
        )
      },
      confirmButton = {
        TextButton(
          enabled = !busy,
          onClick = {
            if (!busy) {
              busy = true
              scope.launch {
                try {
                  val (deleted, failed) = StorageOps.deleteVideos(context, target.videos)
                  if (deleted > 0) {
                    metadataCache.invalidateVideos(target.videos.map { it.path })
                    val deletedVideos = withContext(Dispatchers.IO) {
                      target.videos.filter { video ->
                        runCatching { Files.notExists(File(video.path).toPath()) }.getOrDefault(false)
                      }
                    }
                    currentOnDeleted(deletedVideos)
                    haptics.confirm()
                    currentOnChanged()
                  }
                  val message = context.getString(R.string.video_swipe_delete_result, deleted, failed)
                  Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                  throw cancelled
                } catch (error: Exception) {
                  Log.e("VideoSwipeActions", "Unable to delete video", error)
                  Toast.makeText(context, R.string.ui_failed_to_delete, Toast.LENGTH_SHORT).show()
                } finally {
                  deleteTarget = null
                  busy = false
                }
              }
            }
          },
        ) {
          if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
          else Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(enabled = !busy, onClick = { deleteTarget = null }) { Text(stringResource(R.string.generic_cancel)) }
      },
    )
  }

  playlistTarget?.let { target ->
    AddToPlaylistDialog(
      isOpen = true,
      videos = target.videos,
      onDismiss = { playlistTarget = null },
      onSuccess = { currentOnChanged() },
      onItemsAdded = haptics::confirm,
    )
  }

  val perform: (SwipeTarget, VideoSwipeAction) -> Unit = { target, action ->
    when (action) {
      VideoSwipeAction.None -> Unit
      VideoSwipeAction.Delete -> deleteTarget = target
      VideoSwipeAction.AddToPlaylist -> playlistTarget = target
      VideoSwipeAction.PlayNext, VideoSwipeAction.AddToQueue -> {
        val insertion = if (action == VideoSwipeAction.PlayNext) QueueInsertion.PlayNext else QueueInsertion.AddToEnd
        if (addVideosToPlaybackQueue(context, target.videos, insertion)) haptics.confirm()
      }
      VideoSwipeAction.ToggleWatched,
      VideoSwipeAction.MarkNew,
      VideoSwipeAction.LastPlayed,
      VideoSwipeAction.Finished,
      VideoSwipeAction.ClearHistory,
      -> {
        busy = true
        scope.launch {
          try {
            when (action) {
              VideoSwipeAction.ToggleWatched -> {
                val watched = !PlaybackStateOps.areAllWatched(target.videos, preferences.watchedThreshold.get())
                PlaybackStateOps.setWatched(target.videos, watched)
                haptics.selection(watched)
              }
              VideoSwipeAction.Finished -> {
                PlaybackStateOps.setWatched(target.videos, true)
                haptics.confirm()
              }
              VideoSwipeAction.MarkNew, VideoSwipeAction.ClearHistory -> {
                PlaybackStateOps.resetWatchHistory(target.videos, markAsNew = action == VideoSwipeAction.MarkNew)
                haptics.confirm()
              }
              VideoSwipeAction.LastPlayed -> {
                if (PlaybackStateOps.markLastPlayed(target.videos)) {
                  haptics.confirm()
                } else {
                  Toast.makeText(context, R.string.video_swipe_recent_disabled, Toast.LENGTH_SHORT).show()
                }
              }
              else -> Unit
            }
            currentOnChanged()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            Log.e("VideoSwipeActions", "Unable to update video marks", error)
            Toast.makeText(context, R.string.generic_unknown_error, Toast.LENGTH_SHORT).show()
          } finally {
            busy = false
          }
        }
      }
    }
  }

  return VideoSwipeActions(
    video = { video, _, action ->
      if (!busy && !collecting && deleteTarget == null && playlistTarget == null &&
        !video.isAudio && video.path.startsWith('/')) {
        perform(SwipeTarget(listOf(video)), action)
      }
    },
    folder = { folder, action ->
      if (!busy && !collecting && deleteTarget == null && playlistTarget == null && action != VideoSwipeAction.None) {
        collecting = true
        scanJob = scope.launch {
          try {
            val videos = withContext(Dispatchers.IO) {
              val root = File(folder.path).canonicalFile
              require(root.isDirectory && root.canRead())
              val coroutineContext = currentCoroutineContext()
              val files = root.walkTopDown()
                .onEnter { directory ->
                  coroutineContext.ensureActive()
                  !Files.isSymbolicLink(directory.toPath())
                }
                .onFail { _, error -> throw error }
                .filter { file ->
                  coroutineContext.ensureActive()
                  file.isFile && !Files.isSymbolicLink(file.toPath()) && FileTypeUtils.isVideoFile(file)
                }.toList()
              val resolved = MediaFileRepository.getVideosFromFiles(context, files).filterNot { it.isAudio }
              coroutineContext.ensureActive()
              check(resolved.size == files.size)
              resolved.distinctBy { it.path }.sortedBy { it.path.lowercase() }
            }
            currentCoroutineContext().ensureActive()
            if (videos.isEmpty()) {
              Toast.makeText(context, R.string.video_swipe_folder_empty, Toast.LENGTH_SHORT).show()
            } else {
              perform(SwipeTarget(videos, folder.name), action)
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            Log.e("VideoSwipeActions", "Unable to collect folder videos", error)
            Toast.makeText(context, R.string.generic_unknown_error, Toast.LENGTH_SHORT).show()
          } finally {
            collecting = false
            scanJob = null
          }
        }
      }
    },
  )
}