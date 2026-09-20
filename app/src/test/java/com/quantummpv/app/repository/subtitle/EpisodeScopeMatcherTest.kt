/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.repository.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpisodeScopeMatcherTest {
  private fun subtitle(
    name: String,
    metadata: Map<String, String> = emptyMap(),
  ): OnlineSubtitle =
    OnlineSubtitle(
      provider = SubtitleProvider.WYZIE,
      url = "https://example.com/$name",
      displayName = name,
      displayLanguage = "English",
      metadata = metadata,
    )

  @Test
  fun returnsListUnchangedWithoutSeasonOrEpisode() {
    val subtitles = listOf(subtitle("Movie"), subtitle("Other"))

    assertEquals(subtitles, EpisodeScopeMatcher.filter(subtitles, season = null, episode = 2))
    assertEquals(subtitles, EpisodeScopeMatcher.filter(subtitles, season = 1, episode = null))
  }

  @Test
  fun prefersMetadataExactMatch() {
    val exact = subtitle("Exact", metadata = mapOf("season" to "1", "episode" to "2"))
    val other = subtitle("Show s01e03 release")

    val result = EpisodeScopeMatcher.filter(listOf(exact, other), season = 1, episode = 2)

    assertEquals(listOf(exact), result)
  }

  @Test
  fun dropsConflictingSeasonEpisodeNames() {
    val wanted = subtitle("Show s01e03 release")
    val conflicting = subtitle("Show s02e03 release")

    val result = EpisodeScopeMatcher.filter(listOf(wanted, conflicting), season = 1, episode = 3)

    assertEquals(listOf(wanted), result)
  }

  @Test
  fun keepsUnmarkedSubtitlesWhenNothingScoresExactly() {
    val plain = subtitle("Random release")
    val other = subtitle("Another release")

    val result = EpisodeScopeMatcher.filter(listOf(plain, other), season = 1, episode = 3)

    assertEquals(listOf(plain, other), result)
  }

  @Test
  fun episodeTokenInNameOutranksPlainSubtitle() {
    val token = subtitle("Show s01e03")
    val plain = subtitle("Random release")
    val empty = listOf<OnlineSubtitle>()

    val result = EpisodeScopeMatcher.filter(listOf(plain, token), season = 1, episode = 3)

    assertEquals(listOf(token), result)
    assertTrue(EpisodeScopeMatcher.filter(empty, season = 1, episode = 3).isEmpty())
  }
}
