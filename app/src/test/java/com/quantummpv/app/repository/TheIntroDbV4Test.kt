/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.repository

import com.quantummpv.app.ui.player.SkipMarkerResolver
import com.quantummpv.app.ui.player.SkipSegmentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TheIntroDB v4 responses, taken from the examples in its OpenAPI spec (https://theintrodb.org/openapi.yaml).
 * v4 bodies are keyed by `tvdb_id`, carry no `tmdb_id`, and give each segment type as a `start_ms`/`end_ms` array.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TheIntroDbV4Test {
  private val requests = mutableListOf<HttpUrl>()
  private val acceptHeaders = mutableListOf<String?>()

  private fun repository(
    code: Int,
    body: String,
  ): IntroDbRepository {
    val client =
      OkHttpClient
        .Builder()
        .addInterceptor(
          Interceptor { chain ->
            requests += chain.request().url
            acceptHeaders += chain.request().header("Accept")
            Response
              .Builder()
              .request(chain.request())
              .protocol(Protocol.HTTP_1_1)
              .code(code)
              .message("test")
              .body(body.toResponseBody("application/json".toMediaType()))
              .build()
          },
        ).build()
    // Same configuration as the app's Json (DatabaseModule).
    return IntroDbRepository(client, Json { isLenient = true; ignoreUnknownKeys = true })
  }

  @Test
  fun movieResponseYieldsEverySegmentTypeWithItsTimes() {
    val segments =
      runBlocking {
        repository(200, MOVIE_MEDIA).getTheIntroDbSegments(tmdbId = 550, mediaType = "movie")
      }.getOrThrow()

    assertEquals(listOf("intro", "recap", "credits", "credits", "preview"), segments.map { it.segmentType })
    // An intro that starts at the very beginning comes back with start_ms = null.
    assertNull(segments[0].startSecondsOrNull)
    assertEquals(23.0, segments[0].endSecondsOrNull!!, 1e-9)
    assertEquals(25.0, segments[1].startSecondsOrNull!!, 1e-9)
    assertEquals(134.0, segments[1].endSecondsOrNull!!, 1e-9)
    assertEquals(5801.777, segments[2].startSecondsOrNull!!, 1e-9)
    assertEquals(6371.111, segments[2].endSecondsOrNull!!, 1e-9)
    // Credits that run to the end of the media come back with end_ms = null.
    assertEquals(6408.0, segments[3].startSecondsOrNull!!, 1e-9)
    assertNull(segments[3].endSecondsOrNull)
    assertEquals(1680.0, segments[4].startSecondsOrNull!!, 1e-9)
    assertEquals(1740.0, segments[4].endSecondsOrNull!!, 1e-9)
  }

  @Test
  fun movieResponseBecomesTheSkipMarkersThePlayerShows() {
    val segments =
      runBlocking {
        repository(200, MOVIE_MEDIA).getTheIntroDbSegments(tmdbId = 550, mediaType = "movie")
      }.getOrThrow()

    val markers =
      segments.mapNotNull {
        SkipMarkerResolver.resolveProviderSegment(
          segmentType = it.segmentType,
          startSeconds = it.startSecondsOrNull,
          endSeconds = it.endSecondsOrNull,
          durationSeconds = 6600.0,
          source = "theintrodb",
        )
      }

    assertEquals(
      listOf(
        SkipSegmentType.INTRO,
        SkipSegmentType.RECAP,
        SkipSegmentType.CREDITS,
        SkipSegmentType.CREDITS,
        SkipSegmentType.PREVIEW,
      ),
      markers.map { it.type },
    )
    assertEquals(0.0, markers[0].startSeconds, 1e-9)
    assertEquals(23.0, markers[0].endSeconds, 1e-9)
    // Open-ended credits run to the end of the file.
    assertEquals(6408.0, markers[3].startSeconds, 1e-9)
    assertEquals(6600.0, markers[3].endSeconds, 1e-9)
  }

  @Test
  fun episodeLookupUsesV4AndReadsDetailedSegments() {
    val segments =
      runBlocking {
        repository(200, EPISODE_MEDIA_DETAILS)
          .getTheIntroDbSegments(tmdbId = 1399, mediaType = "tv", season = 1, episode = 1)
      }.getOrThrow()

    val url = requests.single()
    assertEquals("api.theintrodb.org", url.host)
    assertEquals("/v4/media", url.encodedPath)
    assertEquals("1399", url.queryParameter("tmdb_id"))
    assertEquals("1", url.queryParameter("season"))
    assertEquals("1", url.queryParameter("episode"))
    assertEquals("application/json", acceptHeaders.single())

    // confidence, submission_count and group_duration_ms (details=true) don't become extra segments.
    assertEquals(1, segments.size)
    assertEquals("intro", segments.single().segmentType)
    assertEquals(23.0, segments.single().endSecondsOrNull!!, 1e-9)
  }

  @Test
  fun imdbLookupIsSentWhenThereIsNoTmdbId() {
    runBlocking {
      repository(200, MOVIE_MEDIA).getTheIntroDbSegments(imdbId = "tt0137523", mediaType = "movie")
    }.getOrThrow()

    val url = requests.single()
    assertEquals("tt0137523", url.queryParameter("imdb_id"))
    assertNull(url.queryParameter("tmdb_id"))
    assertNull(url.queryParameter("season"))
  }

  @Test
  fun episodeListFallbackProducesNoMarkers() {
    // Returned with 200 when the media has no submissions; its per-episode counts are not timestamps.
    val segments =
      runBlocking {
        repository(200, EPISODE_LIST)
          .getTheIntroDbSegments(tmdbId = 1399, mediaType = "tv", season = 1, episode = 1)
      }.getOrThrow()

    assertTrue(segments.isEmpty())
  }

  @Test
  fun notFoundMeansNoMarkersWithoutFallingBackToRetiredVersions() {
    val result =
      runBlocking {
        repository(404, """{"error":"No submissions","code":"no_submissions"}""")
          .getTheIntroDbSegments(tmdbId = 1399, mediaType = "tv", season = 1, episode = 1)
      }

    assertTrue(result.getOrThrow().isEmpty())
    assertEquals(listOf("/v4/media"), requests.map { it.encodedPath })
  }

  @Test
  fun serverErrorsAreReportedAsFailuresNotAsMissingMarkers() {
    val result =
      runBlocking {
        repository(503, """{"error":"unavailable"}""").getTheIntroDbSegments(tmdbId = 550, mediaType = "movie")
      }

    assertTrue(result.isFailure)
  }

  private companion object {
    const val MOVIE_MEDIA = """
      {
        "tvdb_id": 169,
        "type": "movie",
        "intro": [{ "start_ms": null, "end_ms": 23000 }],
        "recap": [{ "start_ms": 25000, "end_ms": 134000 }],
        "credits": [
          { "start_ms": 5801777, "end_ms": 6371111 },
          { "start_ms": 6408000, "end_ms": null }
        ],
        "preview": [{ "start_ms": 1680000, "end_ms": 1740000 }]
      }
    """

    const val EPISODE_MEDIA_DETAILS = """
      {
        "tvdb_id": 71663,
        "type": "tv",
        "season": 1,
        "episode": 1,
        "group_duration_ms": 1400000,
        "intro": [{ "start_ms": null, "end_ms": 23000, "confidence": 0.87, "submission_count": 12 }]
      }
    """

    const val EPISODE_LIST = """
      {
        "tvdb_id": 71663,
        "type": "tv",
        "episodes": [
          {
            "season": 1, "episode": 1,
            "total_submission_count": 4, "total_accepted_count": 4, "total_pending_count": 0,
            "total_rejected_count": 0, "flag_votes": 0,
            "intro_accepted_count": 4, "intro_pending_count": 0, "intro_rejected_count": 0,
            "recap_accepted_count": 0, "recap_pending_count": 0, "recap_rejected_count": 0,
            "credits_accepted_count": 0, "credits_pending_count": 0, "credits_rejected_count": 0,
            "preview_accepted_count": 0, "preview_pending_count": 0, "preview_rejected_count": 0
          }
        ]
      }
    """
  }
}
