import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "r") as f:
    content = f.read()

old_resume = """  val resumeMode = playerPreferences.resumePlaybackMode.get()
  val hasValidSavedPosition = state.lastPosition > 3
  if (!playerPreferences.savePositionOnQuit.get() || !hasValidSavedPosition) {
    PlaybackSession.setPropertyInt("time-pos", 0)
    return
  }

  when (resumeMode) {
    ResumePlaybackMode.Always -> {
      PlaybackSession.setPropertyInt("time-pos", state.lastPosition)
      if (playerPreferences.showResumeIndicatorOverlay.get()) {
        withContext(Dispatchers.Main) {
          viewModel.playerUpdate.value = PlayerUpdates.ResumedFrom(state.lastPosition)
        }
      }
    }

    ResumePlaybackMode.Ask -> {
      PlaybackSession.setPropertyInt("time-pos", 0)
      withContext(Dispatchers.Main) {
        viewModel.playerUpdate.value = PlayerUpdates.ResumeAvailable(state.lastPosition)
      }
    }

    ResumePlaybackMode.Never -> {
      PlaybackSession.setPropertyInt("time-pos", 0)
      if (playerPreferences.showResumeIndicatorOverlay.get()) {
        withContext(Dispatchers.Main) {
          viewModel.playerUpdate.value = PlayerUpdates.StartedAfresh
        }
      }
    }
  }"""

new_resume = """  val resumeMode = playerPreferences.resumePlaybackMode.get()
  val isEffectivelyAtEnd = state.duration > 0 && state.lastPosition >= state.duration - 5
  val hasValidSavedPosition = state.lastPosition > 3 && !isEffectivelyAtEnd
  if (!playerPreferences.savePositionOnQuit.get() || !hasValidSavedPosition) {
    PlaybackSession.commandNode("set", "time-pos", "0")
    return
  }

  when (resumeMode) {
    ResumePlaybackMode.Always -> {
      PlaybackSession.commandNode("set", "time-pos", state.lastPosition.toString())
      if (playerPreferences.showResumeIndicatorOverlay.get()) {
        withContext(Dispatchers.Main) {
          viewModel.playerUpdate.value = PlayerUpdates.ResumedFrom(state.lastPosition)
        }
      }
    }

    ResumePlaybackMode.Ask -> {
      PlaybackSession.commandNode("set", "time-pos", "0")
      withContext(Dispatchers.Main) {
        viewModel.playerUpdate.value = PlayerUpdates.ResumeAvailable(state.lastPosition)
      }
    }

    ResumePlaybackMode.Never -> {
      PlaybackSession.commandNode("set", "time-pos", "0")
      if (playerPreferences.showResumeIndicatorOverlay.get()) {
        withContext(Dispatchers.Main) {
          viewModel.playerUpdate.value = PlayerUpdates.StartedAfresh
        }
      }
    }
  }"""

content = content.replace(old_resume, new_resume)
with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "w") as f:
    f.write(content)
