package com.quantummpv.app.ui.theme

import com.quantummpv.app.ui.theme.DesignTokens.BlurLevel
import com.quantummpv.app.ui.theme.DesignTokens.DynamicTintMode
import com.quantummpv.app.ui.theme.DesignTokens.EdgeHighlightMode
import com.quantummpv.app.ui.theme.DesignTokens.GlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.MotionStyle
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import com.quantummpv.app.ui.theme.DesignTokens.RefractionMode
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassConfigTest {
  /** Verifies that a default configuration selects Classic with optional glass effects disabled. */
  @Test
  fun defaultsKeepGlassEffectsOptIn() {
    val config = GlassConfig()

    assertEquals(SurfaceStyle.CLASSIC, config.style)
    assertEquals(1f, config.intensity, 0f)
    assertEquals(BlurLevel.OFF, config.blurLevel)
    assertEquals(DynamicTintMode.OFF, config.dynamicTint)
    assertEquals(EdgeHighlightMode.OFF, config.edgeHighlight)
    assertEquals(RefractionMode.OFF, config.refraction)
    assertEquals(MotionStyle.STANDARD, config.motionStyle)
    assertEquals(PerformanceMode.AUTOMATIC, config.performanceMode)
    assertEquals(false, config.cinemaMode)
  }

  /** Checks every style preset while ensuring unrelated settings and the source configuration are preserved. */
  @Test
  fun changingStyleResetsIntensityAndBlurButPreservesOtherChoices() {
    val original = GlassConfig(
      style = SurfaceStyle.LIQUID_GLASS,
      intensity = 0.23f,
      blurLevel = BlurLevel.OFF,
      dynamicTint = DynamicTintMode.MEDIA,
      edgeHighlight = EdgeHighlightMode.STRONG,
      refraction = RefractionMode.EXPERIMENTAL,
      motionStyle = MotionStyle.REDUCED,
      performanceMode = PerformanceMode.BATTERY_SAVER,
      cinemaMode = true,
    )
    val before = original.copy()
    val presets = listOf(
      Triple(SurfaceStyle.CLASSIC, 1f, BlurLevel.OFF),
      Triple(SurfaceStyle.FROSTED_GLASS, 0.75f, BlurLevel.MEDIUM),
      Triple(SurfaceStyle.CLEAR_GLASS, 0.6f, BlurLevel.LOW),
      Triple(SurfaceStyle.LIQUID_GLASS, 0.7f, BlurLevel.HIGH),
    )

    for ((style, intensity, blur) in presets) {
      assertEquals(
        "Switching to $style must also preserve tint, highlight, refraction, motion and performance",
        original.copy(style = style, intensity = intensity, blurLevel = blur),
        original.copyWithStyle(style),
      )
    }
    assertEquals("Changing a style must not mutate the source config", before, original)
  }

  @Test
  fun removedPresetsMapToTheClosestRetainedStyle() {
    assertEquals(
      listOf(SurfaceStyle.CLASSIC, SurfaceStyle.FROSTED_GLASS, SurfaceStyle.CLEAR_GLASS, SurfaceStyle.LIQUID_GLASS),
      SurfaceStyle.selectableStyles,
    )
    assertEquals(SurfaceStyle.FROSTED_GLASS, SurfaceStyle.SOFT_GLASS.canonicalStyle)
    assertEquals(SurfaceStyle.FROSTED_GLASS, SurfaceStyle.AMOLED_GLASS.canonicalStyle)
    assertEquals(SurfaceStyle.FROSTED_GLASS, SurfaceStyle.CINEMA.canonicalStyle)
    assertEquals(SurfaceStyle.CLASSIC, SurfaceStyle.MINIMAL.canonicalStyle)
  }

  /** Verifies that selecting the current style resets customized intensity and blur to its defaults. */
  @Test
  fun reselectingCurrentStyleRestoresItsPreset() {
    val customized = GlassConfig(style = SurfaceStyle.LIQUID_GLASS, intensity = 0f, blurLevel = BlurLevel.OFF)

    assertEquals(
      customized.copy(intensity = 0.7f, blurLevel = BlurLevel.HIGH),
      customized.copyWithStyle(SurfaceStyle.LIQUID_GLASS),
    )
  }

  /** Checks the exact style sets advertising tint, edge highlight, and refraction support. */
  @Test
  fun onlyGlassStylesSupportTintAndHighlightsAndOnlyLiquidSupportsRefraction() {
    val glassStyles = setOf(
      SurfaceStyle.SOFT_GLASS,
      SurfaceStyle.FROSTED_GLASS,
      SurfaceStyle.CLEAR_GLASS,
      SurfaceStyle.LIQUID_GLASS,
      SurfaceStyle.AMOLED_GLASS,
    )
    for (style in SurfaceStyle.entries) {
      assertEquals("$style tint", style in glassStyles, style.supportsDynamicTint)
      assertEquals("$style highlight", style in glassStyles, style.supportsEdgeHighlight)
      assertEquals("$style refraction", style == SurfaceStyle.LIQUID_GLASS, style.supportsRefraction)
    }
  }

  /** Checks blur limits, dynamic effects, animation simplification, and blur radii for the declared presets. */
  @Test
  fun performancePresetsLimitBlurAndBatteryCost() {
    val limits = mapOf(
      PerformanceMode.BATTERY_SAVER to BlurLevel.LOW,
      PerformanceMode.BALANCED to BlurLevel.MEDIUM,
      PerformanceMode.QUALITY to BlurLevel.HIGH,
      PerformanceMode.AUTOMATIC to BlurLevel.HIGH,
    )
    for ((mode, limit) in limits) {
      assertEquals("$mode blur limit", limit, mode.maxBlurLevel)
      assertEquals("$mode dynamic effects", mode != PerformanceMode.BATTERY_SAVER, mode.enableDynamicEffects)
      assertEquals("$mode animation simplification", mode == PerformanceMode.BATTERY_SAVER, mode.simplifyAnimations)
    }
    assertEquals(listOf(0, 8, 16, 24), BlurLevel.entries.map { it.renderEffectRadius })
  }
}
