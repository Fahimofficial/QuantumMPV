/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntentSubtitleLoadPolicyTest {
  @Test
  fun loadsEverySubtitleWithSelectionFlagsWhenExtraPresent() {
    val subtitles = listOf("a", "b", "c")

    val entries = IntentSubtitleLoadPolicy.entriesToLoad(
      subtitles = subtitles,
      enabledSubtitles = listOf("b"),
      hasEnabledSubtitleExtra = true,
    )

    assertEquals(3, entries.size)
    assertEquals(0, entries[0].metadataIndex)
    assertFalse(entries[0].select)
    assertEquals(1, entries[1].metadataIndex)
    assertTrue(entries[1].select)
    assertEquals(2, entries[2].metadataIndex)
    assertFalse(entries[2].select)
  }

  @Test
  fun selectsNothingWithoutEnabledExtra() {
    val entries = IntentSubtitleLoadPolicy.entriesToLoad(
      subtitles = listOf("a", "b"),
      enabledSubtitles = listOf("b"),
      hasEnabledSubtitleExtra = false,
    )

    assertTrue(entries.all { !it.select })
    assertEquals(listOf(0, 1), entries.map { it.metadataIndex })
  }

  @Test
  fun appendsEnabledSubtitleMissingFromList() {
    val entries = IntentSubtitleLoadPolicy.entriesToLoad(
      subtitles = listOf("a", "b"),
      enabledSubtitles = listOf("a", "d"),
      hasEnabledSubtitleExtra = true,
    )

    assertEquals(3, entries.size)
    assertEquals("d", entries[2].value)
    assertEquals(-1, entries[2].metadataIndex)
    assertTrue(entries[2].select)
  }

  @Test
  fun deduplicatesSubtitleList() {
    val entries = IntentSubtitleLoadPolicy.entriesToLoad(
      subtitles = listOf("a", "a", "b"),
      enabledSubtitles = emptyList(),
      hasEnabledSubtitleExtra = false,
    )

    assertEquals(2, entries.size)
    assertEquals(0, entries[0].metadataIndex)
    assertEquals(2, entries[1].metadataIndex)
  }
}
