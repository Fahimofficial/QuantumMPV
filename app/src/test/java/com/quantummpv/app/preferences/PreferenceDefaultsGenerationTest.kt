package com.quantummpv.app.preferences

import android.content.Context
import android.content.SharedPreferences
import com.quantummpv.app.preferences.preference.AndroidPreferenceStore
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
@Config(manifest = Config.NONE, sdk = [34])
class PreferenceDefaultsGenerationTest {
  private lateinit var context: Context
  private lateinit var prefs: SharedPreferences

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    prefs = context.getSharedPreferences("defaults_generation_test", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
  }

  private fun appearance() = AppearancePreferences(AndroidPreferenceStore(context, prefs))

  private fun player() = PlayerPreferences(AndroidPreferenceStore(context, prefs))

  @Test
  fun freshInstallGetsTheRecommendedLayout() {
    PreferenceDefaultsGeneration.apply(prefs, hasOtherAppData = false)

    val appearance = appearance()
    assertEquals(AppearancePreferences.DEFAULT_TOP_RIGHT_CONTROLS, appearance.topRightControls.get())
    assertEquals(AppearancePreferences.DEFAULT_PORTRAIT_BOTTOM_CONTROLS, appearance.portraitBottomControls.get())
    assertFalse(player().showControlsDrawer.get())
    assertEquals(PreferenceDefaultsGeneration.CURRENT, prefs.getInt(PreferenceDefaultsGeneration.KEY, 0))
  }

  @Test
  fun existingInstallKeepsTheDefaultsItStartedWith() {
    // Used before, never touched the layout: only an unrelated setting is stored.
    prefs.edit().putBoolean("amoled_mode", true).commit()

    PreferenceDefaultsGeneration.apply(prefs, hasOtherAppData = true)

    val appearance = appearance()
    assertEquals(
      PreferenceDefaultsGeneration.legacyDefault("top_right_controls"),
      appearance.topRightControls.get(),
    )
    assertEquals(
      PreferenceDefaultsGeneration.legacyDefault("portrait_bottom_controls"),
      appearance.portraitBottomControls.get(),
    )
    assertTrue(player().showControlsDrawer.get())
  }

  @Test
  fun savedLayoutsAreNeverRewritten() {
    prefs
      .edit()
      .putString("top_right_controls", "SUBTITLES")
      .putString("portrait_bottom_controls", "AUDIO_TRACK,MORE_OPTIONS")
      .putBoolean("cast_button_migration_complete", true)
      .commit()

    PreferenceDefaultsGeneration.apply(prefs, hasOtherAppData = true)
    val appearance = appearance()

    assertEquals("SUBTITLES", appearance.topRightControls.get())
    assertEquals("AUDIO_TRACK,MORE_OPTIONS", appearance.portraitBottomControls.get())
    // Cast and Clip are no longer forced into saved layouts.
    assertFalse("CAST" in appearance.topRightControls.get())
    assertFalse("CLIP" in appearance.portraitBottomControls.get())
    assertFalse(prefs.contains("cast_button_migration_complete"))
  }

  @Test
  fun emptyPreferencesWithExistingDatabaseCountAsExistingInstall() {
    PreferenceDefaultsGeneration.apply(prefs, hasOtherAppData = true)

    assertEquals(PreferenceDefaultsGeneration.legacyDefault("show_controls_drawer"), player().showControlsDrawer.get())
  }

  @Test
  fun runningAgainChangesNothing() {
    PreferenceDefaultsGeneration.apply(prefs, hasOtherAppData = false)
    appearance().topRightControls.set("AUDIO_TRACK")

    val from = PreferenceDefaultsGeneration.apply(prefs, hasOtherAppData = true)

    assertEquals(PreferenceDefaultsGeneration.CURRENT, from)
    assertEquals("AUDIO_TRACK", appearance().topRightControls.get())
    assertFalse(player().showControlsDrawer.get())
  }

  @Test
  fun resetToRecommendedReplacesEveryRegion() {
    prefs.edit().putBoolean("amoled_mode", true).commit()
    PreferenceDefaultsGeneration.apply(prefs, hasOtherAppData = true)
    val appearance = appearance()

    appearance.resetPlayerLayoutToRecommended()

    assertEquals(AppearancePreferences.DEFAULT_TOP_RIGHT_CONTROLS, appearance.topRightControls.get())
    assertEquals(AppearancePreferences.DEFAULT_BOTTOM_RIGHT_CONTROLS, appearance.bottomRightControls.get())
    assertEquals(AppearancePreferences.DEFAULT_BOTTOM_LEFT_CONTROLS, appearance.bottomLeftControls.get())
    assertEquals(AppearancePreferences.DEFAULT_PORTRAIT_BOTTOM_CONTROLS, appearance.portraitBottomControls.get())
  }

  @Test
  fun generationMarkerIsNotPartOfSettingsBackups() {
    assertTrue(SettingsBackupPolicy.isProtectedKey(PreferenceDefaultsGeneration.KEY))
  }
}
