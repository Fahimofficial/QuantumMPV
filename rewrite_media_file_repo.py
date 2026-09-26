import re

with open('/workspace/swift-curie/app/src/main/java/com/quantummpv/app/repository/MediaFileRepository.kt', 'r') as f:
    content = f.read()

# 1. Add imports
imports = """import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import com.quantummpv.app.database.dao.MediaIndexDao
import com.quantummpv.app.utils.storage.MediaScannerWorker
import com.quantummpv.app.database.entities.MediaIndexEntity
"""
content = re.sub(r'(import [^\n]+\n)+', lambda m: m.group(0) + imports, content, count=1)

# 2. Inject mediaIndexDao
content = re.sub(
    r'private val database: MpvRxDatabase by inject\(\)',
    'private val database: MpvRxDatabase by inject()\n  private val mediaIndexDao: MediaIndexDao by inject()',
    content
)

# 3. Add toVideo() extension function
to_video = """
  private fun MediaIndexEntity.toVideo(): Video {
    return Video(
      id = this.uri.hashCode().toLong(),
      title = this.displayName.substringBeforeLast("."),
      displayName = this.displayName,
      path = this.path,
      uri = Uri.parse(this.uri),
      duration = this.durationMs ?: 0L,
      durationFormatted = formatDuration(this.durationMs ?: 0L),
      size = this.size,
      sizeFormatted = FormatUtils.formatFileSize(this.size),
      dateModified = this.lastModified,
      dateAdded = this.lastModified,
      mimeType = FileTypeUtils.getMimeTypeFromExtension(this.extension),
      bucketId = this.parentFolder,
      bucketDisplayName = this.parentFolder.substringAfterLast('/'),
      width = 0,
      height = 0,
      fps = 0f,
      resolution = "",
      isAudio = this.mediaType == 1
    )
  }
"""

content = re.sub(
    r'object MediaFileRepository : KoinComponent \{',
    'object MediaFileRepository : KoinComponent {\n' + to_video,
    content
)

# 4. Modify getAllVideoFolders
get_all_video_folders = """suspend fun getAllVideoFolders(
    context: Context,
    forceFileSystemCheck: Boolean = false,
    includeAudioOverride: Boolean? = null,
  ): List<VideoFolder> =
    withContext(Dispatchers.IO) {
      try {
        triggerMediaScan(context)
        val folders = mediaIndexDao.getAllFolders().first()
        folders.map { folderPath ->
          val folderName = folderPath.substringAfterLast('/')
          VideoFolder(
            bucketId = folderPath,
            name = folderName.ifEmpty { "Root" },
            path = folderPath,
            videoCount = 0
          )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
      } catch (e: Exception) {
        Log.e(TAG, "Error scanning for video folders", e)
        emptyList()
      }
    }"""

content = re.sub(
    r'suspend fun getAllVideoFolders\(.*?try \{.*?catch \(e: Exception\) \{.*?emptyList\(\)\s*\}\s*\}',
    get_all_video_folders,
    content,
    flags=re.DOTALL
)

# 5. Modify getVideosInFolder
get_videos_in_folder = """suspend fun getVideosInFolder(
    context: Context,
    bucketId: String,
    forceFileSystemCheck: Boolean = false,
    includeAudioOverride: Boolean? = null,
  ): List<Video> =
    withContext(Dispatchers.IO) {
      try {
        val entities = mediaIndexDao.getMediaInFolder(bucketId).first()
        entities.map { it.toVideo() }
      } catch (e: Exception) {
        Log.e(TAG, "Error getting videos for bucket $bucketId", e)
        emptyList()
      }
    }"""

content = re.sub(
    r'suspend fun getVideosInFolder\(.*?try \{.*?catch \(e: Exception\) \{.*?emptyList\(\)\s*\}\s*\}',
    get_videos_in_folder,
    content,
    flags=re.DOTALL
)

# 6. Add searchMedia and triggerMediaScan
search_and_scan = """
  suspend fun searchMedia(query: String): List<Video> = 
    withContext(Dispatchers.IO) {
      try {
        mediaIndexDao.searchMedia("*$query*").first().map { it.toVideo() }
      } catch (e: Exception) {
        Log.e(TAG, "Error searching media", e)
        emptyList()
      }
    }

  fun triggerMediaScan(context: Context) {
    val request = OneTimeWorkRequestBuilder<MediaScannerWorker>().build()
    WorkManager.getInstance(context).enqueue(request)
  }
"""

content = content.replace('object MediaFileRepository : KoinComponent {', 'object MediaFileRepository : KoinComponent {' + search_and_scan)

with open('/workspace/swift-curie/app/src/main/java/com/quantummpv/app/repository/MediaFileRepository.kt', 'w') as f:
    f.write(content)
