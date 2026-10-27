/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.quantummpv.app.preferences.preference.PreferenceStore
import com.quantummpv.app.preferences.preference.getEnum
import com.quantummpv.app.ui.player.controls.components.rememberTvInitialFocusRequester
import com.quantummpv.app.ui.player.controls.components.tvFocusGroup
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.player.controls.components.tvInitialFocus
import com.quantummpv.app.ui.theme.AppTheme
import com.quantummpv.app.ui.theme.CustomThemeDefinition
import com.quantummpv.app.ui.theme.DarkMode
import com.quantummpv.app.ui.theme.DesignTokens.DynamicTintMode
import com.quantummpv.app.ui.theme.DesignTokens.EdgeHighlightMode
import com.quantummpv.app.ui.theme.DesignTokens.GlassConfig
import com.quantummpv.app.ui.theme.DesignTokens.MotionStyle
import com.quantummpv.app.ui.theme.DesignTokens.PerformanceMode
import com.quantummpv.app.ui.theme.DesignTokens.RefractionMode
import com.quantummpv.app.ui.theme.DesignTokens.SurfaceStyle
import com.quantummpv.app.ui.theme.WallpaperScaleMode
import com.quantummpv.app.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

class AppearancePreferences(
  private val preferenceStore: PreferenceStore,
) {
  companion object {
    const val CUSTOM_WALLPAPER_URI_KEY = "custom_wallpaper_uri"

    const val DEFAULT_TOP_LEFT_CONTROLS = "BACK_ARROW,VIDEO_TITLE"
    const val DEFAULT_TOP_RIGHT_CONTROLS = "SUBTITLES,AUDIO_TRACK,MORE_OPTIONS"
    const val DEFAULT_BOTTOM_RIGHT_CONTROLS = "SCREEN_ROTATION,PICTURE_IN_PICTURE,ASPECT_RATIO"
    const val DEFAULT_BOTTOM_LEFT_CONTROLS = "LOCK_CONTROLS,PLAYBACK_SPEED"
    const val DEFAULT_PORTRAIT_BOTTOM_CONTROLS =
      "LOCK_CONTROLS,SUBTITLES,AUDIO_TRACK,PLAYBACK_SPEED,SCREEN_ROTATION,MORE_OPTIONS"
  }

  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.System)
  val appTheme = preferenceStore.getEnum("app_theme", AppTheme.Dynamic)
  val customTheme = preferenceStore.getString("custom_theme", "")
  val selectedCustomThemeName = preferenceStore.getString("selected_custom_theme_name", "")
  val customWallpaperUri = preferenceStore.getString(CUSTOM_WALLPAPER_URI_KEY, "")
  val customWallpaperZoom = preferenceStore.getFloat("custom_wallpaper_zoom", 1f)
  val customWallpaperOffsetX = preferenceStore.getFloat("custom_wallpaper_offset_x", 0f)
  val customWallpaperOffsetY = preferenceStore.getFloat("custom_wallpaper_offset_y", 0f)
  val customWallpaperScaleMode = preferenceStore.getEnum("custom_wallpaper_scale_mode", WallpaperScaleMode.Fit)
  val customWallpaperBlur = preferenceStore.getFloat("custom_wallpaper_blur", 0f)
  val customWallpaperAlpha = preferenceStore.getFloat("custom_wallpaper_alpha", 1f)
  val amoledMode = preferenceStore.getBoolean("amoled_mode", false)

  // New Glass/Surface Style System
  val surfaceStyle = preferenceStore.getEnum("surface_style", SurfaceStyle.CLASSIC)

  // Read the former navigation-only preference until the user chooses a global surface style.
  val legacyGlassBottomNavigation = preferenceStore.getBoolean("glass_bottom_navigation", false)
  val hasLegacyGlassBottomNavigation: Boolean
    get() = "glass_bottom_navigation" in preferenceStore.getAll()
  val hasStoredSurfaceStyle: Boolean
    get() = "surface_style" in preferenceStore.getAll()

  /**
   * Source of the intensity/blur fallbacks. A saved style may predate its intensity/blur keys (partial
   * import, older migration); such users keep the look they had, the former Liquid Glass fallback,
   * while fresh installs get Classic's values.
   */
  private val glassFallbackStyle = if (hasStoredSurfaceStyle) SurfaceStyle.LIQUID_GLASS else SurfaceStyle.CLASSIC
  val glassIntensity = preferenceStore.getFloat("glass_intensity", glassFallbackStyle.defaultIntensity)
  val glassBlurLevel = preferenceStore.getEnum("glass_blur_level", glassFallbackStyle.defaultBlurLevel)
  val dynamicTintMode = preferenceStore.getEnum("dynamic_tint_mode", DynamicTintMode.THEME)
  val edgeHighlightMode = preferenceStore.getEnum("edge_highlight_mode", EdgeHighlightMode.SUBTLE)
  val refractionMode = preferenceStore.getEnum("refraction_mode", RefractionMode.SUBTLE)
  val motionStyle = preferenceStore.getEnum("motion_style", MotionStyle.STANDARD)
  val performanceMode = preferenceStore.getEnum("performance_mode", PerformanceMode.AUTOMATIC)
  val cinemaMode = preferenceStore.getBoolean("cinema_mode", false)

  // Computed GlassConfig - combines all glass settings
  // This is a computed property that always returns the current config based on preferences
  val glassConfig: GlassConfig
    get() {
      val storedSurfaceStyle = surfaceStyle.get()
      return GlassConfig(
        style = storedSurfaceStyle.selectableStyle,
        intensity = glassIntensity.get(),
        blurLevel = glassBlurLevel.get(),
        dynamicTint = storedSurfaceStyle.effectiveDynamicTint(dynamicTintMode.get()),
        edgeHighlight = storedSurfaceStyle.effectiveEdgeHighlight(edgeHighlightMode.get()),
        refraction = refractionMode.get(),
        motionStyle = motionStyle.get(),
        performanceMode = performanceMode.get(),
        cinemaMode = cinemaMode.get(),
      )
    }

  /**
   * Persists [style]'s canonical preset and its default intensity and blur, even when reselecting it.
   * Tint and highlight preferences remain stored for compatibility, but their effective values are
   * fixed by the selected surface style.
   */
  fun setSurfaceStyle(style: SurfaceStyle) {
    val preset = glassConfig.copyWithStyle(style)
    surfaceStyle.set(preset.style)
    glassIntensity.set(preset.intensity)
    glassBlurLevel.set(preset.blurLevel)
    if (style == SurfaceStyle.CINEMA) {
      dynamicTintMode.set(preset.dynamicTint)
      edgeHighlightMode.set(preset.edgeHighlight)
    }
  }

  /**
   * Returns [style]'s canonical preset and persists it only when [style] is a legacy preset.
   * Preserves custom intensity and blur; legacy tint and highlight values remain readable while the
   * renderer applies the fixed treatment for the selected style.
   *
   * @param style Stored preset to migrate; defaults to the current surface-style preference.
   */
  fun canonicalizeSurfaceStyle(style: SurfaceStyle = surfaceStyle.get()): SurfaceStyle {
    val canonicalStyle = style.selectableStyle
    if (style != canonicalStyle) {
      if (style == SurfaceStyle.CINEMA) {
        dynamicTintMode.set(DynamicTintMode.OFF)
        edgeHighlightMode.set(EdgeHighlightMode.OFF)
      }
      surfaceStyle.set(canonicalStyle)
    }
    return canonicalStyle
  }

  val useSystemOsdFont = preferenceStore.getBoolean("use_system_osd_font", false)
  val useSystemFont = preferenceStore.getBoolean("use_system_font", false)
  val unlimitedNameLines = preferenceStore.getBoolean("unlimited_name_lines", false)
  val hidePlayerButtonsBackground = preferenceStore.getBoolean("hide_player_buttons_background", false)
  val forceDarkPlayerButtonsBackground = preferenceStore.getBoolean("force_dark_player_buttons_background", false)
  val showUnplayedOldVideoLabel = preferenceStore.getBoolean("show_unplayed_old_video_label", true)
  val unplayedOldVideoDays = preferenceStore.getInt("unplayed_old_video_days", 7)
  val showNetworkThumbnails = preferenceStore.getBoolean("show_network_thumbnails", false)
  val seekbarStyle = preferenceStore.getEnum("seekbar_style", SeekbarStyle.Thick)
  val portraitPlaybackControlsPosition =
    preferenceStore.getEnum("portrait_playback_controls_position", PortraitPlaybackControlsPosition.Center)
  val showMusicTab = preferenceStore.getBoolean("show_music_tab", true)
  val showRecentsTab = preferenceStore.getBoolean("show_recents_tab", true)
  val showPlaylistsTab = preferenceStore.getBoolean("show_playlists_tab", true)
  val showNetworkTab = preferenceStore.getBoolean("show_network_tab", false)
  val showJellyfinTab = preferenceStore.getBoolean("show_jellyfin_tab", false)
  val showQuickPlayFab = preferenceStore.getBoolean("show_quick_play_fab", true)
  val quickPlayFabDirect = preferenceStore.getBoolean("quick_play_fab_direct", false)

  // Recommended layout for new installs: the controls people reach for while watching, with the rest in
  // More. Existing installs keep the layout they had (see PreferenceDefaultsGeneration).
  val topLeftControls = preferenceStore.getString("top_left_controls", DEFAULT_TOP_LEFT_CONTROLS)
  val topRightControls = preferenceStore.getString("top_right_controls", DEFAULT_TOP_RIGHT_CONTROLS)
  val bottomRightControls = preferenceStore.getString("bottom_right_controls", DEFAULT_BOTTOM_RIGHT_CONTROLS)
  val bottomLeftControls = preferenceStore.getString("bottom_left_controls", DEFAULT_BOTTOM_LEFT_CONTROLS)
  val portraitBottomControls =
    preferenceStore.getString("portrait_bottom_controls", DEFAULT_PORTRAIT_BOTTOM_CONTROLS)

  init {
    canonicalizeSurfaceStyle()

    if (dynamicTintMode.get() != DynamicTintMode.OFF && dynamicTintMode.get() != DynamicTintMode.THEME) {
      dynamicTintMode.set(DynamicTintMode.OFF)
    }

    if (selectedCustomThemeName.get().isBlank()) {
      CustomThemeDefinition.parse(customTheme.get())?.let { legacyTheme ->
        selectedCustomThemeName.set(legacyTheme.name)
      }
    }
  }

  /** Replaces every player control region with the recommended layout. */
  fun resetPlayerLayoutToRecommended() {
    topLeftControls.set(DEFAULT_TOP_LEFT_CONTROLS)
    topRightControls.set(DEFAULT_TOP_RIGHT_CONTROLS)
    bottomRightControls.set(DEFAULT_BOTTOM_RIGHT_CONTROLS)
    bottomLeftControls.set(DEFAULT_BOTTOM_LEFT_CONTROLS)
    portraitBottomControls.set(DEFAULT_PORTRAIT_BOTTOM_CONTROLS)
  }

  fun parseButtons(
    csv: String,
    usedButtons: MutableSet<PlayerButton>,
  ): List<PlayerButton> =
    csv
      .splitToSequence(',')
      .map { it.trim().uppercase() }
      .mapNotNull { name ->
        try {
          PlayerButton.valueOf(name)
        } catch (_: IllegalArgumentException) {
          null
        }
      }.filter { it != PlayerButton.NONE }
      .filter { usedButtons.add(it) }
      .toList()
}

enum class PortraitPlaybackControlsPosition(
  val displayName: String,
) {
  Center("Center of screen"),
  BelowSeekbar("Between seekbar and controls"),
}

@Composable
fun MultiChoiceSegmentedButton(
  choices: ImmutableList<String>,
  selectedIndices: ImmutableList<Int>,
  onClick: (Int, Offset) -> Unit,
  modifier: Modifier = Modifier,
) {
  val initialFocusRequester =
    rememberTvInitialFocusRequester(
      enabled = choices.isNotEmpty(),
      requestKey = selectedIndices,
    )
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .tvFocusGroup()
        .padding(MaterialTheme.spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
  ) {
    choices.forEachIndexed { index, choice ->
      var buttonCenter by remember(choice) { mutableStateOf(Offset.Zero) }
      ToggleButton(
        checked = selectedIndices.contains(index),
        onCheckedChange = { onClick(index, buttonCenter) },
        modifier =
          Modifier
            .weight(1f)
            .then(
              if (index == selectedIndices.firstOrNull()) {
                Modifier.tvInitialFocus(initialFocusRequester)
              } else {
                Modifier
              },
            ).tvFocusHighlight(MaterialTheme.shapes.medium, focusedScale = 1.03f)
            .defaultMinSize(minHeight = MaterialTheme.spacing.extraLarge)
            .onGloballyPositioned { buttonCenter = it.boundsInWindow().center }
            .semantics { role = Role.RadioButton },
        colors =
          ToggleButtonDefaults.colors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          ),
        shapes =
          when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
          },
      ) {
        Text(text = choice)
      }
    }
  }
}
