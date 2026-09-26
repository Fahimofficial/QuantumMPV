import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "r") as f:
    content = f.read()

old_load_playlist = """  private fun loadPlaylistItemInternal(
    index: Int,
    saveCurrentPlaybackState: Boolean = true,
    requestAlreadyStarted: Boolean = false,
  ) {"""

new_load_playlist = """  private fun loadPlaylistItemInternal(
    index: Int,
    saveCurrentPlaybackState: Boolean = true,
    requestAlreadyStarted: Boolean = false,
    isAutomaticTransition: Boolean = false,
  ) {"""
content = content.replace(old_load_playlist, new_load_playlist)

old_load_video = """      loadVideoInternal(
        playableUri = playableUri,
        originalUri = uri,
        mediaTitle = resolveTitle,
        headers = resolveHeaders,
        torrentIndex = torrentIndex,
        requestGeneration = requestGeneration,
        ytdlFormat = if (isYouTubeMedia(playableUri)) playerPreferences.ytDlQualityFormat.get() else null,
      )"""

new_load_video = """      loadVideoInternal(
        playableUri = playableUri,
        originalUri = uri,
        mediaTitle = resolveTitle,
        headers = resolveHeaders,
        torrentIndex = torrentIndex,
        requestGeneration = requestGeneration,
        ytdlFormat = if (isYouTubeMedia(playableUri)) playerPreferences.ytDlQualityFormat.get() else null,
        positionRestoreOverride = if (isAutomaticTransition) PlaybackPositionRestoreOverride(0.0) else null,
      )"""
content = content.replace(old_load_video, new_load_video)

old_next = """  override fun playNextQueueItem() {
    if (!PlaybackSession.hasNext() || !beginMediaRequest()) return
    PlaybackSession.selectNext() ?: return
    syncPlaylistFromSession()
    loadPlaylistItemInternal(
      index = PlaybackSession.queue.value.currentIndex,
      requestAlreadyStarted = true,
    )
  }"""

new_next = """  override fun playNextQueueItem() {
    if (!PlaybackSession.hasNext() || !beginMediaRequest()) return
    PlaybackSession.selectNext() ?: return
    syncPlaylistFromSession()
    loadPlaylistItemInternal(
      index = PlaybackSession.queue.value.currentIndex,
      requestAlreadyStarted = true,
      isAutomaticTransition = true,
    )
  }"""
content = content.replace(old_next, new_next)

old_prev = """  override fun playPreviousQueueItem() {
    if (!PlaybackSession.hasPrevious() || !beginMediaRequest()) return
    PlaybackSession.selectPrevious() ?: return
    syncPlaylistFromSession()
    loadPlaylistItemInternal(
      index = PlaybackSession.queue.value.currentIndex,
      requestAlreadyStarted = true,
    )
  }"""

new_prev = """  override fun playPreviousQueueItem() {
    if (!PlaybackSession.hasPrevious() || !beginMediaRequest()) return
    PlaybackSession.selectPrevious() ?: return
    syncPlaylistFromSession()
    loadPlaylistItemInternal(
      index = PlaybackSession.queue.value.currentIndex,
      requestAlreadyStarted = true,
      isAutomaticTransition = true,
    )
  }"""
content = content.replace(old_prev, new_prev)

with open("app/src/main/java/com/quantummpv/app/ui/player/PlayerActivity.kt", "w") as f:
    f.write(content)
