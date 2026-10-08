/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.theme.DesignTokens.DynamicTintMode
import com.quantummpv.app.ui.theme.DesignTokens.EdgeHighlightMode
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.theme.DesignTokens.calculateSurfaceOpacity

/**
 * Reusable glass surface components for QuantumMPV.
 * These components respect the global GlassConfig and provide consistent glass effects.
 */
object GlassComponents {
    /**
     * Displays [content] on a surface styled by the current glass configuration.
     * Classic and Minimal use a Material surface. Other styles apply opacity, supported
     * edge highlights, and theme tint while keeping foreground content sharp.
     *
     * @param isOnVideo Whether background video luminance should affect opacity.
     * @param videoLuminance Background luminance from 0 (dark) to 1 (bright), ignored off video.
     */
    @Composable
    fun GlassSurface(
        modifier: Modifier = Modifier,
        shape: Shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        isOnVideo: Boolean = false,
        videoLuminance: Float = 0.5f,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        // Quick path for classic/minimal styles
        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier,
                shape = shape,
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                content = { content() },
            )
            return
        }

        val effectiveOpacity = calculateSurfaceOpacity(
            isOnVideo = isOnVideo,
            videoLuminance = videoLuminance,
        )

        val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
        val surfaceColor = colorScheme.surface.copy(alpha = effectiveOpacity)

        // A backdrop source is not available at this composable boundary. Do not blur the
        // solid surface fill: that has no visual effect and cannot blur content behind it.

        // Edge highlight
        val edgeHighlightModifier = if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
               glassConfig.style.supportsEdgeHighlight) {
            Modifier
                .border(
                    width = 1.dp,
                    color = when (glassConfig.edgeHighlight) {
                        EdgeHighlightMode.SUBTLE -> colorScheme.outline.copy(alpha = 0.15f)
                        EdgeHighlightMode.STRONG -> colorScheme.outline.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    },
                    shape = shape,
                )
        } else {
            Modifier
        }

        // Dynamic tint overlay
        val dynamicTintModifier = if (glassConfig.dynamicTint == DynamicTintMode.THEME &&
               glassConfig.style.supportsDynamicTint) {
            Modifier.background(
                color = colorScheme.primary.copy(alpha = 0.08f),
                shape = shape,
            )
        } else {
            Modifier
        }

        Box(
            modifier = modifier
                .clip(shape)
                .then(edgeHighlightModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor, shape)
                    .then(dynamicTintModifier),
            )
            content()
        }
    }

    /**
     * Displays bottom navigation content on a Material surface for Classic and Minimal styles.
     * Other styles use the configured opacity and supported edge highlights.
     */
    @Composable
    fun GlassBottomNavBackground(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                content = { content() },
            )
            return
        }

        val effectiveOpacity = calculateSurfaceOpacity()
        val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = effectiveOpacity)
        val containerShape = androidx.compose.material3.MaterialTheme.shapes.medium

        Box(
            modifier = modifier
                .clip(containerShape)
                .background(surfaceColor, containerShape)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = containerShape,
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }

    /**
     * Displays bottom sheet content on a Material surface for Classic and Minimal styles.
     * Other styles add 0.1 to the configured opacity, capped at 1, with supported edge highlights.
     */
    @Composable
    fun GlassBottomSheetBackground(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                content = { content() },
            )
            return
        }

        val effectiveOpacity = (calculateSurfaceOpacity() + 0.1f).coerceAtMost(1f)
        val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = effectiveOpacity)
        val containerShape = androidx.compose.material3.MaterialTheme.shapes.large

        Box(
            modifier = modifier
                .clip(containerShape)
                .background(surfaceColor, containerShape)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = containerShape,
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }

    /**
     * Displays dialog content using [classicColor] and [classicTonalElevation] for Classic
     * and Minimal styles. Other styles use the theme surface color with calculated opacity
     * increased by 0.05 and capped at 1, plus supported edge highlights.
     */
    @Composable
    fun GlassDialogBackground(
        modifier: Modifier = Modifier,
        shape: Shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
        classicColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        classicTonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier,
                shape = shape,
                color = classicColor,
                tonalElevation = classicTonalElevation,
                content = { content() },
            )
            return
        }

        val effectiveOpacity = (calculateSurfaceOpacity() + 0.05f).coerceAtMost(1f)
        val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = effectiveOpacity)

        Box(
            modifier = modifier
                .clip(shape)
                .background(surfaceColor, shape)
                .then(
                    if (glassConfig.dynamicTint == DynamicTintMode.THEME &&
                       glassConfig.style.supportsDynamicTint) {
                        Modifier.background(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                                    .copy(alpha = 0.15f),
                                shape = shape,
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }

    /**
     * Displays player controls on a Material surface for Classic and Minimal styles.
     * Other styles apply video-adjusted opacity and supported edge highlights.
     *
     * @param isOnVideo Whether background video luminance should affect opacity.
     * @param videoLuminance Background luminance from 0 (dark) to 1 (bright), ignored off video.
     */
    @Composable
    fun GlassPlayerSurface(
        modifier: Modifier = Modifier,
        isOnVideo: Boolean = true,
        videoLuminance: Float = 0.5f,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                content = { content() },
            )
            return
        }

        val effectiveOpacity = calculateSurfaceOpacity(
            isOnVideo = isOnVideo,
            videoLuminance = videoLuminance,
        )

        val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = effectiveOpacity)
        val containerShape = androidx.compose.material3.MaterialTheme.shapes.medium

        Box(
            modifier = modifier
                .clip(containerShape)
                .background(surfaceColor, containerShape)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = containerShape,
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }

    /**
     * Displays mini-player content on a Material surface for Classic and Minimal styles.
     * Other styles add 0.1 to calculated opacity, capped at 1, and animate color and opacity
     * changes over 300 milliseconds, with supported edge highlights and theme tint.
     */
    @Composable
    fun GlassMiniPlayerSurface(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                content = { content() },
            )
            return
        }

        val effectiveOpacity = (calculateSurfaceOpacity() + 0.1f).coerceAtMost(1f)
        val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = effectiveOpacity)
        val containerShape = androidx.compose.material3.MaterialTheme.shapes.large

        // Animate surface appearance
        val animatedOpacity by animateFloatAsState(
            targetValue = effectiveOpacity,
            animationSpec = tween(300),
            label = "glassMiniPlayerOpacity",
        )
        val animatedSurfaceColor by animateColorAsState(
            targetValue = surfaceColor,
            animationSpec = tween(300),
            label = "glassMiniPlayerColor",
        )
        val animatedColor = animatedSurfaceColor.copy(alpha = animatedOpacity)

        Box(
            modifier = modifier
                .clip(containerShape)
                .background(animatedColor, containerShape)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = containerShape,
                            )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (glassConfig.dynamicTint == DynamicTintMode.THEME &&
                       glassConfig.style.supportsDynamicTint) {
                        Modifier.background(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = containerShape,
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }

    /**
     * Displays media card content on a Material surface for Classic and Minimal styles.
     * Other styles use 90% of calculated opacity, capped at 1, with supported edge highlights.
     */
    @Composable
    fun GlassMediaCardSurface(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                content = { content() },
            )
            return
        }

        // Media cards use lighter glass treatment
        val effectiveOpacity = (calculateSurfaceOpacity() * 0.9f).coerceAtMost(1f)
        val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = effectiveOpacity)
        val containerShape = androidx.compose.material3.MaterialTheme.shapes.medium

        Box(
            modifier = modifier
                .clip(containerShape)
                .background(surfaceColor, containerShape)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = containerShape,
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }

    /**
     * Displays navigation content with a background determined by [isSelected].
     * Classic and Minimal use the primary container color when selected and transparency otherwise.
     * Other styles use primary container alpha 0.9 when selected, or 70% of calculated opacity
     * with a minimum of 0.3 otherwise. Supported edge highlights appear only when selected.
     */
    @Composable
    fun GlassNavigationPill(
        modifier: Modifier = Modifier,
        isSelected: Boolean = false,
        shape: Shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier,
                color = if (isSelected) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = shape,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                content = { content() },
            )
            return
        }

        val surfaceColor = if (isSelected) {
            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(
                alpha = (calculateSurfaceOpacity() * 0.7f).coerceAtLeast(0.3f),
            )
        }

        Box(
            modifier = modifier
                .clip(shape)
                .background(surfaceColor, shape)
                .then(
                    if (glassConfig.dynamicTint == DynamicTintMode.THEME &&
                       glassConfig.style.supportsDynamicTint) {
                        Modifier.background(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight && isSelected) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = shape,
                            )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
