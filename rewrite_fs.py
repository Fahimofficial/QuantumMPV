import re

with open("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt", "r") as f:
    content = f.read()

# Replace refresh
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

    _isLoading.value = true

    viewModelScope.launch(Dispatchers.IO) {
      MediaFileRepository.clearCache()
      FolderViewScanner.clearCache()
      TreeViewScanner.clearCache()
      triggerMediaScan()
      loadCurrentDirectoryInternal(forceFileSystemCheck = true)
    }
  }"""
content = content.replace(old_refresh, new_refresh)

# Replace loadCurrentDirectory
old_load = """  private fun loadCurrentDirectory(forceFileSystemCheck: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      _error.value = null
      // Don't reset the flag here - let navigation handle it

      try {"""

new_load = """  private fun loadCurrentDirectory(forceFileSystemCheck: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      loadCurrentDirectoryInternal(forceFileSystemCheck)
    }
  }

  private suspend fun loadCurrentDirectoryInternal(forceFileSystemCheck: Boolean = false) {
      _isLoading.value = true
      _error.value = null
      // Don't reset the flag here - let navigation handle it

      try {"""
content = content.replace(old_load, new_load)

# Also fix the inner catch/finally that I messed up before? No, with suspend fun loadCurrentDirectoryInternal, the structure stays EXACTLY the same, just the wrapper changed.

with open("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt", "w") as f:
    f.write(content)
