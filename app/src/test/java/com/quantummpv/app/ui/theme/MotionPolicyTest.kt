package com.quantummpv.app.ui.theme

import com.quantummpv.app.ui.theme.DesignTokens.MotionStyle
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {
  /** Verifies standard animation intensity and enabled dynamic effects under the default motion policy. */
  @Test
  fun defaultPolicyKeepsStandardMotionAndDynamicEffects() {
    val policy = MotionPolicy()

    assertFalse(policy.shouldReduceAnimations)
    assertFalse(policy.shouldDisableAnimations)
    assertEquals(0.75f, policy.intensityMultiplier, 0f)
    assertTrue(policy.shouldEnableDynamicEffects)
  }

  /** Checks that system reduced motion suppresses dynamic effects and reduces animations for every preset. */
  @Test
  fun systemReductionOverridesEveryMotionStyleAndPerformanceMode() {
    for (style in MotionStyle.entries) {
      for (mode in PerformanceMode.entries) {
        val policy = MotionPolicy(reduceMotion = true, motionStyle = style, performanceMode = mode)

        assertTrue("$policy must respect system reduced motion", policy.shouldReduceAnimations)
        assertFalse("$policy must suppress dynamic effects", policy.shouldEnableDynamicEffects)
        assertEquals("Only Off disables animations entirely: $policy", style == MotionStyle.OFF, policy.shouldDisableAnimations)
      }
    }
  }

  /** Verifies each motion style's reduction, disabling, and intensity values across all performance modes. */
  @Test
  fun motionStylesSelectReductionDisablingAndIntensityIndependentlyOfPerformance() {
    val expectations = listOf(
      Triple(MotionStyle.FULL, false, 1f),
      Triple(MotionStyle.STANDARD, false, 0.75f),
      Triple(MotionStyle.REDUCED, true, 0.5f),
      Triple(MotionStyle.OFF, true, 0f),
    )
    for ((style, reduced, intensity) in expectations) {
      for (mode in PerformanceMode.entries) {
        val policy = MotionPolicy(motionStyle = style, performanceMode = mode)

        assertEquals("$policy reduction", reduced, policy.shouldReduceAnimations)
        assertEquals("$policy disabling", style == MotionStyle.OFF, policy.shouldDisableAnimations)
        assertEquals("$policy intensity", intensity, policy.intensityMultiplier, 0f)
        assertEquals("$style token intensity", intensity, style.intensity, 0f)
      }
    }
  }

  /** Checks that Off motion and battery saver each prevent dynamic effects across all preset combinations. */
  @Test
  fun dynamicEffectsRequireEnabledMotionAndAPerformanceModeThatAllowsThem() {
    for (style in MotionStyle.entries) {
      for (mode in PerformanceMode.entries) {
        val policy = MotionPolicy(motionStyle = style, performanceMode = mode)
        val expected = style != MotionStyle.OFF && mode != PerformanceMode.BATTERY_SAVER

        assertEquals("$policy dynamic effects", expected, policy.shouldEnableDynamicEffects)
      }
    }
  }

  /** Verifies that user Reduced motion permits dynamic effects until system reduced motion is enabled. */
  @Test
  fun reducedUserMotionStillAllowsDynamicEffectsWithoutSystemReduction() {
    val policy = MotionPolicy(motionStyle = MotionStyle.REDUCED, performanceMode = PerformanceMode.QUALITY)

    assertTrue(policy.shouldReduceAnimations)
    assertFalse(policy.shouldDisableAnimations)
    assertTrue(policy.shouldEnableDynamicEffects)
    assertFalse(policy.copy(reduceMotion = true).shouldEnableDynamicEffects)
  }
}
