package com.quantummpv.app.ui.theme

import com.quantummpv.app.ui.theme.DesignTokens.MotionStyle
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {
  @Test
  fun defaultPolicyKeepsStandardMotionAndDynamicEffects() {
    val policy = MotionPolicy()

    assertFalse(policy.shouldReduceAnimations)
    assertFalse(policy.shouldDisableAnimations)
    assertEquals(0.75f, policy.intensityMultiplier, 0f)
    assertTrue(policy.shouldEnableDynamicEffects)
  }

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

  @Test
  fun reducedUserMotionStillAllowsDynamicEffectsWithoutSystemReduction() {
    val policy = MotionPolicy(motionStyle = MotionStyle.REDUCED, performanceMode = PerformanceMode.QUALITY)

    assertTrue(policy.shouldReduceAnimations)
    assertFalse(policy.shouldDisableAnimations)
    assertTrue(policy.shouldEnableDynamicEffects)
    assertFalse(policy.copy(reduceMotion = true).shouldEnableDynamicEffects)
  }
}
