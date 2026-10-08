package com.quantummpv.app.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import com.quantummpv.app.ui.theme.DesignTokens.GlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.GlassConfigProvider
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.MotionStyle
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.theme.DesignTokens.calculateSurfaceOpacity
import com.quantummpv.app.ui.theme.DesignTokens.glassSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DesignTokensCompositionTest {
  /** Verifies the default configuration and restoration of outer values after nested providers exit. */
  @Test
  fun compositionDefaultsToClassicAndProvidersAreScoped() {
    val outer = GlassConfig(style = SurfaceStyle.SOFT_GLASS, intensity = 0.8f)
    val inner = GlassConfig(style = SurfaceStyle.CLEAR_GLASS, intensity = 0.4f)
    val observed = mutableListOf<GlassConfig>()

    compose {
      observed += LocalGlassConfig.current
      GlassConfigProvider(outer) {
        observed += LocalGlassConfig.current
        GlassConfigProvider(inner) { observed += LocalGlassConfig.current }
        observed += LocalGlassConfig.current
      }
      observed += LocalGlassConfig.current
    }

    assertEquals(listOf(GlassConfig(), outer, inner, outer, GlassConfig()), observed)
  }

  /** Checks that Classic and Minimal use their base opacity rules regardless of glass or video settings. */
  @Test
  fun classicAndMinimalIgnoreGlassIntensityVideoAndModeAdjustments() {
    for ((style, expected) in listOf(SurfaceStyle.CLASSIC to 0.8f, SurfaceStyle.MINIMAL to 0.76f)) {
      val config = GlassConfig(
        style = style,
        intensity = 0f,
        cinemaMode = true,
        performanceMode = PerformanceMode.BATTERY_SAVER,
      )
      for (luminance in listOf(0f, 0.5f, 1f)) {
        assertOpacity(expected, config, baseOpacity = 0.8f, isOnVideo = true, videoLuminance = luminance)
      }
      assertOpacity(0f, config, baseOpacity = 0f, isOnVideo = true)
    }
  }

  /** Checks intensity scaling for every glass style, including transparent and opaque endpoints. */
  @Test
  fun everyGlassStyleMultipliesBaseOpacityByTheConfiguredIntensity() {
    val glassStyles = SurfaceStyle.entries.filter { it != SurfaceStyle.CLASSIC && it != SurfaceStyle.MINIMAL }
    for (style in glassStyles) {
      assertOpacity(0.3f, GlassConfig(style = style, intensity = 0.6f), baseOpacity = 0.5f)
      assertOpacity(0f, GlassConfig(style = style, intensity = 0f))
      assertOpacity(1f, GlassConfig(style = style, intensity = 1f))
    }
  }

  /** Checks luminance adjustments across dark and bright video scenes and their absence off video. */
  @Test
  fun videoContrastHandlesDarkMidpointAndBrightScenes() {
    val config = GlassConfig(style = SurfaceStyle.SOFT_GLASS, intensity = 0.6f)
    val samples = listOf(0f to 0.55f, 0.25f to 0.575f, 0.5f to 0.6f, 0.75f to 0.675f, 1f to 0.75f)

    for ((luminance, expected) in samples) {
      assertOpacity(expected, config, isOnVideo = true, videoLuminance = luminance)
      assertOpacity(0.6f, config, isOnVideo = false, videoLuminance = luminance)
    }
  }

  /** Verifies that video surfaces clamp opacity to the readability floor and the opaque ceiling. */
  @Test
  fun videoOpacityHasAReadabilityFloorAndAnOpaqueCeiling() {
    val transparent = GlassConfig(style = SurfaceStyle.CLEAR_GLASS, intensity = 0f)
    for (luminance in listOf(0f, 0.5f, 1f)) {
      assertOpacity(0.3f, transparent, isOnVideo = true, videoLuminance = luminance)
    }
    assertOpacity(1f, transparent.copy(intensity = 0.95f), isOnVideo = true, videoLuminance = 1f)
    assertOpacity(0.3f, transparent.copy(intensity = 0.35f), isOnVideo = true, videoLuminance = 0f)
    assertOpacity(0.3f, transparent.copy(intensity = 1f), baseOpacity = 0f, isOnVideo = true)
  }

  /** Checks separate and combined opacity increases for cinema and battery saver, capped at one. */
  @Test
  fun cinemaAndBatterySaverAddIndependentlyAndSaturateAtOne() {
    val config = GlassConfig(style = SurfaceStyle.FROSTED_GLASS, intensity = 0.5f)

    assertOpacity(0.6f, config.copy(cinemaMode = true))
    assertOpacity(0.65f, config.copy(performanceMode = PerformanceMode.BATTERY_SAVER))
    assertOpacity(0.75f, config.copy(cinemaMode = true, performanceMode = PerformanceMode.BATTERY_SAVER))
    assertOpacity(1f, config.copy(intensity = 0.95f, cinemaMode = true))
    assertOpacity(1f, config.copy(intensity = 0.95f, performanceMode = PerformanceMode.BATTERY_SAVER))
    assertOpacity(1f, config.copy(intensity = 0.8f, cinemaMode = true, performanceMode = PerformanceMode.BATTERY_SAVER))
    for (mode in listOf(PerformanceMode.BALANCED, PerformanceMode.QUALITY, PerformanceMode.AUTOMATIC)) {
      assertOpacity(0.5f, config.copy(performanceMode = mode))
    }
  }

  /** Verifies that cinema and battery saver increase opacity after the video readability floor is applied. */
  @Test
  fun videoFloorIsAppliedBeforeCinemaAndBatteryIncreases() {
    val config = GlassConfig(
      style = SurfaceStyle.CLEAR_GLASS,
      intensity = 0f,
      cinemaMode = true,
      performanceMode = PerformanceMode.BATTERY_SAVER,
    )

    assertOpacity(0.55f, config, isOnVideo = true, videoLuminance = 0f)
    assertOpacity(0.25f, config, isOnVideo = false, videoLuminance = 0f)
  }

  /** Checks that the Cinema style preset and the cinema mode opacity increase remain independent. */
  @Test
  fun cinemaStyleDoesNotImplicitlyEnableTheSeparateCinemaMode() {
    val config = GlassConfig().copyWithStyle(SurfaceStyle.CINEMA)

    assertOpacity(0.9f, config)
    assertOpacity(1f, config.copy(cinemaMode = true))
  }

  /** Verifies that Classic and Minimal return the original modifier even with video opacity overrides. */
  @Test
  fun classicAndMinimalGlassModifierPreserveTheIncomingModifier() {
    val original = Modifier.then(object : Modifier.Element {})
    for (style in listOf(SurfaceStyle.CLASSIC, SurfaceStyle.MINIMAL)) {
      compose {
        GlassConfigProvider(GlassConfig(style = style)) {
          assertSame(original, original.glassSurface(isOnVideo = true, customOpacity = 0.1f))
        }
      }
    }
  }

  /** Checks AppMotion accessors and animation selection for every system, motion, and performance combination. */
  @Test
  fun appMotionSelectsSpecsAndExposesTheProvidedPolicy() {
    val normal = tween<Float>(durationMillis = 300)
    val reduced = tween<Float>(durationMillis = 0)
    for (systemReduction in listOf(false, true)) {
      for (style in MotionStyle.entries) {
        for (mode in PerformanceMode.entries) {
          val policy = MotionPolicy(systemReduction, style, mode)
          compose {
            CompositionLocalProvider(LocalMotionPolicy provides policy) {
              assertSame(policy, AppMotion.policy())
              assertSame(if (policy.shouldReduceAnimations) reduced else normal, AppMotion.spatial(normal, reduced))
              assertEquals(policy.shouldReduceAnimations, AppMotion.shouldReduceMotion())
              assertEquals(policy.shouldDisableAnimations, AppMotion.shouldDisableAnimations())
              assertEquals(policy.intensityMultiplier, AppMotion.intensityMultiplier(), 0f)
              assertEquals(policy.shouldEnableDynamicEffects, AppMotion.shouldEnableDynamicEffects())
            }
          }
        }
      }
    }
  }

  /** Evaluates opacity under [config] in a composition and compares it with [expected] using float tolerance. */
  private fun assertOpacity(
    expected: Float,
    config: GlassConfig,
    baseOpacity: Float = 1f,
    isOnVideo: Boolean = false,
    videoLuminance: Float = 0.5f,
  ) {
    var actual = Float.NaN
    compose {
      GlassConfigProvider(config) {
        actual = calculateSurfaceOpacity(baseOpacity, isOnVideo, videoLuminance)
      }
    }
    assertEquals("$config, base=$baseOpacity, onVideo=$isOnVideo, luminance=$videoLuminance", expected, actual, 0.00001f)
  }

  /**
   * Runs [content] synchronously for composition-local assertions without a UI tree or frame clock.
   * Rejects UI node operations and disposes the fixture even if an assertion fails.
   */
  private fun compose(content: @Composable () -> Unit) {
    val applier = object : AbstractApplier<Unit>(Unit) {
      override fun insertTopDown(index: Int, instance: Unit) = error("Unexpected UI node")
      override fun insertBottomUp(index: Int, instance: Unit) = error("Unexpected UI node")
      override fun remove(index: Int, count: Int) = error("Unexpected UI node")
      override fun move(from: Int, to: Int, count: Int) = error("Unexpected UI node")
      override fun onClear() = Unit
    }
    val recomposer = Recomposer(EmptyCoroutineContext)
    val composition = Composition(applier, recomposer)
    try {
      composition.setContent(content)
    } finally {
      composition.dispose()
      recomposer.cancel()
    }
  }
}
