/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.domain.playback

import org.junit.Test
import kotlin.test.assertEquals

class PlaybackEngineRouterTest {
  private val router = PlaybackEngineRouter()

  @Test
  fun `auto routes HLS manifest to Media3`() {
    assertEquals(
      PlaybackEngineType.MEDIA3,
      router.preferredEngine("https://example.com/video/master.m3u8"),
    )
  }

  @Test
  fun `auto routes DASH manifest to Media3`() {
    assertEquals(
      PlaybackEngineType.MEDIA3,
      router.preferredEngine("https://example.com/video/manifest.mpd"),
    )
  }

  @Test
  fun `auto keeps ordinary local media on MPV`() {
    assertEquals(
      PlaybackEngineType.MPV,
      router.preferredEngine("content://media/external/video/123", "video/mp4"),
    )
  }

  @Test
  fun `explicit preference wins over auto`() {
    assertEquals(
      PlaybackEngineType.MPV,
      router.preferredEngine("https://example.com/video/master.m3u8", preference = PlaybackEngineRouter.Preference.MPV),
    )
    assertEquals(
      PlaybackEngineType.MEDIA3,
      router.preferredEngine("/storage/emulated/0/Movie.mkv", preference = PlaybackEngineRouter.Preference.MEDIA3),
    )
  }
}
