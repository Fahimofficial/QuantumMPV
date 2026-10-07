/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.ui.theme.DesignTokens.GlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import org.koin.compose.koinInject

/**
 * Glass-aware theme wrapper that provides the GlassConfig and adjusts Material3 theme
 * based on the selected surface style.
 */
@Composable
fun GlassAwareTheme(
    content: @Composable () -> Unit,
) {
    val preferences = koinInject<AppearancePreferences>()
    val surfaceStyle by preferences.surfaceStyle.collectAsState()
    val amoledMode by preferences.amoledMode.collectAsState()
    
    // If classic or minimal style, just use standard theme
    if (surfaceStyle == SurfaceStyle.CLASSIC || surfaceStyle == SurfaceStyle.MINIMAL) {
        content()
        return
    }
    
    // For glass styles, we might want to adjust the theme slightly
    // (e.g., reduce default surface opacities, adjust colors)
    val glassConfig = preferences.glassConfig
    CompositionLocalProvider(
        LocalGlassConfig provides glassConfig,
    ) {
        content()
    }
}

/**
 * Extension to apply glass-aware surface colors.
 */
@Composable
fun GlassAwareSurfaceColors(
    isOnVideo: Boolean = false,
    videoLuminance: Float = 0.5f,
    content: @Composable () -> Unit,
) {
    val glassConfig = LocalGlassConfig.current
    
    if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
        content()
        return
    }
    
    // Glass styles might need adjusted surface colors
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val adjustedSurface = colorScheme.surface.copy(alpha = 
        DesignTokens.calculateSurfaceOpacity(isOnVideo = isOnVideo, videoLuminance = videoLuminance)
    )
    
    // This would require CompositionLocal for colorScheme, which is not easily replaceable
    // For now, just provide the glass config
    CompositionLocalProvider(
        LocalGlassConfig provides glassConfig,
    ) {
        content()
    }
}