/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import org.junit.Test
import kotlin.test.assertEquals

class SubtitlePositioningTest {
  @Test
  fun `manual secondary subtitle position is preserved`() {
    assertEquals(
      62,
      resolveSecondarySubtitlePosition(
        primaryPosition = 100,
        preferredSecondaryPosition = 62,
      ),
    )
  }

  @Test
  fun `manual secondary subtitle position is clamped to mpv range`() {
    assertEquals(
      150,
      resolveSecondarySubtitlePosition(
        primaryPosition = 100,
        preferredSecondaryPosition = 200,
      ),
    )
  }
}
