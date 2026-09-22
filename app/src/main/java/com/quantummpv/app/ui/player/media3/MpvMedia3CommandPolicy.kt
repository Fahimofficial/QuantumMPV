/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.media3

/**
 * Android-facing transport capabilities of the current libmpv session.
 *
 * This is intentionally a plain data model. Only the Media3 adapter translates it into
 * `androidx.media3.common.Player` command constants, which keeps the policy pure Kotlin (and
 * unit-testable) and keeps Media3 out of the playback domain.
 */
data class MpvMedia3Capabilities(
  /** True while libmpv owns loaded or loading media, i.e. transport is meaningful at all. */
  val isPlayable: Boolean,
  /** True when libmpv reports a duration, which is what makes seeking meaningful. */
  val canSeek: Boolean,
  val hasNextItem: Boolean,
  val hasPreviousItem: Boolean,
  /** True when the queue is known, so jumping to a queue entry is meaningful. */
  val hasQueue: Boolean,
)

/**
 * The transport surface QuantumMPV exposes to Android media controllers.
 *
 * Anything absent from this enum is deliberately not offered through Media3: queue mutation,
 * volume control, repeat control, track selection, and every libmpv-only feature (shaders,
 * scripts, filters, subtitle styling) stay inside QuantumMPV.
 */
enum class MpvMedia3Command {
  READ_CURRENT_ITEM,
  READ_METADATA,
  READ_TIMELINE,
  RELEASE,
  PLAY_PAUSE,
  STOP,
  SEEK_TO_DEFAULT_POSITION,
  SEEK_IN_CURRENT_ITEM,
  SEEK_BACK,
  SEEK_FORWARD,
  SEEK_TO_ITEM,
  SEEK_TO_NEXT,
  SEEK_TO_PREVIOUS,
  SET_SPEED,
  SET_SHUFFLE_MODE,
}

/**
 * Decides which Media3 player commands QuantumMPV advertises.
 *
 * The rule is "advertise only what libmpv can really do right now". A command that would need a
 * second playback pipeline, an ExoPlayer path, or semantics QuantumMPV does not have is left
 * unavailable instead of being faked.
 */
object MpvMedia3CommandPolicy {
  /**
   * State reads are always available: the adapter reads libmpv directly, so metadata and the queue
   * are never stale-by-design, unlike a mirrored copy.
   */
  val READ_ONLY_COMMANDS: Set<MpvMedia3Command> =
    setOf(
      MpvMedia3Command.READ_CURRENT_ITEM,
      MpvMedia3Command.READ_METADATA,
      MpvMedia3Command.READ_TIMELINE,
      MpvMedia3Command.RELEASE,
    )

  fun availableCommands(capabilities: MpvMedia3Capabilities): Set<MpvMedia3Command> {
    val commands = READ_ONLY_COMMANDS.toMutableSet()
    if (!capabilities.isPlayable) return commands

    commands += MpvMedia3Command.PLAY_PAUSE
    commands += MpvMedia3Command.STOP
    if (capabilities.canSeek) {
      commands += MpvMedia3Command.SEEK_TO_DEFAULT_POSITION
      commands += MpvMedia3Command.SEEK_IN_CURRENT_ITEM
      commands += MpvMedia3Command.SEEK_BACK
      commands += MpvMedia3Command.SEEK_FORWARD
    }
    if (capabilities.hasQueue) {
      commands += MpvMedia3Command.SEEK_TO_ITEM
      commands += MpvMedia3Command.SET_SHUFFLE_MODE
    }
    if (capabilities.hasPreviousItem) commands += MpvMedia3Command.SEEK_TO_PREVIOUS
    if (capabilities.hasNextItem) commands += MpvMedia3Command.SEEK_TO_NEXT
    // Playback speed is a real libmpv property, so it stays a genuine (not emulated) capability.
    commands += MpvMedia3Command.SET_SPEED
    return commands
  }
}
