package com.quantummpv.app.preferences

import android.content.Context
import android.content.SharedPreferences
import com.quantummpv.app.preferences.preference.AndroidPreferenceStore
import com.quantummpv.app.ui.theme.DesignTokens.BlurLevel
import com.quantummpv.app.ui.theme.DesignTokens.DynamicTintMode
import com.quantummpv.app.ui.theme.DesignTokens.EdgeHighlightMode
import com.quantummpv.app.ui.theme.DesignTokens.GlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.MotionStyle
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import com.quantummpv.app.ui.theme.DesignTokens.RefractionMode
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppearanceGlassPreferencesTest {
  private lateinit var context: Context
  private lateinit var storage: SharedPreferences
  private lateinit var preferences: AppearancePreferences

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    storage = context.getSharedPreferences("appearance-glass-test", Context.MODE_PRIVATE)
    storage.edit().clear().commit()
    preferences = newPreferences()
  }

  @Test
  fun freshPreferencesKeepClassicStyleWithDormantGlassDefaults() {
    assertEquals(defaultConfig, preferences.glassConfig)
  }

  @Test
  fun persistedKeysPopulateEveryFieldOfTheGlassConfiguration() {
    storage.edit()
      .putString("surface_style", "LIQUID_GLASS")
      .putFloat("glass_intensity", 0.42f)
      .putString("glass_blur_level", "HIGH")
      .putString("dynamic_tint_mode", "ADAPTIVE")
      .putString("edge_highlight_mode", "STRONG")
      .putString("refraction_mode", "EXPERIMENTAL")
      .putString("motion_style", "OFF")
      .putString("performance_mode", "BATTERY_SAVER")
      .putBoolean("cinema_mode", true)
      .commit()

    assertEquals(customConfig, newPreferences().glassConfig)
  }

  @Test
  fun preferenceWritesSurviveReconstructionAndUseStableStorageKeys() {
    preferences.surfaceStyle.set(SurfaceStyle.LIQUID_GLASS)
    preferences.glassIntensity.set(0.42f)
    preferences.glassBlurLevel.set(BlurLevel.HIGH)
    preferences.dynamicTintMode.set(DynamicTintMode.ADAPTIVE)
    preferences.edgeHighlightMode.set(EdgeHighlightMode.STRONG)
    preferences.refractionMode.set(RefractionMode.EXPERIMENTAL)
    preferences.motionStyle.set(MotionStyle.OFF)
    preferences.performanceMode.set(PerformanceMode.BATTERY_SAVER)
    preferences.cinemaMode.set(true)

    assertEquals(customConfig, newPreferences().glassConfig)
    assertEquals("LIQUID_GLASS", storage.getString("surface_style", null))
    assertEquals(0.42f, storage.getFloat("glass_intensity", -1f), 0f)
    assertEquals("HIGH", storage.getString("glass_blur_level", null))
    assertEquals("ADAPTIVE", storage.getString("dynamic_tint_mode", null))
    assertEquals("STRONG", storage.getString("edge_highlight_mode", null))
    assertEquals("EXPERIMENTAL", storage.getString("refraction_mode", null))
    assertEquals("OFF", storage.getString("motion_style", null))
    assertEquals("BATTERY_SAVER", storage.getString("performance_mode", null))
    assertEquals(true, storage.getBoolean("cinema_mode", false))
  }

  @Test
  fun computedConfigReadsEachUpdateWithoutMutatingEarlierSnapshots() {
    val initial = preferences.glassConfig
    var expected = defaultConfig

    preferences.surfaceStyle.set(SurfaceStyle.CLEAR_GLASS)
    expected = expected.copy(style = SurfaceStyle.CLEAR_GLASS)
    assertEquals(expected, preferences.glassConfig)
    preferences.glassIntensity.set(0f)
    expected = expected.copy(intensity = 0f)
    assertEquals(expected, preferences.glassConfig)
    preferences.glassBlurLevel.set(BlurLevel.OFF)
    expected = expected.copy(blurLevel = BlurLevel.OFF)
    assertEquals(expected, preferences.glassConfig)
    preferences.dynamicTintMode.set(DynamicTintMode.MEDIA)
    expected = expected.copy(dynamicTint = DynamicTintMode.MEDIA)
    assertEquals(expected, preferences.glassConfig)
    preferences.edgeHighlightMode.set(EdgeHighlightMode.SUBTLE)
    expected = expected.copy(edgeHighlight = EdgeHighlightMode.SUBTLE)
    assertEquals(expected, preferences.glassConfig)
    preferences.refractionMode.set(RefractionMode.SUBTLE)
    expected = expected.copy(refraction = RefractionMode.SUBTLE)
    assertEquals(expected, preferences.glassConfig)
    preferences.motionStyle.set(MotionStyle.REDUCED)
    expected = expected.copy(motionStyle = MotionStyle.REDUCED)
    assertEquals(expected, preferences.glassConfig)
    preferences.performanceMode.set(PerformanceMode.QUALITY)
    expected = expected.copy(performanceMode = PerformanceMode.QUALITY)
    assertEquals(expected, preferences.glassConfig)
    preferences.cinemaMode.set(true)
    expected = expected.copy(cinemaMode = true)
    assertEquals(expected, preferences.glassConfig)

    assertEquals(defaultConfig, initial)
  }

  @Test
  fun unknownAndEmptyEnumValuesFallBackToTheDeclaredDefaults() {
    val enumKeys = listOf(
      "surface_style", "glass_blur_level", "dynamic_tint_mode", "edge_highlight_mode",
      "refraction_mode", "motion_style", "performance_mode",
    )
    for (invalidValue in listOf("FUTURE_VALUE", "", "classic")) {
      val editor = storage.edit()
      for (key in enumKeys) editor.putString(key, invalidValue)
      editor.commit()

      assertEquals("Invalid stored enum: '$invalidValue'", defaultConfig, preferences.glassConfig)
    }
  }

  @Test
  fun allSurfaceStylesRoundTripWithoutOverwritingExplicitIntensityAndBlur() {
    preferences.glassIntensity.set(0.37f)
    preferences.glassBlurLevel.set(BlurLevel.HIGH)
    for (style in SurfaceStyle.entries) {
      preferences.surfaceStyle.set(style)

      assertEquals(
        "Persisting $style must retain separately stored glass settings",
        defaultConfig.copy(style = style, intensity = 0.37f, blurLevel = BlurLevel.HIGH),
        newPreferences().glassConfig,
      )
    }
  }

  @Test
  fun intensityEndpointsRemainDistinctAndCinemaCanBeTurnedOffAgain() {
    for (intensity in listOf(0f, 1f)) {
      preferences.glassIntensity.set(intensity)
      assertEquals(intensity, newPreferences().glassConfig.intensity, 0f)
    }
    preferences.cinemaMode.set(true)
    assertEquals(true, preferences.glassConfig.cinemaMode)
    preferences.cinemaMode.set(false)
    assertEquals(false, newPreferences().glassConfig.cinemaMode)
  }

  private fun newPreferences() = AppearancePreferences(AndroidPreferenceStore(context, storage))

  private val defaultConfig = GlassConfig(intensity = 0.85f, blurLevel = BlurLevel.LOW)
  private val customConfig = GlassConfig(
    style = SurfaceStyle.LIQUID_GLASS,
    intensity = 0.42f,
    blurLevel = BlurLevel.HIGH,
    dynamicTint = DynamicTintMode.ADAPTIVE,
    edgeHighlight = EdgeHighlightMode.STRONG,
    refraction = RefractionMode.EXPERIMENTAL,
    motionStyle = MotionStyle.OFF,
    performanceMode = PerformanceMode.BATTERY_SAVER,
    cinemaMode = true,
  )
}
