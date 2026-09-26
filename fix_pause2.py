import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerViewModel.kt", "r") as f:
    content = f.read()

old_pause = """  fun pauseUnpause() {
    val wasPaused = PlaybackSession.propBoolean["pause"].value ?: PlaybackSession.state.value.paused
    if (wasPaused) {
      val focusGranted = host.requestAudioFocus()
      if (!focusGranted) return
      PlaybackSession.commandNode("cycle", "pause")
      syncplayManager.updatePlayerState(precisePosition.value.toDouble(), false, doSeek = false)
    } else {
      PlaybackSession.commandNode("cycle", "pause")
      syncplayManager.updatePlayerState(precisePosition.value.toDouble(), true, doSeek = false)
      host.abandonAudioFocus()
    }
  }"""

new_pause = """  fun pauseUnpause() {
    val wasPaused = PlaybackSession.propBoolean["pause"].value ?: PlaybackSession.state.value.paused
    if (wasPaused) {
      val focusGranted = host.requestAudioFocus()
      if (!focusGranted) return
      PlaybackSession.commandNode("set", "pause", "no")
      syncplayManager.updatePlayerState(precisePosition.value.toDouble(), false, doSeek = false)
    } else {
      PlaybackSession.commandNode("set", "pause", "yes")
      syncplayManager.updatePlayerState(precisePosition.value.toDouble(), true, doSeek = false)
      host.abandonAudioFocus()
    }
  }"""

content = content.replace(old_pause, new_pause)
with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerViewModel.kt", "w") as f:
    f.write(content)
