import re

with open("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt", "r") as f:
    content = f.read()

old_refresh = """  override fun refresh() {
    Log.d(TAG, "Hard refreshing current directory: ${_currentPath.value}")

    // Set loading state
    _isLoading.value = true

    // Clear all caches to force fresh data from filesystem
    MediaFileRepository.clearCache()
    FolderViewScanner.clearCache()
    TreeViewScanner.clearCache()

    // Trigger media scan to ensure MediaStore is up-to-date
    triggerMediaScan()

    loadCurrentDirectory(forceFileSystemCheck = true)
  }"""

new_refresh = """  override fun refresh() {
    Log.d(TAG, "Hard refreshing current directory: ${_currentPath.value}")

    // Set loading state on main thread
    _isLoading.value = true

    viewModelScope.launch(Dispatchers.IO) {
      // Clear all caches to force fresh data from filesystem
      MediaFileRepository.clearCache()
      FolderViewScanner.clearCache()
      TreeViewScanner.clearCache()

      // Trigger media scan to ensure MediaStore is up-to-date
      triggerMediaScan()

      loadCurrentDirectory(forceFileSystemCheck = true)
    }
  }"""
content = content.replace(old_refresh, new_refresh)

with open("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt", "w") as f:
    f.write(content)
