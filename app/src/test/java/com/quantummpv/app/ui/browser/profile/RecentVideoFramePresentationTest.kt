package com.quantummpv.app.ui.browser.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentVideoFramePresentationTest {
  @Test
  fun missingThumbnailUsesMediaSpecificFallback() {
    assertEquals(
      RecentMediaFallback.AUDIO,
      recentVideoFramePresentation(hasThumbnail = false, isAudio = true, duration = "", progress = null).fallback,
    )
    assertEquals(
      RecentMediaFallback.VIDEO,
      recentVideoFramePresentation(hasThumbnail = false, isAudio = false, duration = "", progress = null).fallback,
    )
  }

  @Test
  fun thumbnailSuppressesFallbackAndInvalidDurationsAreHidden() {
    val withThumbnail = recentVideoFramePresentation(hasThumbnail = true, isAudio = false, duration = "01:30", progress = null)
    val noDuration = recentVideoFramePresentation(hasThumbnail = false, isAudio = false, duration = "--:--", progress = null)
    val blankDuration = recentVideoFramePresentation(hasThumbnail = false, isAudio = true, duration = " ", progress = null)

    assertNull(withThumbnail.fallback)
    assertEquals("01:30", withThumbnail.durationLabel)
    assertNull(noDuration.durationLabel)
    assertNull(blankDuration.durationLabel)
  }

  @Test
  fun zeroProgressIsHiddenAndInProgressOrWatchedValuesAreKept() {
    val zero = recentVideoFramePresentation(hasThumbnail = true, isAudio = false, duration = "", progress = 0f)
    val inProgress = recentVideoFramePresentation(hasThumbnail = true, isAudio = false, duration = "", progress = 0.45f)
    val watched = recentVideoFramePresentation(hasThumbnail = true, isAudio = false, duration = "", progress = 1f)

    assertNull(zero.progress)
    assertEquals(0.45f, inProgress.progress!!, 0.001f)
    assertEquals(1f, watched.progress!!, 0.001f)
  }
}
