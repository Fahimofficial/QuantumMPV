package com.quantummpv.app.utils.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quantummpv.app.database.dao.MediaIndexDao
import com.quantummpv.app.database.entities.MediaIndexEntity
import com.quantummpv.app.database.repository.VideoMetadataCacheRepository
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.FoldersPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class MediaScannerWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
  private val mediaIndexDao: MediaIndexDao by inject()
  private val foldersPreferences: FoldersPreferences by inject()
  private val browserPreferences: BrowserPreferences by inject()
  private val metadataRepository: VideoMetadataCacheRepository by inject()

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    Log.d("MediaScanner", "Starting media scan")

    val includeNoMedia = foldersPreferences.includeNoMediaFolders.get()
    val includeAudio = browserPreferences.includeAudioBrowser.get()

    // In a real implementation we would iterate through defined roots.
    // For now, we simulate scanning by walking the external storage directory.
    val rootDir = android.os.Environment.getExternalStorageDirectory()

    val mediaEntities = mutableListOf<MediaIndexEntity>()

    try {
      rootDir.walkTopDown().onEnter { dir ->
        if (!includeNoMedia && File(dir, ".nomedia").exists()) return@onEnter false
        true
      }.forEach { file ->
        if (file.isFile) {
          val ext = file.extension.lowercase()
          val isVideo = ext in VALID_VIDEO_EXTENSIONS
          val isAudio = includeAudio && ext in VALID_AUDIO_EXTENSIONS

          if (isVideo || isAudio) {
            val mediaType = if (isVideo) 0 else 1
            val uri = Uri.fromFile(file)
            val metadata = if (isVideo) {
              metadataRepository.getOrExtractMetadata(file, uri, file.name)
            } else null

            mediaEntities.add(
              MediaIndexEntity(
                uri = file.toURI().toString(),
                path = file.absolutePath,
                parentFolder = file.parent ?: "",
                displayName = file.name,
                extension = ext,
                mediaType = mediaType,
                size = file.length(),
                lastModified = file.lastModified(),
                durationMs = metadata?.durationMs,
                hasThumbnail = false
              )
            )
          }
        }
      }

      // Batch upsert to DB
      if (mediaEntities.isNotEmpty()) {
        mediaIndexDao.upsert(mediaEntities)
      }

      Log.d("MediaScanner", "Completed media scan. Indexed ${mediaEntities.size} items.")
      Result.success()
    } catch (e: Exception) {
      Log.e("MediaScanner", "Error scanning media", e)
      Result.failure()
    }
  }

  companion object {
    val VALID_VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v", "ts")
    val VALID_AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "wav", "m4a", "aac", "opus")
  }
}
