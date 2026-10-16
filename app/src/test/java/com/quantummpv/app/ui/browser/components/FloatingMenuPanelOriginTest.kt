package com.quantummpv.app.ui.browser.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMenuPanelOriginTest {
  @Test
  fun pivotSitsOnTheOverflowButtonEvenBelowAndBesideThePanel() {
    // Panel 600x300 px at (100, 900); button centre 50 px left of its end edge, 90 px below it.
    val origin = panelTransformOrigin(Offset(100f, 900f), 600f, 300f, Offset(650f, 1290f))

    assertEquals((650f - 100f) / 600f, origin.pivotFractionX, 1e-4f)
    assertEquals((1290f - 900f) / 300f, origin.pivotFractionY, 1e-4f)
    assertTrue("button is below the panel, so the pivot lies outside it", origin.pivotFractionY > 1f)
  }

  @Test
  fun clampedPanelStillPivotsOnTheButton() {
    // Narrow window: the panel is pushed right of the button's x, so the pivot fraction is negative-ish.
    val origin = panelTransformOrigin(Offset(300f, 500f), 400f, 200f, Offset(260f, 760f))
    assertEquals(-0.1f, origin.pivotFractionX, 1e-4f)
  }
}
