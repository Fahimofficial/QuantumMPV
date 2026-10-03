/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package com.quantummpv.app.ui.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleSelectionGuardTest {
  @Test
  fun manualSelectionBlocksAutomaticSelectionForTheSameGeneration() {
    val generation = 101L
    SubtitleSelectionGuard.markManual(generation)

    assertTrue(SubtitleSelectionGuard.isManualSelection(generation))
    assertFalse(SubtitleSelectionGuard.isManualSelection(generation + 1))
  }

  @Test
  fun aLaterPlaybackGenerationIsNotBlockedByAnOlderManualSelection() {
    SubtitleSelectionGuard.markManual(202L)

    assertFalse(SubtitleSelectionGuard.isManualSelection(203L))
  }

  @Test
  fun aManualOffChoiceIsStillProtectedForTheCurrentGeneration() {
    val generation = 303L
    SubtitleSelectionGuard.markManual(generation)

    // This models subtitle OFF followed by a refresh: the refresh must not auto-enable it.
    assertTrue(SubtitleSelectionGuard.isManualSelection(generation))
    assertFalse(SubtitleSelectionGuard.isManualSelection(304L))
  }
}
