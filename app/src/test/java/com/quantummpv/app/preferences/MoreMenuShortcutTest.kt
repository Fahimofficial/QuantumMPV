package com.quantummpv.app.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class MoreMenuShortcutTest {
  @Test
  fun defaultShowsAppearanceOnly() {
    assertEquals(listOf(MoreMenuShortcut.APPEARANCE), MoreMenuShortcut.resolve(MoreMenuShortcut.DEFAULT.map { it.name }.toSet()))
  }

  @Test
  fun resolvesInStableOrderAndIgnoresUnknownNames() {
    val stored = setOf("LIBRARY_INSIGHTS", "SETTINGS", "SUPPORT", "REMOVED_IN_A_LATER_BUILD", "DECODER")

    // Settings and Support are fixed rows, so stored copies of them never produce duplicates.
    assertEquals(listOf(MoreMenuShortcut.DECODER, MoreMenuShortcut.LIBRARY_INSIGHTS), MoreMenuShortcut.resolve(stored))
  }

  @Test
  fun emptySelectionLeavesOnlyFixedRows() {
    assertEquals(emptyList<MoreMenuShortcut>(), MoreMenuShortcut.resolve(emptySet()))
  }
}
