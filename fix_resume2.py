import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "r") as f:
    content = f.read()

old_resume = """  val resumeMode = playerPreferences.resumePlaybackMode.get()
  val isEffectivelyAtEnd = state.duration > 0 && state.lastPosition >= state.duration - 5
  val hasValidSavedPosition = state.lastPosition > 3 && !isEffectivelyAtEnd"""

new_resume = """  val resumeMode = playerPreferences.resumePlaybackMode.get()
  val isEffectivelyAtEnd = state.timeRemaining in 1..5
  val hasValidSavedPosition = state.lastPosition > 3 && !isEffectivelyAtEnd"""

content = content.replace(old_resume, new_resume)
with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "w") as f:
    f.write(content)
