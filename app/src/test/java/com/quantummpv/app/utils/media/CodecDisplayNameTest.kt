package com.quantummpv.app.utils.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecDisplayNameTest {
  @Test
  fun mapsCommonVideoMimeTypes() {
    assertEquals("HEVC", codecDisplayName("video/hevc"))
    assertEquals("H.264", codecDisplayName("video/avc"))
    assertEquals("VP9", codecDisplayName("video/x-vnd.on2.vp9"))
    assertEquals("AV1", codecDisplayName("video/av01"))
  }

  @Test
  fun fallsBackToTheMimeSubtype() = assertEquals("THEORA", codecDisplayName("video/theora"))

  @Test
  fun ignoresNonVideoTracks() {
    assertNull(codecDisplayName("audio/mp4a-latm"))
    assertNull(codecDisplayName(null))
  }
}
