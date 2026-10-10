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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

  /** Clears the shared preference fixture before constructing the settings used by each test. */
  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    storage = context.getSharedPreferences("appearance-glass-test", Context.MODE_PRIVATE)
    storage.edit().clear().commit()
    preferences = newPreferences()
  }

  /** Verifies that fresh installs show the requested Liquid Glass design by default. */
  @Test
  fun freshPreferencesEnableLiquidGlassWithVisibleOpticalDefaults() {
    assertEquals(defaultConfig, preferences.glassConfig)
  }

  @Test
  fun legacyNavigationChoiceAppliesUntilGlobalSurfaceStyleIsSaved() {
    storage.edit().putBoolean("glass_bottom_navigation", true).commit()
    val legacyPreferences = newPreferences()

    assertTrue(legacyPreferences.hasLegacyGlassBottomNavigation)
    assertFalse(legacyPreferences.hasStoredSurfaceStyle)
    assertEquals(true, legacyPreferences.legacyGlassBottomNavigation.get())

    legacyPreferences.surfaceStyle.set(SurfaceStyle.CLASSIC)
    assertTrue(legacyPreferences.hasStoredSurfaceStyle)
  }

  @Test
  fun redundantSavedSurfaceStylesMigrateToClosestSelectableStyle() {
    val legacyStyles = mapOf(
      "SOFT_GLASS" to SurfaceStyle.FROSTED_GLASS,
      "AMOLED_GLASS" to SurfaceStyle.FROSTED_GLASS,
      "CINEMA" to SurfaceStyle.FROSTED_GLASS,
      "MINIMAL" to SurfaceStyle.CLASSIC,
    )

    for ((storedStyle, expectedStyle) in legacyStyles) {
      storage.edit().putString("surface_style", storedStyle).commit()

      val migratedPreferences = newPreferences()

      assertEquals(expectedStyle, migratedPreferences.surfaceStyle.get())
      assertEquals(expectedStyle.name, storage.getString("surface_style", null))
    }

    storage.edit().putString("surface_style", "CINEMA").commit()
    val cinemaPreferences = newPreferences()
    assertEquals(DynamicTintMode.OFF, cinemaPreferences.dynamicTintMode.get())
    assertEquals(EdgeHighlightMode.OFF, cinemaPreferences.edgeHighlightMode.get())
  }

  @Test
  fun importedLegacySurfaceStyleIsCanonicalizedInRuntimeConfiguration() {
    storage.edit().putString("surface_style", "AMOLED_GLASS").commit()

    assertEquals(SurfaceStyle.FROSTED_GLASS, preferences.glassConfig.style)
  }

  @Test
  fun importedCinemaStyleKeepsItsPreviouslySuppressedEffectsOff() {
    storage.edit().putString("surface_style", "CINEMA").commit()

    assertEquals(SurfaceStyle.FROSTED_GLASS, preferences.canonicalizeSurfaceStyle())
    assertEquals(DynamicTintMode.OFF, preferences.dynamicTintMode.get())
    assertEquals(EdgeHighlightMode.OFF, preferences.edgeHighlightMode.get())
  }

  @Test
  fun selectingSurfaceStyleAppliesItsPresetDefaultsAndPreservesSeparateEffects() {
    preferences.glassIntensity.set(0.33f)
    preferences.glassBlurLevel.set(BlurLevel.HIGH)
    preferences.dynamicTintMode.set(DynamicTintMode.OFF)
    preferences.edgeHighlightMode.set(EdgeHighlightMode.STRONG)

    preferences.setSurfaceStyle(SurfaceStyle.CLEAR_GLASS)

    assertEquals(SurfaceStyle.CLEAR_GLASS, preferences.surfaceStyle.get())
    assertEquals(SurfaceStyle.CLEAR_GLASS.defaultIntensity, preferences.glassIntensity.get(), 0f)
    assertEquals(SurfaceStyle.CLEAR_GLASS.defaultBlurLevel, preferences.glassBlurLevel.get())
    assertEquals(DynamicTintMode.OFF, preferences.dynamicTintMode.get())
    assertEquals(EdgeHighlightMode.STRONG, preferences.edgeHighlightMode.get())
  }

  /** Verifies that raw persisted values populate all fields of a newly read glass configuration. */
  @Test
  fun persistedKeysPopulateEveryFieldOfTheGlassConfiguration() {
    storage.edit()
      .putString("surface_style", "LIQUID_GLASS")
      .putFloat("glass_intensity", 0.42f)
      .putString("glass_blur_level", "HIGH")
      .putString("dynamic_tint_mode", "THEME")
      .putString("edge_highlight_mode", "STRONG")
      .putString("refraction_mode", "EXPERIMENTAL")
      .putString("motion_style", "OFF")
      .putString("performance_mode", "BATTERY_SAVER")
      .putBoolean("cinema_mode", true)
      .commit()

    assertEquals(customConfig, newPreferences().glassConfig)
  }

  @Test
  fun unsupportedLegacyTintModesAreResetToOff() {
    for (mode in listOf("MEDIA", "ADAPTIVE")) {
      storage.edit().putString("dynamic_tint_mode", mode).commit()
      val migratedPreferences = newPreferences()

      assertEquals(DynamicTintMode.OFF, migratedPreferences.dynamicTintMode.get())
      assertEquals("OFF", storage.getString("dynamic_tint_mode", null))
      storage.edit().remove("dynamic_tint_mode").commit()
    }
  }

  @Test
  fun unsupportedTintImportedAfterInitializationNeverReachesGlassRendering() {
    storage.edit().putString("dynamic_tint_mode", "MEDIA").commit()
    assertEquals(DynamicTintMode.OFF, preferences.glassConfig.dynamicTint)

    storage.edit().putString("dynamic_tint_mode", "ADAPTIVE").commit()
    assertEquals(DynamicTintMode.OFF, preferences.glassConfig.dynamicTint)
  }

  /** Checks both reconstructed settings and raw storage keys after writing every glass preference. */
  @Test
  fun preferenceWritesSurviveReconstructionAndUseStableStorageKeys() {
    preferences.surfaceStyle.set(SurfaceStyle.LIQUID_GLASS)
    preferences.glassIntensity.set(0.42f)
    preferences.glassBlurLevel.set(BlurLevel.HIGH)
    preferences.dynamicTintMode.set(DynamicTintMode.THEME)
    preferences.edgeHighlightMode.set(EdgeHighlightMode.STRONG)
    preferences.refractionMode.set(RefractionMode.EXPERIMENTAL)
    preferences.motionStyle.set(MotionStyle.OFF)
    preferences.performanceMode.set(PerformanceMode.BATTERY_SAVER)
    preferences.cinemaMode.set(true)

    assertEquals(customConfig, newPreferences().glassConfig)
    assertEquals("LIQUID_GLASS", storage.getString("surface_style", null))
    assertEquals(0.42f, storage.getFloat("glass_intensity", -1f), 0f)
    assertEquals("HIGH", storage.getString("glass_blur_level", null))
    assertEquals("THEME", storage.getString("dynamic_tint_mode", null))
    assertEquals("STRONG", storage.getString("edge_highlight_mode", null))
    assertEquals("EXPERIMENTAL", storage.getString("refraction_mode", null))
    assertEquals("OFF", storage.getString("motion_style", null))
    assertEquals("BATTERY_SAVER", storage.getString("performance_mode", null))
    assertEquals(true, storage.getBoolean("cinema_mode", false))
  }

  /** Checks that each preference update produces a fresh configuration without changing earlier snapshots. */
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
    preferences.dynamicTintMode.set(DynamicTintMode.THEME)
    expected = expected.copy(dynamicTint = DynamicTintMode.THEME)
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

  /** Verifies fallback defaults for unknown, empty, and incorrectly cased stored enum names. */
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

  /** Checks that every selectable style persists while preserving separately stored intensity and blur. */
  @Test
  fun selectableSurfaceStylesRoundTripWithoutOverwritingExplicitIntensityAndBlur() {
    preferences.glassIntensity.set(0.37f)
    preferences.glassBlurLevel.set(BlurLevel.HIGH)
    for (style in SurfaceStyle.selectableStyles) {
      preferences.surfaceStyle.set(style)

      assertEquals(
        "Persisting $style must retain separately stored glass settings",
        defaultConfig.copy(style = style, intensity = 0.37f, blurLevel = BlurLevel.HIGH),
        newPreferences().glassConfig,
      )
    }
  }

  /** Verifies persistence of both intensity endpoints and the transition from enabled to disabled cinema mode. */
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

  /** Creates a new preference wrapper over the shared storage to exercise persistence across instances. */
  private fun newPreferences() = AppearancePreferences(AndroidPreferenceStore(context, storage))

  private val defaultConfig =
    GlassConfig(
      style = SurfaceStyle.LIQUID_GLASS,
      intensity = SurfaceStyle.LIQUID_GLASS.defaultIntensity,
      blurLevel = SurfaceStyle.LIQUID_GLASS.defaultBlurLevel,
      dynamicTint = DynamicTintMode.THEME,
      edgeHighlight = EdgeHighlightMode.SUBTLE,
      refraction = RefractionMode.SUBTLE,
    )
  private val customConfig = GlassConfig(
    style = SurfaceStyle.LIQUID_GLASS,
    intensity = 0.42f,
    blurLevel = BlurLevel.HIGH,
    dynamicTint = DynamicTintMode.THEME,
    edgeHighlight = EdgeHighlightMode.STRONG,
    refraction = RefractionMode.EXPERIMENTAL,
    motionStyle = MotionStyle.OFF,
    performanceMode = PerformanceMode.BATTERY_SAVER,
    cinemaMode = true,
  )
}
