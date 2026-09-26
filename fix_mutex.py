import re

with open("app/src/main/java/com/quantummpv/app/ui/player/MediaPlaybackService.kt", "r") as f:
    content = f.read()

# Add Mutex
import_mutex = "import kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock"
content = content.replace("import kotlinx.coroutines.withContext", import_mutex + "\nimport kotlinx.coroutines.withContext")

mutex_decl = "  private var playbackStateSaveJob: Job? = null"
mutex_decl_new = "  private val playbackStateMutex = Mutex()\n  private var playbackStateSaveJob: Job? = null"
content = content.replace(mutex_decl, mutex_decl_new)

old_persist = """  private suspend fun persistPlaybackState(
    identifier: String,
    capturedSnapshot: PlaybackStateSnapshot,
  ) {
    if (identifier.isBlank() || capturedSnapshot.mediaIdentifier != identifier) return

    runCatching {"""

new_persist = """  private suspend fun persistPlaybackState(
    identifier: String,
    capturedSnapshot: PlaybackStateSnapshot,
  ) {
    if (identifier.isBlank() || capturedSnapshot.mediaIdentifier != identifier) return

    playbackStateMutex.withLock {
    runCatching {"""

content = content.replace(old_persist, new_persist)
content = content.replace("PlaybackStateEvents.notifyChanged(identifier)\n    }.onFailure { error ->", "PlaybackStateEvents.notifyChanged(identifier)\n    }\n    }.onFailure { error ->")

with open("app/src/main/java/com/quantummpv/app/ui/player/MediaPlaybackService.kt", "w") as f:
    f.write(content)
