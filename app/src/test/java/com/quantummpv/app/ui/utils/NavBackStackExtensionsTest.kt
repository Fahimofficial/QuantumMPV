/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.utils

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavBackStackExtensionsTest {
  data class TestScreen(
    val id: Int,
  ) : NavKey

  private class TestNavBackStack(
    initialList: List<TestScreen>,
  ) : NavBackStack<TestScreen>(),
    MutableList<TestScreen> by initialList.toMutableList()

  @Test
  fun `popSafely returns false and does not mutate when stack is empty`() {
    val stack = TestNavBackStack(emptyList())

    val result = stack.popSafely()

    assertFalse(result)
    assertEquals(0, stack.size)
  }

  @Test
  fun `popSafely returns false and does not mutate when stack has one element`() {
    val stack = TestNavBackStack(listOf(TestScreen(1)))

    val result = stack.popSafely()

    assertFalse(result)
    assertEquals(1, stack.size)
  }

  @Test
  fun `popSafely returns true and removes last element when stack has multiple elements`() {
    val stack = TestNavBackStack(listOf(TestScreen(1), TestScreen(2), TestScreen(3)))

    val result = stack.popSafely()

    assertTrue(result)
    assertEquals(2, stack.size)
    assertEquals(TestScreen(1), stack[0])
    assertEquals(TestScreen(2), stack[1])
  }
}
