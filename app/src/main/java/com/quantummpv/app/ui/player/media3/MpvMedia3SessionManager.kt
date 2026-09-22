/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.media3

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns the single Media3 `MediaSession` that exposes QuantumMPV playback to Android.
 *
 * The session is hosted by the existing playback service instead of a `MediaSessionService`, so
 * mpv's lifecycle, the foreground notification, audio-focus ownership, and the Activity handoff all
 * stay exactly as they are. Nothing in this class starts, stops, or duplicates that service.
 *
 * Responsibilities:
 * - create the session on top of the libmpv-backed [MpvMedia3Player]
 * - publish only the commands in [MpvMedia3CommandPolicy] to controllers
 * - publish visibility, mirroring the service's existing notification/ownership state
 * - route controller commands into [MpvMedia3SessionHost] (the service)
 * - release the session with the service
 *
 * All session calls happen on the application looper.
 *
 * The manager opts into Media3's unstable session surface itself, so hosts (the playback service and
 * the foreground player Activity) treat it as an ordinary QuantumMPV type and never have to opt in to
 * a Media3 marker.
 */
@OptIn(UnstableApi::class)
class MpvMedia3SessionManager(
  context: Context,
  private val host: MpvMedia3SessionHost,
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val player = MpvMedia3Player(context, host)
  private val session =
    MediaSession
      .Builder(context, player)
      .setCallback(SessionCallback())
      .build()

  /**
   * Media3 is active only while it is the publishing owner of playback.
   *
   * This mirrors the previous `MediaSessionCompat.isActive` flag: false while a foreground
   * `PlayerActivity` owns playback, while detached background playback is disabled, and while the
   * progress-style notification is the user's chosen surface.
   */
  @Volatile
  private var publishing = false

  /** Session token for the app's own MediaStyle notification. */
  val mediaStyleToken: MediaSessionCompat.Token
    get() = MediaSessionCompat.Token.fromToken(session.platformToken)

  /** Republishes libmpv state to Media3 controllers. */
  fun refresh() {
    onMain { player.refresh() }
  }

  /** Keeps the notification tap target on the session, as the legacy session did. */
  fun setSessionActivity(pendingIntent: PendingIntent?) {
    onMain { session.setSessionActivity(pendingIntent) }
  }

  /**
   * Publishes or withdraws the transport surface.
   *
   * Withdrawing means controllers see no available commands, so no external surface can act on a
   * playback session the service does not currently own.
   */
  fun setPublishing(enabled: Boolean) {
    if (publishing == enabled) return
    publishing = enabled
    onMain { applyCommandsToConnectedControllers() }
  }

  /** Routes a media button intent delivered to the service's manifest receiver. */
  fun handleMediaButtonIntent(intent: Intent): Boolean = host.onMediaButtonIntent(intent)

  /** Releases the session, then the adapter. libmpv itself is owned by `PlaybackSession`. */
  fun release() {
    onMain {
      session.release()
      player.release()
    }
  }

  private fun applyCommandsToConnectedControllers() {
    val sessionCommands = availableSessionCommands()
    val playerCommands = availablePlayerCommands()
    session.connectedControllers.forEach { controller ->
      session.setAvailableCommands(controller, sessionCommands, playerCommands)
    }
  }

  private fun availableSessionCommands(): SessionCommands =
    if (publishing) connectedSessionCommands() else SessionCommands.EMPTY

  private fun availablePlayerCommands(): Player.Commands =
    if (publishing) player.availableCommands else Player.Commands.EMPTY

  /**
   * Custom commands controllers may invoke. They carry the same non-transport actions the host
   * exposes (the notification's favorite and close actions); they are not playback semantics, so
   * Media3 treats them as custom session commands rather than media controls.
   */
  private fun connectedSessionCommands(): SessionCommands {
    val actions = host.availableCustomActions()
    if (actions.isEmpty()) return SessionCommands.EMPTY
    val builder = SessionCommands.Builder()
    actions.forEach { action -> builder.add(SessionCommand(action, Bundle.EMPTY)) }
    return builder.build()
  }

  private inner class SessionCallback : MediaSession.Callback {
    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
      MediaSession.ConnectionResult
        .AcceptedResultBuilder(session, controller)
        .setAvailableSessionCommands(availableSessionCommands())
        .setAvailablePlayerCommands(availablePlayerCommands())
        .build()

    /**
     * Media buttons stay inside QuantumMPV's own handling so the user's configured media-button
     * gestures keep working, instead of Media3 turning every button into a transport command.
     */
    override fun onMediaButtonEvent(
      session: MediaSession,
      controllerInfo: MediaSession.ControllerInfo,
      intent: Intent,
    ): Boolean = host.onMediaButtonIntent(intent)

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      if (!host.canApplyTransportCommands()) {
        // SessionError is the current error-code space; the legacy SessionResult.RESULT_ERROR_*
        // constants are no longer accepted and fail Android lint.
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
      }
      host.onCustomSessionAction(customCommand.customAction)
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
  }

  private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
  }

  companion object {
    /** Custom session command for the notification favorite action. */
    const val CUSTOM_ACTION_FAVORITE = "com.quantummpv.app.media3.action.FAVORITE"

    /** Custom session command for the notification close action. */
    const val CUSTOM_ACTION_CLOSE = "com.quantummpv.app.media3.action.CLOSE"
  }
}
