package com.quantummpv.app.domain.playbackengine

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackEngineRouterTest {
  @Test
  fun media3IsOptInForSupportedLocalVideo() {
    val uri = "file:///storage/emulated/0/Movies/sample.mp4"

    assertEquals(
      PlaybackEngineKind.MPV,
      PlaybackEngineRouter.select(uri),
    )
    assertEquals(
      PlaybackEngineKind.MEDIA3,
      PlaybackEngineRouter.select(uri, media3Enabled = true),
    )
  }

  @Test
  fun unsupportedFormatsRemainOnMpv() {
    val uri = "https://example.com/video.mkv"

    assertFalse(PlaybackEngineRouter.isSupportedByMedia3(uri))
    assertEquals(
      PlaybackEngineKind.MPV,
      PlaybackEngineRouter.select(uri, media3Enabled = true),
    )
  }

  @Test
  fun explicitVideoMimeTypeAllowsUnknownExtension() {
    val uri = "https://example.com/stream"

    assertTrue(PlaybackEngineRouter.isSupportedByMedia3(uri, "video/mp4"))
    assertEquals(
      PlaybackEngineKind.MEDIA3,
      PlaybackEngineRouter.select(uri, "video/mp4", media3Enabled = true),
    )
  }

  @Test
  fun nonVideoMimeTypeFallsBackToMpv() {
    val uri = "https://example.com/audio.mp4"

    assertEquals(
      PlaybackEngineKind.MPV,
      PlaybackEngineRouter.select(uri, "audio/mp4", media3Enabled = true),
    )
  }
}
