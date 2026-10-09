/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.theme.DesignTokens.DynamicTintMode
import com.quantummpv.app.ui.theme.DesignTokens.EdgeHighlightMode
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import com.quantummpv.app.ui.theme.DesignTokens.RefractionMode
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.theme.DesignTokens.calculateSurfaceOpacity
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.HazePerformanceMode as HazeRenderPerformance

/** Shared backdrop state provided by [MpvrxTheme]; null is used by isolated previews and tests. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Shared glass treatments for browser, navigation, dialogs, player controls, and sheets.
 * Non-classic styles use Haze's backdrop Glass renderer. Classic and Minimal retain their
 * existing Material surfaces so the feature remains optional and backwards compatible.
 */
object GlassComponents {
    @Composable
    fun GlassSurface(
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.medium,
        isOnVideo: Boolean = false,
        videoLuminance: Float = 0.5f,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(modifier, shape, isOnVideo, videoLuminance, content = content)
    }

    @Composable
    fun GlassBottomNavBackground(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(modifier, MaterialTheme.shapes.medium, content = content)
    }

    @Composable
    fun GlassBottomSheetBackground(
        modifier: Modifier = Modifier,
        shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        classicColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(
            modifier = modifier,
            shape = shape,
            opacityBoost = 0.1f,
            classicColor = classicColor,
            content = content,
        )
    }

    @Composable
    fun GlassDialogBackground(
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.extraLarge,
        classicColor: Color = MaterialTheme.colorScheme.surface,
        classicTonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(
            modifier = modifier,
            shape = shape,
            opacityBoost = 0.05f,
            classicColor = classicColor,
            classicTonalElevation = classicTonalElevation,
            content = content,
        )
    }

    @Composable
    fun GlassPlayerSurface(
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.medium,
        isOnVideo: Boolean = true,
        videoLuminance: Float = 0.5f,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(
            modifier = modifier,
            shape = shape,
            isOnVideo = isOnVideo,
            videoLuminance = videoLuminance,
            content = content,
        )
    }

    @Composable
    fun GlassMiniPlayerSurface(
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.large,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(
            modifier = modifier,
            shape = shape,
            opacityBoost = 0.1f,
            content = content,
        )
    }

    @Composable
    fun GlassMediaCardSurface(
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.medium,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(
            modifier = modifier,
            shape = shape,
            opacityScale = 0.9f,
            content = content,
        )
    }

    @Composable
    fun GlassNavigationPill(
        modifier: Modifier = Modifier,
        isSelected: Boolean = false,
        forceGlass: Boolean = false,
        shape: Shape = MaterialTheme.shapes.medium,
        content: @Composable () -> Unit,
    ) {
        GlassContainer(
            modifier = modifier,
            shape = shape,
            selected = isSelected,
            forceGlass = forceGlass,
            classicColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            classicTonalElevation = 6.dp,
            classicShadowElevation = 8.dp,
            content = content,
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun GlassContainer(
    modifier: Modifier,
    shape: Shape,
    isOnVideo: Boolean = false,
    videoLuminance: Float = 0.5f,
    opacityBoost: Float = 0f,
    opacityScale: Float = 1f,
    selected: Boolean = false,
    forceGlass: Boolean = false,
    classicColor: Color = MaterialTheme.colorScheme.surface,
    classicTonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    classicShadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val config = LocalGlassConfig.current
    val colors = MaterialTheme.colorScheme

    val usesGlassStyle = config.style != SurfaceStyle.CLASSIC && config.style != SurfaceStyle.MINIMAL
    if (!usesGlassStyle && !forceGlass) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = if (selected) colors.primaryContainer else classicColor,
            tonalElevation = classicTonalElevation,
            shadowElevation = classicShadowElevation,
            content = content,
        )
        return
    }

    val effectiveStyle = if (usesGlassStyle) config.style else SurfaceStyle.LIQUID_GLASS
    val hazeState = LocalHazeState.current
    val opacity =
        ((if (usesGlassStyle) {
            calculateSurfaceOpacity(isOnVideo = isOnVideo, videoLuminance = videoLuminance)
        } else {
            config.intensity
        } + opacityBoost) * opacityScale)
            .coerceIn(0f, 1f)
    val containerColor = (if (selected) colors.primaryContainer else colors.surface).copy(alpha = opacity)
    val tint =
        when {
            !effectiveStyle.supportsDynamicTint -> null
            config.dynamicTint == DynamicTintMode.OFF -> null
            config.dynamicTint == DynamicTintMode.THEME -> colors.primary.copy(alpha = if (selected) 0.16f else 0.1f)
            else -> colors.tertiary.copy(alpha = 0.08f)
        }
    val glassShape = shape.toGlassShape()
    val baseStyle =
        when (effectiveStyle) {
            SurfaceStyle.CLEAR_GLASS -> GlassStyle.clear
            SurfaceStyle.LIQUID_GLASS -> if (isOnVideo) GlassStyle.clear else GlassStyle.regular
            SurfaceStyle.SOFT_GLASS -> GlassStyle.regular
            SurfaceStyle.FROSTED_GLASS,
            SurfaceStyle.AMOLED_GLASS,
            SurfaceStyle.CINEMA,
            SurfaceStyle.CLASSIC,
            SurfaceStyle.MINIMAL,
            -> GlassStyle.regular
        }
    val blurRadius = config.blurLevel.renderEffectRadius.dp
    val refractionStrength =
        if (!effectiveStyle.supportsRefraction) {
            0f
        } else {
            when (config.refraction) {
                RefractionMode.OFF -> 0f
                RefractionMode.SUBTLE -> 0.28f
                RefractionMode.EXPERIMENTAL -> 0.68f
            }
        }
    val edgeHighlight =
        when (config.edgeHighlight) {
            EdgeHighlightMode.OFF -> 0f
            EdgeHighlightMode.SUBTLE -> 0.32f
            EdgeHighlightMode.STRONG -> 0.68f
        }
    val optics =
        GlassDefaults.optics.copy(
            refractionStrength = refractionStrength,
            blurRadius = OpticalSizeValue.Fixed(blurRadius),
            depth = OpticalSizeValue.Fixed(if (isOnVideo) 0.7f else 1f),
        )
    val glassStyle =
        remember(
            baseStyle,
            containerColor,
            tint,
            glassShape,
            optics,
            edgeHighlight,
            config.refraction,
            effectiveStyle,
            selected,
        ) {
            baseStyle.then {
                backgroundColor(containerColor)
                if (tint != null) tint(tint)
                shape(glassShape)
                optics(optics)
                specularIntensity(edgeHighlight)
                edgeShadow(if (edgeHighlight == 0f) Color.Transparent else colors.onSurface.copy(alpha = 0.14f))
                ambientResponse(if (edgeHighlight == 0f) 0.08f else 0.18f + edgeHighlight * 0.38f)
                edgeSoftness(if (edgeHighlight == 0f) 0.dp else 1.dp)
                chromaticAberrationStrength(
                    if (effectiveStyle.supportsRefraction && config.refraction == RefractionMode.EXPERIMENTAL) 0.12f else 0f,
                )
            }
        }
    val borderColor =
        when (config.edgeHighlight) {
            EdgeHighlightMode.OFF -> Color.Transparent
            EdgeHighlightMode.SUBTLE -> colors.onSurface.copy(alpha = 0.14f)
            EdgeHighlightMode.STRONG -> colors.onSurface.copy(alpha = 0.28f)
        }
    val hazeModifier =
        if (hazeState != null) {
            Modifier.hazeGlass(
                input = HazeInput.Backdrop(hazeState),
                style = glassStyle,
                performanceMode = config.performanceMode.toHazePerformanceMode(),
            )
        } else {
            Modifier
                .background(containerColor, shape)
                .then(if (tint == null) Modifier else Modifier.background(tint, shape))
        }

    Box(
        modifier =
            modifier
                .clip(shape)
                .then(hazeModifier)
                .then(if (borderColor == Color.Transparent) Modifier else Modifier.border(1.dp, borderColor, shape)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

private fun Shape.toGlassShape(): RoundedCornerShape =
    this as? RoundedCornerShape ?: RoundedCornerShape(percent = 50)

private fun PerformanceMode.toHazePerformanceMode(): HazeRenderPerformance =
    when (this) {
        PerformanceMode.BATTERY_SAVER -> HazeRenderPerformance.Performance
        PerformanceMode.BALANCED -> HazeRenderPerformance.Balanced
        PerformanceMode.QUALITY -> HazeRenderPerformance.Quality
        PerformanceMode.AUTOMATIC -> HazeRenderPerformance.Default
    }
