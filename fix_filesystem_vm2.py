import re

with open("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt", "r") as f:
    content = f.read()

old_load_current = """  private fun loadCurrentDirectory(forceFileSystemCheck: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      _error.value = null"""

new_load_current = """  private fun loadCurrentDirectory(forceFileSystemCheck: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      loadCurrentDirectoryInternal(forceFileSystemCheck)
    }
  }

  private suspend fun loadCurrentDirectoryInternal(forceFileSystemCheck: Boolean = false) {
      withContext(Dispatchers.Main) {
          _isLoading.value = true
          _error.value = null
      }"""

content = content.replace(old_load_current, new_load_current)

old_load_end = """        Log.d(TAG, "Loaded ${_unsortedItems.value.size} items for path: $path")
      } catch (e: Exception) {
        Log.e(TAG, "Error loading directory: ${_currentPath.value}", e)
        _error.value = e.message ?: "Unknown error occurred"
        _unsortedItems.value = emptyList()
        _videoFilesWithPlayback.value = emptyMap()
        _newVideoIds.value = emptySet()
        _watchedVideoIds.value = emptySet()
      } finally {
        _isLoading.value = false
      }
    }
  }"""

new_load_end = """        Log.d(TAG, "Loaded ${_unsortedItems.value.size} items for path: $path")
      } catch (e: Exception) {
        Log.e(TAG, "Error loading directory: ${_currentPath.value}", e)
        withContext(Dispatchers.Main) {
            _error.value = e.message ?: "Unknown error occurred"
        }
        _unsortedItems.value = emptyList()
        _videoFilesWithPlayback.value = emptyMap()
        _newVideoIds.value = emptySet()
        _watchedVideoIds.value = emptySet()
      } finally {
        withContext(Dispatchers.Main) {
            _isLoading.value = false
        }
      }
  }"""

content = content.replace(old_load_end, new_load_end)

# Fix the refresh method to use the internal suspend method
old_refresh = """    viewModelScope.launch(Dispatchers.IO) {
      // Clear all caches to force fresh data from filesystem
      MediaFileRepository.clearCache()
      FolderViewScanner.clearCache()
      TreeViewScanner.clearCache()

      // Trigger media scan to ensure MediaStore is up-to-date
      triggerMediaScan()

      loadCurrentDirectory(forceFileSystemCheck = true)
    }"""

new_refresh = """    viewModelScope.launch(Dispatchers.IO) {
      // Clear all caches to force fresh data from filesystem
      MediaFileRepository.clearCache()
      FolderViewScanner.clearCache()
      TreeViewScanner.clearCache()

      // Trigger media scan to ensure MediaStore is up-to-date
      triggerMediaScan()

      loadCurrentDirectoryInternal(forceFileSystemCheck = true)
    }"""

content = content.replace(old_refresh, new_refresh)

with open("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt", "w") as f:
    f.write(content)
