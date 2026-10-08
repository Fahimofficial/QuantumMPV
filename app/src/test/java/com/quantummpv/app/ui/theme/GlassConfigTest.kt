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
      Triple(SurfaceStyle.SOFT_GLASS, 0.85f, BlurLevel.LOW),
      Triple(SurfaceStyle.FROSTED_GLASS, 0.75f, BlurLevel.MEDIUM),
      Triple(SurfaceStyle.CLEAR_GLASS, 0.6f, BlurLevel.LOW),
      Triple(SurfaceStyle.LIQUID_GLASS, 0.7f, BlurLevel.HIGH),
      Triple(SurfaceStyle.AMOLED_GLASS, 0.5f, BlurLevel.LOW),
      Triple(SurfaceStyle.CINEMA, 0.9f, BlurLevel.MEDIUM),
      Triple(SurfaceStyle.MINIMAL, 1f, BlurLevel.OFF),
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
  fun reselectingCurrentStyleRestoresItsPreset() {
    val customized = GlassConfig(style = SurfaceStyle.LIQUID_GLASS, intensity = 0f, blurLevel = BlurLevel.OFF)

    assertEquals(
      customized.copy(intensity = 0.7f, blurLevel = BlurLevel.HIGH),
      customized.copyWithStyle(SurfaceStyle.LIQUID_GLASS),
    )
  }

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
