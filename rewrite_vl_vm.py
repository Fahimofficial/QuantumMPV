import re

with open("app/src/main/java/com/quantummpv/app/ui/browser/videolist/VideoListViewModel.kt", "r") as f:
    content = f.read()

# Replace refresh
old_refresh = """  override fun refresh() {
    Log.d(tag, "Hard refreshing video list for bucket: $bucketId")

    // Set loading state
    _isLoading.value = true

    // Clear cache to force fresh data from filesystem
    MediaFileRepository.clearCache()
    FolderViewScanner.clearCache()

    // Trigger media scan before loading to ensure MediaStore is up-to-date
    triggerMediaScan()

    loadVideos(forceFileSystemCheck = true)
  }"""

new_refresh = """  override fun refresh() {
    Log.d(tag, "Hard refreshing video list for bucket: $bucketId")

    _isLoading.value = true

    viewModelScope.launch(Dispatchers.IO) {
      MediaFileRepository.clearCache()
      FolderViewScanner.clearCache()
      triggerMediaScan()
      loadVideosInternal(forceFileSystemCheck = true)
    }
  }"""
content = content.replace(old_refresh, new_refresh)

# Replace loadVideos
old_load = """  private fun loadVideos(forceFileSystemCheck: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      try {"""

new_load = """  private fun loadVideos(forceFileSystemCheck: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      loadVideosInternal(forceFileSystemCheck)
    }
  }

  private suspend fun loadVideosInternal(forceFileSystemCheck: Boolean = false) {
      try {"""
content = content.replace(old_load, new_load)

with open("app/src/main/java/com/quantummpv/app/ui/browser/videolist/VideoListViewModel.kt", "w") as f:
    f.write(content)
