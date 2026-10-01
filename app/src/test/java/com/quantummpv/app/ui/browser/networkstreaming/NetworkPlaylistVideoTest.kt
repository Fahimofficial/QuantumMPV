/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.networkstreaming

import com.quantummpv.app.domain.network.NetworkFile
import com.quantummpv.app.domain.network.NetworkPlaybackUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkPlaylistVideoTest {
  @Test
  fun `network playlist entry persists only the saved connection id and normalized path`() {
    val file =
      NetworkFile(
        name = "clip+100%#?.mkv",
        path = "/Movies/Season 1/clip+100%#?.mkv",
        size = 1_024,
        isDirectory = false,
        mimeType = "video/x-matroska",
      )

    val video = file.toPlaylistVideo(connectionId = 7L)
    val reference = NetworkPlaybackUri.parse(video.path)

    assertEquals(video.path, video.uri.toString())
    assertEquals(7L, reference?.connectionId)
    assertEquals("/Movies/Season 1/clip+100%#?.mkv", reference?.path?.value)
    assertFalse(video.path.contains("password", ignoreCase = true))
    assertEquals("video/x-matroska", video.mimeType)
    assertTrue(video.id > 0L)
  }

  @Test
  fun `audio network playlist entries retain audio-only metadata`() {
    val file =
      NetworkFile(
        name = "track.flac",
        path = "/Music/track.flac",
        size = 4_096,
        isDirectory = false,
        mimeType = "audio/flac",
      )

    val video = file.toPlaylistVideo(connectionId = 23L)

    assertTrue(video.isAudio)
    assertEquals("audio/flac", video.mimeType)
    assertEquals(4_096L, video.size)
    assertEquals(23L, NetworkPlaybackUri.parse(video.path)?.connectionId)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `network playlist entry requires a saved connection`() {
    NetworkFile(
      name = "video.mp4",
      path = "/video.mp4",
      size = 0L,
      isDirectory = false,
    ).toPlaylistVideo(connectionId = 0L)
  }
}
