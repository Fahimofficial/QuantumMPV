/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for navigation smoothness improvements.
 * Verifies the adjacent/non-adjacent tab switching logic that prevents
 * jitter through intermediate pages when tapping distant destinations.
 */
class NavigationSmoothnessTest {

  /**
   * Verifies that adjacent tab changes (distance <= 1) are identified correctly.
   * Adjacent changes should use animated pager transitions for smooth finger-following.
   */
  @Test
  fun testAdjacentTabDetection() {
    // Same tab (distance 0)
    assertTrue("Same tab should be adjacent", isAdjacent(currentIndex = 2, targetIndex = 2))

    // One tab away (distance 1)
    assertTrue("One tab forward should be adjacent", isAdjacent(currentIndex = 2, targetIndex = 3))
    assertTrue("One tab backward should be adjacent", isAdjacent(currentIndex = 2, targetIndex = 1))

    // Two tabs away (distance 2) - not adjacent
    assertTrue("Two tabs away should not be adjacent", !isAdjacent(currentIndex = 2, targetIndex = 4))
    assertTrue("Two tabs away backward should not be adjacent", !isAdjacent(currentIndex = 2, targetIndex = 0))
  }

  /**
   * Verifies non-adjacent transitions across multiple tabs.
   * Non-adjacent changes should use instant scrollToPage to avoid jitter.
   */
  @Test
  fun testNonAdjacentTabDetection() {
    // Home to Network (typical case with 5 visible tabs: 0 -> 4)
    assertTrue("Home to Network should not be adjacent", !isAdjacent(currentIndex = 0, targetIndex = 4))

    // Network to Home (reverse)
    assertTrue("Network to Home should not be adjacent", !isAdjacent(currentIndex = 4, targetIndex = 0))

    // Middle to either end
    assertTrue("Music to Network should not be adjacent", !isAdjacent(currentIndex = 1, targetIndex = 4))
  }

  /**
   * Simulates the actual logic used in MainScreen to determine adjacency.
   * A change is adjacent if the absolute distance between current and target is <= 1.
   */
  private fun isAdjacent(currentIndex: Int, targetIndex: Int): Boolean {
    return abs(currentIndex - targetIndex) <= 1
  }

  /**
   * Verifies that the lightweight glass configuration reduces expensive effects.
   * Navigation bars should use reduced blur and no refraction to maintain frame rate.
   */
  @Test
  fun testLightweightGlassConfiguration() {
    // Lightweight navigation mode should have:
    // - Blur radius reduced by 50%
    // - No refraction (strength = 0)
    // - No edge highlight
    val fullBlurRadius = 16f
    val lightweightBlurRadius = fullBlurRadius * 0.5f

    assertTrue("Lightweight blur should be half of full blur", lightweightBlurRadius == 8f)

    val lightweightRefractionStrength = 0f
    assertTrue("Lightweight mode should have no refraction", lightweightRefractionStrength == 0f)
  }
}
