/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import org.koin.compose.koinInject

/**
 * Central design token system for QuantumMPV.
 * Provides reusable tokens for spacing, radius, typography, shadows, and glass effects.
 */
object DesignTokens {
    // ============================================================================
    // Spacing Tokens
    // ============================================================================

    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
        val xxxl = 48.dp
    }

    // ============================================================================
    // Radius Tokens
    // ============================================================================

    object Radius {
        val compact = 4.dp
        val standard = 8.dp
        val card = 12.dp
        val large = 16.dp
        val modal = 28.dp
        val pill = 999.dp
        val circle = 999.dp
    }

    // ============================================================================
    // Typography Scale
    // ============================================================================

    object TypeScale {
        val displayLarge = 57.sp
        val displayMedium = 45.sp
        val displaySmall = 36.sp
        val headlineLarge = 32.sp
        val headlineMedium = 28.sp
        val headlineSmall = 24.sp
        val titleLarge = 22.sp
        val titleMedium = 16.sp
        val titleSmall = 14.sp
        val labelLarge = 14.sp
        val labelMedium = 12.sp
        val labelSmall = 11.sp
        val bodyLarge = 16.sp
        val bodyMedium = 14.sp
        val bodySmall = 12.sp
    }

    // ============================================================================
    // Shadow Elevation Tokens
    // ============================================================================

    object Elevation {
        val level0 = 0.dp
        val level1 = 1.dp
        val level2 = 3.dp
        val level3 = 6.dp
        val level4 = 8.dp
        val level5 = 12.dp
        val floating = 16.dp
        val modal = 24.dp
    }

    // ============================================================================
    // Surface Style Enum
    // ============================================================================

    enum class SurfaceStyle(
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
        val defaultIntensity: Float,
        val defaultBlurLevel: BlurLevel,
        val supportsDynamicTint: Boolean,
        val supportsEdgeHighlight: Boolean,
        val supportsRefraction: Boolean,
    ) {
        CLASSIC(
            titleRes = R.string.surface_style_classic,
            summaryRes = R.string.surface_style_classic_summary,
            defaultIntensity = 1f,
            defaultBlurLevel = BlurLevel.OFF,
            supportsDynamicTint = false,
            supportsEdgeHighlight = false,
            supportsRefraction = false,
        ),
        SOFT_GLASS(
            titleRes = R.string.surface_style_soft_glass,
            summaryRes = R.string.surface_style_soft_glass_summary,
            defaultIntensity = 0.85f,
            defaultBlurLevel = BlurLevel.LOW,
            supportsDynamicTint = true,
            supportsEdgeHighlight = true,
            supportsRefraction = false,
        ),
        FROSTED_GLASS(
            titleRes = R.string.surface_style_frosted_glass,
            summaryRes = R.string.surface_style_frosted_glass_summary,
            defaultIntensity = 0.75f,
            defaultBlurLevel = BlurLevel.MEDIUM,
            supportsDynamicTint = true,
            supportsEdgeHighlight = true,
            supportsRefraction = false,
        ),
        CLEAR_GLASS(
            titleRes = R.string.surface_style_clear_glass,
            summaryRes = R.string.surface_style_clear_glass_summary,
            defaultIntensity = 0.6f,
            defaultBlurLevel = BlurLevel.LOW,
            supportsDynamicTint = true,
            supportsEdgeHighlight = true,
            supportsRefraction = false,
        ),
        LIQUID_GLASS(
            titleRes = R.string.surface_style_liquid_glass,
            summaryRes = R.string.surface_style_liquid_glass_summary,
            defaultIntensity = 0.7f,
            defaultBlurLevel = BlurLevel.HIGH,
            supportsDynamicTint = true,
            supportsEdgeHighlight = true,
            supportsRefraction = true,
        ),
        AMOLED_GLASS(
            titleRes = R.string.surface_style_amoled_glass,
            summaryRes = R.string.surface_style_amoled_glass_summary,
            defaultIntensity = 0.5f,
            defaultBlurLevel = BlurLevel.LOW,
            supportsDynamicTint = true,
            supportsEdgeHighlight = true,
            supportsRefraction = false,
        ),
        CINEMA(
            titleRes = R.string.surface_style_cinema,
            summaryRes = R.string.surface_style_cinema_summary,
            defaultIntensity = 0.9f,
            defaultBlurLevel = BlurLevel.MEDIUM,
            supportsDynamicTint = false,
            supportsEdgeHighlight = false,
            supportsRefraction = false,
        ),
        MINIMAL(
            titleRes = R.string.surface_style_minimal,
            summaryRes = R.string.surface_style_minimal_summary,
            defaultIntensity = 1f,
            defaultBlurLevel = BlurLevel.OFF,
            supportsDynamicTint = false,
            supportsEdgeHighlight = false,
            supportsRefraction = false,
        )
    }

    enum class BlurLevel(
        @StringRes val titleRes: Int,
        val renderEffectRadius: Int,
    ) {
        OFF(R.string.blur_level_off, 0),
        LOW(R.string.blur_level_low, 8),
        MEDIUM(R.string.blur_level_medium, 16),
        HIGH(R.string.blur_level_high, 24),
    }

    enum class MotionStyle(
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
        val intensity: Float,
    ) {
        FULL(R.string.motion_full, R.string.motion_full_summary, 1.0f),
        STANDARD(R.string.motion_standard, R.string.motion_standard_summary, 0.75f),
        REDUCED(R.string.motion_reduced, R.string.motion_reduced_summary, 0.5f),
        OFF(R.string.motion_off, R.string.motion_off_summary, 0f),
    }

    enum class PerformanceMode(
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
        val maxBlurLevel: BlurLevel,
        val enableDynamicEffects: Boolean,
        val simplifyAnimations: Boolean,
    ) {
        BATTERY_SAVER(
            titleRes = R.string.performance_battery_saver,
            summaryRes = R.string.performance_battery_saver_summary,
            maxBlurLevel = BlurLevel.LOW,
            enableDynamicEffects = false,
            simplifyAnimations = true,
        ),
        BALANCED(
            titleRes = R.string.performance_balanced,
            summaryRes = R.string.performance_balanced_summary,
            maxBlurLevel = BlurLevel.MEDIUM,
            enableDynamicEffects = true,
            simplifyAnimations = false,
        ),
        QUALITY(
            titleRes = R.string.performance_quality,
            summaryRes = R.string.performance_quality_summary,
            maxBlurLevel = BlurLevel.HIGH,
            enableDynamicEffects = true,
            simplifyAnimations = false,
        ),
        AUTOMATIC(
            titleRes = R.string.performance_automatic,
            summaryRes = R.string.performance_automatic_summary,
            maxBlurLevel = BlurLevel.HIGH,
            enableDynamicEffects = true,
            simplifyAnimations = false,
        ),
    }

    // ============================================================================
    // Glass Surface Configuration
    // ============================================================================

    data class GlassConfig(
        val style: SurfaceStyle = SurfaceStyle.CLASSIC,
        val intensity: Float = 1f, // 0 = transparent, 1 = opaque
        val blurLevel: BlurLevel = BlurLevel.OFF,
        val dynamicTint: DynamicTintMode = DynamicTintMode.OFF,
        val edgeHighlight: EdgeHighlightMode = EdgeHighlightMode.OFF,
        val refraction: RefractionMode = RefractionMode.OFF,
        val motionStyle: MotionStyle = MotionStyle.STANDARD,
        val performanceMode: PerformanceMode = PerformanceMode.AUTOMATIC,
        val cinemaMode: Boolean = false,
    ) {
        /** Returns a copy using [newStyle] and its default intensity and blur, preserving other settings. */
        fun copyWithStyle(newStyle: SurfaceStyle): GlassConfig = copy(
            style = newStyle,
            intensity = newStyle.defaultIntensity,
            blurLevel = newStyle.defaultBlurLevel,
        )
    }

    enum class DynamicTintMode(
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
    ) {
        OFF(R.string.dynamic_tint_off, R.string.dynamic_tint_off_summary),
        THEME(R.string.dynamic_tint_theme, R.string.dynamic_tint_theme_summary),
        MEDIA(R.string.dynamic_tint_media, R.string.dynamic_tint_media_summary),
        ADAPTIVE(R.string.dynamic_tint_adaptive, R.string.dynamic_tint_adaptive_summary),
    }

    enum class EdgeHighlightMode(
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
    ) {
        OFF(R.string.edge_highlight_off, R.string.edge_highlight_off_summary),
        SUBTLE(R.string.edge_highlight_subtle, R.string.edge_highlight_subtle_summary),
        STRONG(R.string.edge_highlight_strong, R.string.edge_highlight_strong_summary),
    }

    enum class RefractionMode(
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
    ) {
        OFF(R.string.refraction_off, R.string.refraction_off_summary),
        SUBTLE(R.string.refraction_subtle, R.string.refraction_subtle_summary),
        EXPERIMENTAL(R.string.refraction_experimental, R.string.refraction_experimental_summary),
    }

    // ============================================================================
    // CompositionLocal for GlassConfig
    // ============================================================================

    val LocalGlassConfig = staticCompositionLocalOf { GlassConfig() }

    /** Provides [config] as the glass configuration for [content] and its descendants. */
    @Composable
    fun GlassConfigProvider(config: GlassConfig, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalGlassConfig provides config) {
            content()
        }
    }

    /** Collects appearance preferences as Compose state and returns their current glass configuration. */
    @Composable
    fun rememberGlassConfig(): GlassConfig {
        val preferences = koinInject<AppearancePreferences>()
        val style by preferences.surfaceStyle.collectAsState()
        val intensity by preferences.glassIntensity.collectAsState()
        val blurLevel by preferences.glassBlurLevel.collectAsState()
        val dynamicTint by preferences.dynamicTintMode.collectAsState()
        val edgeHighlight by preferences.edgeHighlightMode.collectAsState()
        val refraction by preferences.refractionMode.collectAsState()
        val motionStyle by preferences.motionStyle.collectAsState()
        val performanceMode by preferences.performanceMode.collectAsState()
        val cinemaMode by preferences.cinemaMode.collectAsState()
        return GlassConfig(
            style = style,
            intensity = intensity,
            blurLevel = blurLevel,
            dynamicTint = dynamicTint,
            edgeHighlight = edgeHighlight,
            refraction = refraction,
            motionStyle = motionStyle,
            performanceMode = performanceMode,
            cinemaMode = cinemaMode,
        )
    }

    // ============================================================================
    // Surface Opacity Calculator
    // ============================================================================

    /**
     * Returns opacity from the current glass configuration. Classic returns [baseOpacity];
     * Minimal returns 95% of it. Other styles multiply it by the configured intensity,
     * adjust for video luminance when requested, and increase it for cinema and battery saver modes.
     * The video adjustment clamps opacity to 0.3..1; mode increases cap it at 1.
     * Other paths do not clamp the result or guarantee a contrast ratio.
     *
     * @param baseOpacity Base alpha, conventionally from 0 (transparent) to 1 (opaque).
     * @param isOnVideo Whether to adjust opacity for the background video's luminance.
     * @param videoLuminance Background luminance from 0 (dark) to 1 (bright), ignored off video.
     */
    @Composable
    fun calculateSurfaceOpacity(
        baseOpacity: Float = 1f,
        isOnVideo: Boolean = false,
        videoLuminance: Float = 0.5f, // 0 = dark, 1 = bright
    ): Float {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC) return baseOpacity
        if (glassConfig.style == SurfaceStyle.MINIMAL) return baseOpacity * 0.95f

        var opacity = glassConfig.intensity * baseOpacity

        // Adjust for video content behind glass
        if (isOnVideo) {
            // Dark video -> lighter surface for contrast
            // Bright video -> darker surface for contrast
            val contrastAdjustment = if (videoLuminance > 0.5f) {
                // Bright video: increase opacity to maintain readability
                0.15f * (videoLuminance - 0.5f) * 2f
            } else {
                // Dark video: can afford slightly more transparency
                -0.05f * (0.5f - videoLuminance) * 2f
            }
            opacity = (opacity + contrastAdjustment).coerceIn(0.3f, 1f)
        }

        // Cinema mode: slightly more opaque for immersion
        if (glassConfig.cinemaMode) {
            opacity = (opacity + 0.1f).coerceAtMost(1f)
        }

        // Performance mode: reduce opacity if needed for battery
        if (glassConfig.performanceMode == PerformanceMode.BATTERY_SAVER) {
            opacity = (opacity + 0.15f).coerceAtMost(1f)
        }

        return opacity
    }

    // ============================================================================
    // Glass Surface Modifier
    // ============================================================================

    /**
     * Adds a translucent surface background with supported edge highlights and theme tint.
     * Classic and Minimal styles leave the modifier unchanged.
     *
     * @param isOnVideo Whether video luminance should influence the calculated opacity.
     * @param videoLuminance Background video luminance from 0 (dark) to 1 (bright).
     * @param customOpacity Optional alpha overriding the calculated surface opacity.
     */
    @Composable
    fun Modifier.glassSurface(
        isOnVideo: Boolean = false,
        videoLuminance: Float = 0.5f,
        customOpacity: Float? = null,
    ): Modifier {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            return this
        }

        val effectiveOpacity =
            customOpacity ?: calculateSurfaceOpacity(isOnVideo = isOnVideo, videoLuminance = videoLuminance)

        return composed {
            val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
            val surfaceColor = colorScheme.surface.copy(alpha = effectiveOpacity)

            // Apply background color with opacity
            val backgroundModifier = this
                .then(
                    Modifier.background(
                        color = surfaceColor,
                        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                    )
                )

            // Edge highlight
            val withEdgeHighlight = if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                   glassConfig.style.supportsEdgeHighlight) {
                val highlightColor = when (glassConfig.edgeHighlight) {
                    EdgeHighlightMode.SUBTLE -> colorScheme.outline.copy(alpha = 0.15f)
                    EdgeHighlightMode.STRONG -> colorScheme.outline.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
                backgroundModifier
                    .border(
                      width = 1.dp,
                      color = highlightColor,
                      shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                    )
            } else {
                backgroundModifier
            }

            // Dynamic tint
            val withDynamicTint = if (glassConfig.dynamicTint != DynamicTintMode.OFF &&
                   glassConfig.style.supportsDynamicTint) {
                withEdgeHighlight.background(
                    color = colorScheme.primary.copy(alpha = 0.08f),
                    shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                )
            } else {
                withEdgeHighlight
            }

            withDynamicTint
        }
    }
}
