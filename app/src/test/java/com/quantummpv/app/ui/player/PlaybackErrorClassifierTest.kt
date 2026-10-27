package com.quantummpv.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorClassifierTest {
  @Test
  fun missingLocalFileWinsOverWhateverMpvReported() {
    val ui = PlaybackErrorClassifier.classify("loading failed", "/storage/emulated/0/gone.mkv", localFileMissing = true)
    assertEquals(PlaybackErrorKind.FILE_UNAVAILABLE, ui.kind)
    assertFalse("nothing to hand to another app", ui.canOpenExternally)
  }

  @Test
  fun streamFailuresAreNetworkErrors() {
    assertEquals(PlaybackErrorKind.NETWORK, PlaybackErrorClassifier.classify("loading failed", "https://cdn.example/a.m3u8").kind)
    assertEquals(PlaybackErrorKind.NETWORK, PlaybackErrorClassifier.classify("-13", "rtsp://cam/stream").kind)
    assertEquals(PlaybackErrorKind.NETWORK, PlaybackErrorClassifier.classify("Timed out while opening media", "http://nas/a.mkv").kind)
    assertEquals(PlaybackErrorKind.TIMEOUT, PlaybackErrorClassifier.classify("Timed out while opening media", "/sdcard/a.mkv").kind)
  }

  @Test
  fun unsupportedFormatsOfferAnotherPlayerRatherThanARetry() {
    val ui = PlaybackErrorClassifier.classify("unrecognized file format", "content://media/external/video/media/1")
    assertEquals(PlaybackErrorKind.UNSUPPORTED_FORMAT, ui.kind)
    assertFalse(ui.canRetry)
    assertTrue(ui.canOpenExternally)
    assertEquals(PlaybackErrorKind.UNSUPPORTED_FORMAT, PlaybackErrorClassifier.classify("-17", null).kind)
  }

  @Test
  fun decoderFailuresWithHardwareDecodingSuggestSoftwareDecoding() {
    val ui = PlaybackErrorClassifier.classify("video output initialization failed", "/sdcard/a.mkv", hardwareDecodingActive = true)
    assertEquals(PlaybackErrorKind.DECODER, ui.kind)
    assertTrue(ui.canTrySoftwareDecoding)

    val nothingPlayed = PlaybackErrorClassifier.classify("no audio or video data played", "/sdcard/a.mkv", hardwareDecodingActive = true)
    assertEquals(PlaybackErrorKind.DECODER, nothingPlayed.kind)
    assertFalse(PlaybackErrorClassifier.classify("video output initialization failed", "/sdcard/a.mkv").canTrySoftwareDecoding)
  }

  @Test
  fun unknownErrorsKeepMpvsWordingForDetails() {
    val ui = PlaybackErrorClassifier.classify("something odd", "/sdcard/a.mkv")
    assertEquals(PlaybackErrorKind.UNKNOWN, ui.kind)
    assertEquals("something odd", ui.detail)
    assertTrue(ui.canRetry)
  }
}
