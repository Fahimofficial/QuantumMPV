import re

with open("app/src/main/java/com/quantummpv/app/ui/player/MediaPlaybackService.kt", "r") as f:
    content = f.read()

old_read_int = """  private fun readMpvIntSeconds(
    property: String,
    fallback: Int,
  ): Int =
    runCatching {
      PlaybackSession.getPropertyDouble(property)?.toInt()
        ?: PlaybackSession.getPropertyInt(property)
        ?: fallback
    }.getOrDefault(fallback)"""

new_read_int = """  private fun readMpvIntSeconds(
    property: String,
    fallback: Int,
  ): Int =
    runCatching {
      PlaybackSession.propDouble[property].value?.toInt()
        ?: PlaybackSession.propInt[property].value
        ?: fallback
    }.getOrDefault(fallback)"""

old_read_double = """  private fun readMpvDouble(
    property: String,
    fallback: Double,
  ): Double =
    runCatching {
      PlaybackSession.getPropertyDouble(property) ?: fallback
    }.getOrDefault(fallback)"""

new_read_double = """  private fun readMpvDouble(
    property: String,
    fallback: Double,
  ): Double =
    runCatching {
      PlaybackSession.propDouble[property].value ?: fallback
    }.getOrDefault(fallback)"""

old_read_track = """  private fun readMpvTrackId(
    property: String,
    fallback: Int,
  ): Int =
    runCatching {
      when (val value = PlaybackSession.getPropertyString(property)) {
        null -> fallback
        "no" -> -1
        else -> value.toIntOrNull() ?: fallback
      }
    }.getOrDefault(fallback)"""

new_read_track = """  private fun readMpvTrackId(
    property: String,
    fallback: Int,
  ): Int =
    runCatching {
      when (val value = PlaybackSession.propString[property].value) {
        null -> fallback
        "no" -> -1
        else -> value.toIntOrNull() ?: fallback
      }
    }.getOrDefault(fallback)"""

content = content.replace(old_read_int, new_read_int)
content = content.replace(old_read_double, new_read_double)
content = content.replace(old_read_track, new_read_track)

with open("app/src/main/java/com/quantummpv/app/ui/player/MediaPlaybackService.kt", "w") as f:
    f.write(content)

