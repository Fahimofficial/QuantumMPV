import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerViewModel.kt", "r") as f:
    content = f.read()

old_pause = """  fun pause() {
    viewModelScope.launch(playbackStateDispatcher) {
      PlaybackSession.setPropertyBoolean("pause", true)
      syncplayManager.updatePlayerState(precisePosition.value.toDouble(), true, doSeek = false)
      withContext(Dispatchers.Main) { host.abandonAudioFocus() }
    }
  }"""

new_pause = """  fun pause() {
    PlaybackSession.commandNode("set", "pause", "yes")
    syncplayManager.updatePlayerState(precisePosition.value.toDouble(), true, doSeek = false)
    host.abandonAudioFocus()
  }"""

content = content.replace(old_pause, new_pause)
with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerViewModel.kt", "w") as f:
    f.write(content)
