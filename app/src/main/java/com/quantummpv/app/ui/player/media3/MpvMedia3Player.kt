/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.media3

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.quantummpv.app.R
import com.quantummpv.app.ui.player.PlaybackItem
import com.quantummpv.app.ui.player.PlaybackQueueState
import com.quantummpv.app.ui.player.PlaybackSession
import com.quantummpv.app.ui.player.RepeatMode
import com.quantummpv.app.utils.storage.FileTypeUtils
import com.quantummpv.app.utils.UrlSanitizer

/**
 * An `androidx.media3.common.Player` whose playback engine is the shared libmpv session.
 *
 * This is the only bridge between QuantumMPV playback and Media3, and it exists so Android's media
 * ecosystem (system media controls, lock screen, Bluetooth buttons, Android Auto, MediaController
 * clients) can drive playback that libmpv decodes, renders and times.
 *
 * Boundaries this class deliberately keeps:
 * - libmpv stays the single source of truth: every `getState()` reads the live session and its
 *   cached libmpv properties, so no playback state is mirrored or recomputed here.
 * - It only exposes the transport surface in [MpvMedia3CommandPolicy]. Anything libmpv-specific
 *   (shaders, scripts, filters, subtitles, HDR, track selection, queue editing) is not offered
 *   through the generic Media3 player model.
 * - There is no ExoPlayer path, no Media3 fallback, and no engine routing: commands either reach
 *   libmpv through [MpvMedia3SessionHost] or are simply unavailable.
 * - Rendering, buffering, codec, subtitle, and timing decisions stay in libmpv; Media3 never sees
 *   a decoder, a renderer, or a media source.
 *
 * Instances must be created and called on the application looper (the main thread), matching where
 * [PlaybackSession] is already driven.
 *
 * The class opts into Media3's unstable `Player` implementation surface itself, so callers see it as
 * an ordinary QuantumMPV type and never have to opt in to a Media3 marker.
 */
@OptIn(UnstableApi::class)
class MpvMedia3Player(
  private val context: Context,
  private val host: MpvMedia3SessionHost,
) : SimpleBasePlayer(Looper.getMainLooper()) {
  private var cachedQueueState: PlaybackQueueState? = null
  private var cachedPlaylist: List<SimpleBasePlayer.MediaItemData> = emptyList()

  /**
   * Republishes the current libmpv state to Media3 listeners.
   *
   * The playback service calls this when libmpv reports a change, exactly where it previously
   * rebuilt its `MediaSessionCompat` metadata/playback state.
   */
  fun refresh() {
    invalidateState()
  }

  override fun getState(): SimpleBasePlayer.State {
    val sessionState = PlaybackSession.state.value
    val queueState = PlaybackSession.queue.value
    val snapshot =
      MpvMedia3StateMapper.snapshot(
        phase = sessionState.phase,
        paused = sessionState.paused,
        endOfFileReached = PlaybackSession.propBoolean[PROPERTY_END_OF_FILE].value == true,
        positionSeconds = PlaybackSession.propDouble[PROPERTY_POSITION].value,
        durationSeconds = PlaybackSession.propDouble[PROPERTY_DURATION].value,
        speed = PlaybackSession.propDouble[PROPERTY_SPEED].value,
        hasQueue = queueState.items.isNotEmpty() && queueState.currentItem != null,
        hasNextItem = PlaybackSession.hasNext(),
        hasPreviousItem = PlaybackSession.hasPrevious(),
      )
    val playlist = playlistFor(queueState, snapshot.durationMs)
    val playbackState = playbackStateFor(snapshot.playbackState, playlist.isEmpty())

    val stateBuilder = SimpleBasePlayer.State.Builder()
    stateBuilder
      .setAvailableCommands(commandsFor(snapshot.capabilities))
      .setPlayWhenReady(snapshot.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      .setIsLoading(snapshot.isLoading && playbackState == Player.STATE_BUFFERING)
      .setPlaybackState(playbackState)
      .setPlayerError(playbackErrorFor(sessionState.error))
      .setRepeatMode(queueState.repeatMode.toMedia3RepeatMode())
      .setShuffleModeEnabled(queueState.shuffleEnabled)
      .setPlaybackParameters(PlaybackParameters(snapshot.speed))
      .setSeekBackIncrementMs(host.seekIncrementMillis())
      .setSeekForwardIncrementMs(host.seekIncrementMillis())
      .setContentPositionMs(snapshot.positionMs)

    if (playlist.isEmpty()) {
      stateBuilder.setPlaylist(emptyList())
    } else {
      stateBuilder
        .setPlaylist(playlist)
        .setCurrentMediaItemIndex(queueState.currentIndex.coerceIn(0, playlist.lastIndex))
    }
    return stateBuilder.build()
  }

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    if (host.canApplyTransportCommands()) {
      if (playWhenReady) host.onPlayRequested() else host.onPauseRequested()
    }
    invalidateState()
    return Futures.immediateVoidFuture()
  }

  override fun handleSeek(
    mediaItemIndex: Int,
    positionMs: Long,
    seekCommand: Int,
  ): ListenableFuture<*> {
    if (host.canApplyTransportCommands()) {
      applySeek(mediaItemIndex, positionMs, seekCommand)
    }
    invalidateState()
    return Futures.immediateVoidFuture()
  }

  override fun handleStop(): ListenableFuture<*> {
    if (host.canApplyTransportCommands()) host.onStopRequested()
    invalidateState()
    return Futures.immediateVoidFuture()
  }

  override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
    if (host.canApplyTransportCommands()) host.onSetPlaybackSpeedRequested(playbackParameters.speed)
    invalidateState()
    return Futures.immediateVoidFuture()
  }

  override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
    if (host.canApplyTransportCommands()) host.onSetShuffleRequested(shuffleModeEnabled)
    invalidateState()
    return Futures.immediateVoidFuture()
  }

  /**
   * libmpv is owned by [PlaybackSession], not by this player, so releasing the Media3 player must
   * never tear down the engine. The service releases the native core on its own terms.
   */
  override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

  private fun applySeek(
    mediaItemIndex: Int,
    positionMs: Long,
    seekCommand: Int,
  ) {
    when (seekCommand) {
      Player.COMMAND_SEEK_TO_NEXT,
      Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
      -> host.onSkipToNextRequested()
      Player.COMMAND_SEEK_TO_PREVIOUS,
      Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
      -> host.onSkipToPreviousRequested()
      Player.COMMAND_SEEK_BACK -> host.onIncrementalSeekRequested(MpvMedia3SeekDirection.BACK)
      Player.COMMAND_SEEK_FORWARD -> host.onIncrementalSeekRequested(MpvMedia3SeekDirection.FORWARD)
      Player.COMMAND_SEEK_TO_MEDIA_ITEM,
      Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
      -> if (mediaItemIndex != C.INDEX_UNSET) host.onSeekToItemRequested(mediaItemIndex)
      else -> if (positionMs != C.TIME_UNSET) host.onSeekToPositionRequested(positionMs)
    }
  }

  private fun commandsFor(capabilities: MpvMedia3Capabilities): Player.Commands {
    val builder = Player.Commands.Builder()
    MpvMedia3CommandPolicy
      .availableCommands(capabilities)
      .flatMap(MpvMedia3Command::toPlayerCommands)
      .forEach { command -> builder.add(command) }
    return builder.build()
  }

  /**
   * Media3 only accepts an empty, an idle, or an ended timeline, and it only accepts a player error
   * in `STATE_IDLE`. ERROR therefore becomes IDLE plus a sanitized [PlaybackException].
   */
  internal fun playbackStateFor(
    state: MpvMedia3PlaybackState,
    hasPlaylist: Boolean,
  ): Int = Companion.playbackStateFor(state, hasPlaylist)

  /**
   * Raw libmpv errors can contain authenticated URLs, cookies, or filesystem paths, so only the
   * fact of failure and a user-facing summary cross the Media3 boundary.
   */
  private fun playbackErrorFor(error: String?): PlaybackException? {
    if (error.isNullOrBlank()) return null
    return PlaybackException(
      context.getString(R.string.media_session_playback_error),
      null,
      PlaybackException.ERROR_CODE_UNSPECIFIED,
    )
  }

  /**
   * The queue is published as the Media3 timeline so controllers can show queue position and jump
   * between entries, matching what the notification previously published as a `MediaSession` queue.
   */
  private fun playlistFor(
    queueState: PlaybackQueueState,
    liveDurationMs: Long,
  ): List<SimpleBasePlayer.MediaItemData> {
    val base = playlistCacheFor(queueState)
    val currentIndex = queueState.currentIndex
    if (currentIndex !in base.indices) return base
    // The current entry carries live libmpv duration and artwork; the rest keep their queued
    // metadata so a 200 entry queue is not rebuilt on every position update.
    val liveCurrent =
      mediaItemData(
        item = queueState.items[currentIndex],
        index = currentIndex,
        durationMs = liveDurationMs,
        artworkBytes = host.currentArtworkBytes(),
      )
    return base.toMutableList().also { playlist -> playlist[currentIndex] = liveCurrent }
  }

  private fun playlistCacheFor(queueState: PlaybackQueueState): List<SimpleBasePlayer.MediaItemData> {
    val cached = cachedQueueState
    if (cached === queueState && cachedPlaylist.size == queueState.items.size) return cachedPlaylist
    val built =
      queueState.items.mapIndexed { index, item ->
        mediaItemData(
          item = item,
          index = index,
          durationMs = MpvMedia3StateMapper.secondsToMillis(item.durationSeconds?.toDouble()),
          artworkBytes = null,
        )
      }
    cachedQueueState = queueState
    cachedPlaylist = built
    return built
  }

  private fun mediaItemData(
    item: PlaybackItem,
    index: Int,
    durationMs: Long,
    artworkBytes: ByteArray?,
  ): SimpleBasePlayer.MediaItemData {
    val metadata = mediaMetadataFor(item, durationMs, artworkBytes)
    val safeId = UrlSanitizer.sanitize(item.stableId) ?: item.stableId
    return SimpleBasePlayer.MediaItemData
      // The index keeps the Media3 item UID unique even when the same file is queued twice.
      .Builder("$safeId#$index")
      .setMediaItem(
        MediaItem
          .Builder()
          .setMediaId(safeId)
          .setMediaMetadata(metadata)
          .build(),
      ).setMediaMetadata(metadata)
      .setDurationUs(durationMs.toMicrosOrTimeUnset())
      .setIsSeekable(durationMs > 0L)
      .build()
  }

  private fun mediaMetadataFor(
    item: PlaybackItem,
    durationMs: Long,
    artworkBytes: ByteArray?,
  ): MediaMetadata {
    val mpvTitle = PlaybackSession.propString[PROPERTY_TITLE].value
    val title =
      item.title?.let(FileTypeUtils::stripExtension)?.takeIf(String::isNotBlank)
        ?: mpvTitle?.let(FileTypeUtils::stripExtension)?.takeIf(String::isNotBlank)
        ?: context.getString(R.string.player_unknown_video)
    val artist =
      item.artist?.takeIf(String::isNotBlank)
        ?: PlaybackSession.propString[PROPERTY_ARTIST].value.orEmpty()
    val album = PlaybackSession.propString[PROPERTY_ALBUM].value.orEmpty()
    return MediaMetadata
      .Builder()
      .setTitle(title)
      .setDisplayTitle(title)
      .setArtist(artist.takeIf(String::isNotBlank))
      .setAlbumTitle(album.takeIf(String::isNotBlank))
      .setDurationMs(durationMs.takeIf { it > 0L })
      .setIsPlayable(true)
      // Artwork travels as decoded bytes: item artwork URIs can carry provider credentials.
      .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
      .build()
  }

  private fun Long.toMicrosOrTimeUnset(): Long =
    if (this <= 0L) {
      C.TIME_UNSET
    } else {
      coerceAtMost(Long.MAX_VALUE / MICROS_PER_MILLI) * MICROS_PER_MILLI
    }

  internal companion object {
    const val MICROS_PER_MILLI = 1_000L
    const val PROPERTY_POSITION = "time-pos"
    const val PROPERTY_DURATION = "duration"
    const val PROPERTY_SPEED = "speed"
    const val PROPERTY_END_OF_FILE = "eof-reached"
    const val PROPERTY_TITLE = "media-title"
    const val PROPERTY_ARTIST = "metadata/artist"
    const val PROPERTY_ALBUM = "metadata/album"

    internal fun playbackStateFor(
      state: MpvMedia3PlaybackState,
      hasPlaylist: Boolean,
    ): Int =
      when {
        state == MpvMedia3PlaybackState.ERROR -> Player.STATE_IDLE
        !hasPlaylist -> if (state == MpvMedia3PlaybackState.ENDED) Player.STATE_ENDED else Player.STATE_IDLE
        state == MpvMedia3PlaybackState.IDLE -> Player.STATE_IDLE
        state == MpvMedia3PlaybackState.BUFFERING -> Player.STATE_BUFFERING
        state == MpvMedia3PlaybackState.READY -> Player.STATE_READY
        else -> Player.STATE_ENDED
      }
  }
}

/** Translates the policy surface into the Media3 player commands it stands for. */
@OptIn(UnstableApi::class)
private fun MpvMedia3Command.toPlayerCommands(): List<Int> =
  when (this) {
    MpvMedia3Command.READ_CURRENT_ITEM -> listOf(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
    MpvMedia3Command.READ_METADATA -> listOf(Player.COMMAND_GET_METADATA)
    MpvMedia3Command.READ_TIMELINE -> listOf(Player.COMMAND_GET_TIMELINE)
    MpvMedia3Command.RELEASE -> listOf(Player.COMMAND_RELEASE)
    MpvMedia3Command.PLAY_PAUSE -> listOf(Player.COMMAND_PLAY_PAUSE)
    MpvMedia3Command.STOP -> listOf(Player.COMMAND_STOP)
    MpvMedia3Command.SEEK_TO_DEFAULT_POSITION -> listOf(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
    MpvMedia3Command.SEEK_IN_CURRENT_ITEM -> listOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    MpvMedia3Command.SEEK_BACK -> listOf(Player.COMMAND_SEEK_BACK)
    MpvMedia3Command.SEEK_FORWARD -> listOf(Player.COMMAND_SEEK_FORWARD)
    MpvMedia3Command.SEEK_TO_ITEM -> listOf(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
    // Both variants: system UI asks for the next media item, Android Auto asks for the next
    // entry in the queue. Both mean "advance QuantumMPV's queue".
    MpvMedia3Command.SEEK_TO_NEXT ->
      listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    MpvMedia3Command.SEEK_TO_PREVIOUS ->
      listOf(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    MpvMedia3Command.SET_SPEED -> listOf(Player.COMMAND_SET_SPEED_AND_PITCH)
    MpvMedia3Command.SET_SHUFFLE_MODE -> listOf(Player.COMMAND_SET_SHUFFLE_MODE)
  }

private fun RepeatMode.toMedia3RepeatMode(): Int =
  when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
  }
