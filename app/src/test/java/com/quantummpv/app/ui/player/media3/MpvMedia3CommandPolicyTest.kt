/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.media3

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvMedia3CommandPolicyTest {
  private val transportCommands =
    listOf(
      MpvMedia3Command.PLAY_PAUSE,
      MpvMedia3Command.STOP,
      MpvMedia3Command.SEEK_TO_DEFAULT_POSITION,
      MpvMedia3Command.SEEK_IN_CURRENT_ITEM,
      MpvMedia3Command.SEEK_BACK,
      MpvMedia3Command.SEEK_FORWARD,
      MpvMedia3Command.SEEK_TO_ITEM,
      MpvMedia3Command.SEEK_TO_NEXT,
      MpvMedia3Command.SEEK_TO_PREVIOUS,
      MpvMedia3Command.SET_SPEED,
      MpvMedia3Command.SET_SHUFFLE_MODE,
    )

  private fun capabilities(
    isPlayable: Boolean = true,
    canSeek: Boolean = true,
    hasQueue: Boolean = true,
    hasNextItem: Boolean = false,
    hasPreviousItem: Boolean = false,
  ) = MpvMedia3Capabilities(
    isPlayable = isPlayable,
    canSeek = canSeek,
    hasQueue = hasQueue,
    hasNextItem = hasNextItem,
    hasPreviousItem = hasPreviousItem,
  )

  @Test
  fun readsAreAlwaysAvailableSoControllersAlwaysSeeCurrentState() {
    val idle =
      MpvMedia3CommandPolicy.availableCommands(
        capabilities(isPlayable = false, canSeek = false, hasQueue = false),
      )

    assertEquals(MpvMedia3CommandPolicy.READ_ONLY_COMMANDS, idle)
    assertTrue(idle.contains(MpvMedia3Command.READ_CURRENT_ITEM))
    assertTrue(idle.contains(MpvMedia3Command.READ_METADATA))
    assertTrue(idle.contains(MpvMedia3Command.READ_TIMELINE))
  }

  @Test
  fun aSessionWithoutMediaExposesNoTransportAtAll() {
    val commands =
      MpvMedia3CommandPolicy.availableCommands(
        capabilities(isPlayable = false, canSeek = false, hasQueue = false),
      )

    transportCommands.forEach { command -> assertFalse(commands.contains(command), "$command must stay unavailable") }
  }

  @Test
  fun aPlayingSessionExposesPlayPauseAndStop() {
    val commands = MpvMedia3CommandPolicy.availableCommands(capabilities())

    assertTrue(commands.contains(MpvMedia3Command.PLAY_PAUSE))
    assertTrue(commands.contains(MpvMedia3Command.STOP))
  }

  @Test
  fun seekingIsOnlyAdvertisedWhenTheDurationIsKnown() {
    val seekCommands =
      listOf(
        MpvMedia3Command.SEEK_TO_DEFAULT_POSITION,
        MpvMedia3Command.SEEK_IN_CURRENT_ITEM,
        MpvMedia3Command.SEEK_BACK,
        MpvMedia3Command.SEEK_FORWARD,
      )

    val withoutDuration = MpvMedia3CommandPolicy.availableCommands(capabilities(canSeek = false))
    val withDuration = MpvMedia3CommandPolicy.availableCommands(capabilities(canSeek = true))

    seekCommands.forEach { command ->
      assertFalse(withoutDuration.contains(command), "$command must stay unavailable without a duration")
      assertTrue(withDuration.contains(command), "$command must be available with a duration")
    }
  }

  @Test
  fun queueNavigationFollowsTheActualQueueNeighbours() {
    val noNeighbours = MpvMedia3CommandPolicy.availableCommands(capabilities())
    val bothNeighbours =
      MpvMedia3CommandPolicy.availableCommands(capabilities(hasNextItem = true, hasPreviousItem = true))
    val nextOnly = MpvMedia3CommandPolicy.availableCommands(capabilities(hasNextItem = true))

    assertFalse(noNeighbours.contains(MpvMedia3Command.SEEK_TO_NEXT))
    assertFalse(noNeighbours.contains(MpvMedia3Command.SEEK_TO_PREVIOUS))
    assertTrue(bothNeighbours.contains(MpvMedia3Command.SEEK_TO_NEXT))
    assertTrue(bothNeighbours.contains(MpvMedia3Command.SEEK_TO_PREVIOUS))
    assertTrue(nextOnly.contains(MpvMedia3Command.SEEK_TO_NEXT))
    assertFalse(nextOnly.contains(MpvMedia3Command.SEEK_TO_PREVIOUS))
  }

  @Test
  fun queueJumpingAndShuffleRequireAKnownQueue() {
    val commands = MpvMedia3CommandPolicy.availableCommands(capabilities(hasQueue = false))

    assertFalse(commands.contains(MpvMedia3Command.SEEK_TO_ITEM))
    assertFalse(commands.contains(MpvMedia3Command.SET_SHUFFLE_MODE))
  }

  @Test
  fun playbackSpeedIsAlwaysOfferedForPlayableMedia() {
    assertTrue(
      MpvMedia3CommandPolicy.availableCommands(capabilities()).contains(MpvMedia3Command.SET_SPEED),
    )
    assertFalse(
      MpvMedia3CommandPolicy
        .availableCommands(capabilities(isPlayable = false))
        .contains(MpvMedia3Command.SET_SPEED),
    )
  }

  /**
   * Locks the externally controllable surface.
   *
   * Queue mutation, volume, repeat, and track selection are intentionally absent: libmpv owns
   * them, so they must not leak into the generic Media3 player model.
   */
  @Test
  fun theExposedSurfaceStaysDeliberatelySmall() {
    assertEquals(
      setOf(
        "READ_CURRENT_ITEM",
        "READ_METADATA",
        "READ_TIMELINE",
        "RELEASE",
        "PLAY_PAUSE",
        "STOP",
        "SEEK_TO_DEFAULT_POSITION",
        "SEEK_IN_CURRENT_ITEM",
        "SEEK_BACK",
        "SEEK_FORWARD",
        "SEEK_TO_ITEM",
        "SEEK_TO_NEXT",
        "SEEK_TO_PREVIOUS",
        "SET_SPEED",
        "SET_SHUFFLE_MODE",
      ),
      MpvMedia3Command.entries.map { command -> command.name }.toSet(),
    )
  }
}
