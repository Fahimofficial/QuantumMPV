import re

with open("app/src/main/java/com/quantummpv/app/utils/storage/MediaScannerWorker.kt", "r") as f:
    content = f.read()

# Add imports
import_str = "import android.net.Uri\nimport com.quantummpv.app.database.repository.VideoMetadataCacheRepository"
content = content.replace("import com.quantummpv.app.database.entities.MediaIndexEntity", import_str + "\nimport com.quantummpv.app.database.entities.MediaIndexEntity")

# Inject repository
inject_str = "  private val browserPreferences: BrowserPreferences by inject()\n  private val metadataRepository: VideoMetadataCacheRepository by inject()"
content = content.replace("  private val browserPreferences: BrowserPreferences by inject()", inject_str)

# Modify the extraction block
old_block = """            mediaEntities.add(
              MediaIndexEntity(
                uri = file.toURI().toString(),
                path = file.absolutePath,
                parentFolder = file.parent ?: "",
                displayName = file.name,
                extension = ext,
                mediaType = mediaType,
                size = file.length(),
                lastModified = file.lastModified(),
                durationMs = null, // Extracted later or via VideoMetadataDao
                hasThumbnail = false
              )
            )"""

new_block = """            val uri = Uri.fromFile(file)
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
            )"""

content = content.replace(old_block, new_block)

with open("app/src/main/java/com/quantummpv/app/utils/storage/MediaScannerWorker.kt", "w") as f:
    f.write(content)
