package app.gyrolet.mpvrx.domain.playbackengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineRouterTest {
  @Test
  fun media3RequiresExplicitOptIn() {
    val request = PlaybackEngineRequest("https://example.com/video.mp4")

    assertEquals(PlaybackEngineKind.MPV, PlaybackEngineRouter.route(request))
  }

  @Test
  fun supportedVideoExtensionRoutesToMedia3WhenOptedIn() {
    val request = PlaybackEngineRequest(
      uri = "https://example.com/video.mp4",
      preferredEngine = PlaybackEngineKind.MEDIA3,
    )

    assertEquals(PlaybackEngineKind.MEDIA3, PlaybackEngineRouter.route(request))
  }

  @Test
  fun videoMimeTypeAllowsUnknownExtension() {
    val request = PlaybackEngineRequest(
      uri = "content://media/external/video/42",
      mimeType = "video/x-matroska",
      preferredEngine = PlaybackEngineKind.MEDIA3,
    )

    assertTrue(PlaybackEngineRouter.isMedia3Supported(request))
    assertEquals(PlaybackEngineKind.MEDIA3, PlaybackEngineRouter.route(request))
  }

  @Test
  fun unsupportedSchemeAndFormatStayOnMpv() {
    val request = PlaybackEngineRequest(
      uri = "rtsp://example.com/live.mkv",
      preferredEngine = PlaybackEngineKind.MEDIA3,
    )

    assertFalse(PlaybackEngineRouter.isMedia3Supported(request))
    assertEquals(PlaybackEngineKind.MPV, PlaybackEngineRouter.route(request))
  }
}
