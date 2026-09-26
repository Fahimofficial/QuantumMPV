import re

with open("app/src/main/java/com/quantummpv/app/domain/download/YtdlpDownloadEngine.kt", "r") as f:
    content = f.read()

# Replace findNewestOutput
old_find = """  private fun findNewestOutput(job: Job): String? {
    val prefix = DownloadLocations.sanitizeName(job.title)
    return File(job.directory)
      .listFiles()
      ?.filter { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
      ?.maxByOrNull { it.lastModified() }
      ?.absolutePath
  }"""

new_find = """  private fun findNewestOutput(job: Job): String? {
    if (job.outputFile != null && File(job.outputFile).exists()) {
      return job.outputFile
    }

    val sanitizedTitle = DownloadLocations.sanitizeName(job.title).takeIf { it.isNotBlank() } ?: "download"
    val newSchemeMarker = "-${job.id}-"

    return File(job.directory)
      .listFiles()
      ?.filter { file ->
        file.isFile &&
          !file.name.endsWith(".part") &&
          !file.name.endsWith(".ytdl") &&
          (file.name.contains(newSchemeMarker) || (file.name.startsWith("$sanitizedTitle.") && !file.name.contains("-${job.id}-")))
      }
      ?.maxByOrNull { it.lastModified() }
      ?.absolutePath
  }

  private fun cleanupFiles(job: Job) {
    val sanitizedTitle = DownloadLocations.sanitizeName(job.title).takeIf { it.isNotBlank() } ?: "download"
    val newSchemeMarker = "-${job.id}-"
    File(job.directory).listFiles()?.forEach { file ->
      if (!file.isFile) return@forEach
      val isNewScheme = file.name.contains(newSchemeMarker)
      val isLegacyScheme = file.name.startsWith("$sanitizedTitle.") && !file.name.contains("-${job.id}-")
      
      if (isNewScheme) {
        // Safe to delete anything matching the unique marker (including temp .f* files, .part, .ytdl, or completed files)
        file.delete()
      } else if (isLegacyScheme) {
        // For legacy, only delete .part or .ytdl to avoid deleting other completed jobs with same title
        if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) {
          file.delete()
        }
      }
    }
  }"""
content = content.replace(old_find, new_find)

# Update buildCommand to use the new outputTemplate
old_output = """    val outputTemplate = "${job.directory}/${DownloadLocations.sanitizeName(job.title)}.%(ext)s"
    val command = buildCommand(job.url, outputTemplate, job.formatSelector)"""

new_output = """    val sanitizedTitle = DownloadLocations.sanitizeName(job.title).takeIf { it.isNotBlank() } ?: "download"
    val legacyPrefix = "$sanitizedTitle."
    val hasLegacyPart = File(job.directory).listFiles()?.any {
      it.isFile && it.name.startsWith(legacyPrefix) && !it.name.contains("-${job.id}-") && (it.name.endsWith(".part") || it.name.endsWith(".ytdl"))
    } == true

    val outputTemplate = if (hasLegacyPart) {
      "${job.directory}/$sanitizedTitle.%(ext)s"
    } else {
      "${job.directory}/$sanitizedTitle-${job.id}-%(extractor)s-%(id)s.%(ext)s"
    }
    
    val command = buildCommand(job.url, outputTemplate, job.formatSelector)"""
content = content.replace(old_output, new_output)

# Update remove to call cleanupFiles
old_remove = """  fun remove(id: Int) {
    val job = _jobs.value.firstOrNull { it.id == id } ?: return
    if (job.isActive) cancel(id)
    _jobs.update { current -> current.filterNot { it.id == id } }
    persistenceScope.launch { jobDao.delete(id) }
  }"""

new_remove = """  fun remove(id: Int, deleteFiles: Boolean = true) {
    val job = _jobs.value.firstOrNull { it.id == id } ?: return
    if (job.isActive) cancel(id)
    _jobs.update { current -> current.filterNot { it.id == id } }
    if (deleteFiles) {
      cleanupFiles(job)
    }
    persistenceScope.launch { jobDao.delete(id) }
  }"""
content = content.replace(old_remove, new_remove)

with open("app/src/main/java/com/quantummpv/app/domain/download/YtdlpDownloadEngine.kt", "w") as f:
    f.write(content)
