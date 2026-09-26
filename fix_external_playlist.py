import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "r") as f:
    content = f.read()

old_block = """    // Check if this intent has playlist information
    val hasPlaylistExtras =
      intent.hasExtra("playlist_id") ||
        intent.hasExtra("playlist")

    // Load playlist from intent extras first (fast path)
    val playlistFromIntent =
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra("playlist") ?: emptyList()
      }"""

new_block = """    // External file managers / SAF multiple selections
    val isSendMultiple = intent.action == Intent.ACTION_SEND_MULTIPLE
    val multipleUris = if (isSendMultiple) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
      }
    } else emptyList()

    val videoListUris = intent.getStringArrayListExtra("video_list")?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() } ?: emptyList()
    
    // Check if this intent has playlist information
    val hasPlaylistExtras =
      intent.hasExtra("playlist_id") ||
        intent.hasExtra("playlist") || isSendMultiple || videoListUris.isNotEmpty()

    // Load playlist from intent extras first (fast path)
    val playlistFromIntent = (
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayListExtra("playlist", Uri::class.java) ?: emptyList()
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>("playlist") ?: emptyList()
      }
    ) + multipleUris + videoListUris"""

content = content.replace(old_block, new_block)

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "w") as f:
    f.write(content)
