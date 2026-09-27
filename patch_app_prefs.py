with open("app/src/main/java/com/quantummpv/app/preferences/AppearancePreferences.kt", "r") as f:
    content = f.read()

import_lines = "import com.quantummpv.app.ui.theme.CustomThemeDefinition\nimport com.quantummpv.app.ui.theme.WallpaperScaleMode"
content = content.replace("import com.quantummpv.app.ui.theme.AppTheme", f"import com.quantummpv.app.ui.theme.AppTheme\n{import_lines}")

pref_lines = """  companion object {
    const val CUSTOM_WALLPAPER_URI_KEY = "custom_wallpaper_uri"
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
  val customWallpaperAlpha = preferenceStore.getFloat("custom_wallpaper_alpha", 1f)"""

content = content.replace('val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.System)\n  val appTheme = preferenceStore.getEnum("app_theme", AppTheme.Dynamic)', pref_lines)

init_lines = """    if (selectedCustomThemeName.get().isBlank()) {
      CustomThemeDefinition.parse(customTheme.get())?.let { legacyTheme ->
        selectedCustomThemeName.set(legacyTheme.name)
      }
    }

    if (!castButtonMigrationComplete.get()) {"""

content = content.replace("if (!castButtonMigrationComplete.get()) {", init_lines)

with open("app/src/main/java/com/quantummpv/app/preferences/AppearancePreferences.kt", "w") as f:
    f.write(content)
