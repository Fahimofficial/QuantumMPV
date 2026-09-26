import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlaybackSession.kt", "r") as f:
    content = f.read()

# 1. Update detachRendererSurfaceLocked
old_detach = """  private fun detachRendererSurfaceLocked() {
    runCatching { MPVLib.setPropertyString("vo", "null") }
    runCatching { MPVLib.setOptionString("force-window", "no") }
    runCatching { MPVLib.detachSurface() }
    attachedSurfaceOwner = null
    updateState { it.copy(surfaceAttached = false) }
  }"""

new_detach = """  private fun detachRendererSurfaceLocked() {
    attachedSurfaceOwner = null
    updateState { it.copy(surfaceAttached = false) }
    runCatching { MPVLib.setPropertyString("vo", "null") }
    runCatching { MPVLib.setOptionString("force-window", "no") }
    runCatching { MPVLib.detachSurface() }
  }"""

content = content.replace(old_detach, new_detach)

# 2. Update eventProperty guard
old_guard = """    if (property == "vo" && value != desiredVideoOutput) {
      if (value != "null" && value.isNotBlank()) {
        desiredVideoOutput = value
      } else {
        MPVLib.setPropertyString("vo", desiredVideoOutput)
      }
    }"""

new_guard = """    if (property == "vo" && value != desiredVideoOutput) {
      if (value != "null" && value.isNotBlank()) {
        desiredVideoOutput = value
      } else if (attachedSurfaceOwner != null) {
        MPVLib.setPropertyString("vo", desiredVideoOutput)
      }
    }"""

content = content.replace(old_guard, new_guard)

with open("app/src/main/java/com/quantummpv/app/ui/player/PlaybackSession.kt", "w") as f:
    f.write(content)

