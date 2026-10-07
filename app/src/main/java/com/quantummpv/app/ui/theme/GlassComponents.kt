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
import androidx.compose.ui.graphics.Color
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
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.MaterialTheme.shapes.medium,
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

        // Blur effect (API 31+)
        val blurModifier = if (glassConfig.blurLevel != BlurLevel.OFF &&
               glassConfig.performanceMode.maxBlurLevel.ordinal >= glassConfig.blurLevel.ordinal &&
               android.os.Build.VERSION.SDK_INT >= 31) {
            Modifier
                .graphicsLayer {
                    renderEffect = RenderEffect.createBlurEffect(
                        radiusX = glassConfig.blurLevel.renderEffectRadius.toFloat(),
                        radiusY = glassConfig.blurLevel.renderEffectRadius.toFloat(),
                        edgeTreatment = android.graphics.Shader.TileMode.CLAMP,
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
            // Applied via a subtle overlay
            Modifier
        } else {
            Modifier
        }

        Box(
            modifier = modifier
                .then(blurModifier)
                .then(edgeHighlightModifier)
                .then(dynamicTintModifier)
                .background(surfaceColor, shape),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
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

        Box(
            modifier = modifier
                .background(surfaceColor)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
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

        Box(
            modifier = modifier
                .background(surfaceColor)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = androidx.compose.material3.MaterialTheme.shapes.large,
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

        val effectiveOpacity = (calculateSurfaceOpacity() + 0.05f).coerceAtMost(1f)
        val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = effectiveOpacity)

        Box(
            modifier = modifier
                .background(surfaceColor)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                                    .copy(alpha = 0.15f),
                                shape = androidx.compose.material3.MaterialTheme.shapes.large,
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

        Box(
            modifier = modifier
                .background(surfaceColor)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
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
        isExpanded: Boolean = false,
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

        // Animate surface appearance
        val animatedOpacity by animateFloatAsState(
            targetValue = effectiveOpacity,
            animationSpec = tween(300),
        )

        val animatedColor = surfaceColor.copy(alpha = animatedOpacity)

        Box(
            modifier = modifier
                .background(animatedColor)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = androidx.compose.material3.MaterialTheme.shapes.large,
                            )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (glassConfig.dynamicTint != DynamicTintMode.OFF &&
                       glassConfig.style.supportsDynamicTint) {
                        Modifier
                            .graphicsLayer {
                                // Subtle tint overlay
                            }
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

        Box(
            modifier = modifier
                .background(surfaceColor)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
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
        content: @Composable () -> Unit,
    ) {
        val glassConfig = LocalGlassConfig.current

        if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
            Surface(
                modifier = modifier
                    .background(
                        if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    ),
                content = { content() },
            )
            return
        }

        val surfaceColor = if (isSelected) {
            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        } else {
            androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha =
                calculateSurfaceOpacity() * 0.7f)
        }

        Box(
            modifier = modifier
                .background(surfaceColor)
                .then(
                    if (glassConfig.edgeHighlight != EdgeHighlightMode.OFF &&
                       glassConfig.style.supportsEdgeHighlight && isSelected) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
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