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
import kotlin.test.assertNull

class SubtitleTitleMatcherTest {
  @Test
  fun picksSingleLongKeywordMatch() {
    val titles = listOf("English Full", "Japanese")

    assertEquals(0, SubtitleTitleMatcher.findBestMatchIndex(titles, listOf("english")))
  }

  @Test
  fun appliesPreferencesLeftToRight() {
    val titles = listOf("English Full", "English", "Japanese")

    assertEquals(
      0,
      SubtitleTitleMatcher.findBestMatchIndex(titles, listOf("english", "full")),
    )
  }

  @Test
  fun keepsCandidateWhenLaterKeywordMatchesNothing() {
    val titles = listOf("English Full", "Japanese")

    assertEquals(
      0,
      SubtitleTitleMatcher.findBestMatchIndex(titles, listOf("english", "klingon")),
    )
  }

  @Test
  fun shortCodesMatchOnlyWholeWords() {
    val titles = listOf("ch", "French")

    assertEquals(0, SubtitleTitleMatcher.findBestMatchIndex(titles, listOf("ch")))
    assertNull(SubtitleTitleMatcher.findBestMatchIndex(titles, listOf("en")))
  }

  @Test
  fun trimsAndSkipsBlankKeywords() {
    val titles = listOf("English", "Japanese")

    assertEquals(0, SubtitleTitleMatcher.findBestMatchIndex(titles, listOf(" ", "english")))
  }

  @Test
  fun returnsNullWithoutAnyMatch() {
    val titles = listOf("English", "Japanese")

    assertNull(SubtitleTitleMatcher.findBestMatchIndex(titles, listOf("klingon")))
    assertNull(SubtitleTitleMatcher.findBestMatchIndex(titles, emptyList()))
  }
}
