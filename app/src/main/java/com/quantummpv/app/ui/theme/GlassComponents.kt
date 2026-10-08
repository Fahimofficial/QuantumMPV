/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.theme

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.quantummpv.app.ui.theme.DesignTokens.BlurLevel
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
     * A glass surface that adapts to the current GlassConfig.
     * Use this instead of Material3 Surface for glass-enabled surfaces.
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

        // Keep blur on a sibling background layer so foreground content stays sharp.
        val effectiveBlurLevel = if (glassConfig.blurLevel.ordinal <=
            glassConfig.performanceMode.maxBlurLevel.ordinal
        ) {
            glassConfig.blurLevel
        } else {
            glassConfig.performanceMode.maxBlurLevel
        }
        val blurModifier = if (effectiveBlurLevel != BlurLevel.OFF &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            Modifier.graphicsLayer {
                renderEffect = RenderEffect.createBlurEffect(
                    effectiveBlurLevel.renderEffectRadius.toFloat(),
                    effectiveBlurLevel.renderEffectRadius.toFloat(),
                    android.graphics.Shader.TileMode.CLAMP,
                ).asComposeRenderEffect()
            }
        } else {
            Modifier
        }

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
        val dynamicTintModifier = if (glassConfig.dynamicTint != DynamicTintMode.OFF &&
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
                    .then(blurModifier)
                    .background(surfaceColor, shape)
                    .then(dynamicTintModifier),
            )
            content()
        }
    }

    /**
     * Glass bottom navigation bar background
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
     * Glass bottom sheet background
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
     * Glass dialog/overlay background
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
     * Glass player control surface (for top/bottom bars, seekbar area, menus)
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
     * Glass mini-player surface
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
                    if (glassConfig.dynamicTint != DynamicTintMode.OFF &&
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
     * Glass media card surface
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
     * Glass navigation pill background
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
                    Color.Transparent
                },
                shape = shape,
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
