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
import com.quantummpv.app.ui.theme.DesignTokens.LocalGlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.theme.DesignTokens.rememberGlassConfig

/**
 * Glass-aware theme wrapper that provides the GlassConfig and adjusts Material3 theme
 * based on the selected surface style.
 */
@Composable
fun GlassAwareTheme(
    content: @Composable () -> Unit,
) {
    val glassConfig = rememberGlassConfig()

    // If classic or minimal style, just use standard theme
    if (glassConfig.style == SurfaceStyle.CLASSIC || glassConfig.style == SurfaceStyle.MINIMAL) {
        content()
        return
    }

    // For glass styles, we might want to adjust the theme slightly
    // (e.g., reduce default surface opacities, adjust colors)
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

    // Glass surfaces apply opacity locally; the global Material color scheme remains unchanged.
    CompositionLocalProvider(
        LocalGlassConfig provides glassConfig,
    ) {
        content()
    }
}