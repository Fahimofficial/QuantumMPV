package com.quantummpv.app.ui.player.controls

import com.quantummpv.app.ui.player.SingleActionGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapClassifierTest {
  private fun waits(
    region: TapRegion,
    left: SingleActionGesture = SingleActionGesture.Seek,
    center: SingleActionGesture = SingleActionGesture.PlayPause,
    right: SingleActionGesture = SingleActionGesture.Seek,
    locked: Boolean = false,
  ) = TapClassifier.waitsForDoubleTap(region, locked, left, center, right)

  @Test
  fun defaultsWaitOnlyWhereDoubleTapDoesSomething() {
    assertTrue(waits(TapRegion.LEFT))
    assertTrue(waits(TapRegion.CENTER))
    assertTrue(waits(TapRegion.RIGHT))
  }

  @Test
  fun regionsWithoutDoubleTapActActImmediately() {
    assertFalse(waits(TapRegion.CENTER, center = SingleActionGesture.None))
    assertFalse(waits(TapRegion.LEFT, left = SingleActionGesture.None))
    assertTrue(waits(TapRegion.RIGHT, left = SingleActionGesture.None))
    // Seek is not a centre action, so the centre never waits for it.
    assertFalse(waits(TapRegion.CENTER, center = SingleActionGesture.Seek))
  }

  @Test
  fun lockedControlsNeverWait() {
    assertFalse(waits(TapRegion.RIGHT, locked = true))
    assertFalse(waits(TapRegion.CENTER, locked = true))
  }

  @Test
  fun regionsFollowTheSeekAreaWidth() {
    assertEquals(TapRegion.LEFT, TapClassifier.region(x = 100f, width = 1000f, seekAreaPercent = 35))
    assertEquals(TapRegion.CENTER, TapClassifier.region(x = 500f, width = 1000f, seekAreaPercent = 35))
    assertEquals(TapRegion.RIGHT, TapClassifier.region(x = 700f, width = 1000f, seekAreaPercent = 35))
    assertEquals(TapRegion.CENTER, TapClassifier.region(x = 100f, width = 1000f, seekAreaPercent = 0))
  }

  @Test
  fun seekSensitivityMeansTheSamePhysicalDistanceOnEveryScreen() {
    // Unchanged on the reference density.
    assertEquals(0.05f, TapClassifier.seekSecondsPerPixel(0.05f, 2.75f), 1e-6f)
    // A denser screen has more pixels per centimetre, so each pixel is worth less time.
    val dense = TapClassifier.seekSecondsPerPixel(0.05f, 3.5f)
    val sparse = TapClassifier.seekSecondsPerPixel(0.05f, 2f)
    assertEquals(0.05f * 2.75f, dense * 3.5f, 1e-5f)
    assertEquals(dense * 3.5f, sparse * 2f, 1e-5f)
  }

  @Test
  fun edgeDeadZonesOnlyApplyWhereTheSystemClaimsTheEdge() {
    assertTrue(TapClassifier.isInSideEdgeDeadZone(x = 10f, width = 1000f, leftEdgePx = 66f, rightEdgePx = 66f))
    assertTrue(TapClassifier.isInSideEdgeDeadZone(x = 990f, width = 1000f, leftEdgePx = 66f, rightEdgePx = 66f))
    assertFalse(TapClassifier.isInSideEdgeDeadZone(x = 500f, width = 1000f, leftEdgePx = 66f, rightEdgePx = 66f))
    // Three-button navigation reports no gesture insets.
    assertFalse(TapClassifier.isInSideEdgeDeadZone(x = 1f, width = 1000f, leftEdgePx = 0f, rightEdgePx = 0f))
  }

  @Test
  fun swipeUpOnlyStartsInTheBottomBand() {
    assertTrue(TapClassifier.isInSwipeUpBand(y = 900f, height = 1000f))
    assertTrue(TapClassifier.isInSwipeUpBand(y = 600f, height = 1000f))
    assertFalse(TapClassifier.isInSwipeUpBand(y = 300f, height = 1000f))
  }

  @Test
  fun slopCheckUsesTheDistanceFromTheDownPosition() {
    assertTrue(TapClassifier.withinSlop(dx = 3f, dy = 4f, slopPx = 6f))
    assertFalse(TapClassifier.withinSlop(dx = 6f, dy = 8f, slopPx = 10f))
  }
}
