#!/usr/bin/env python3
"""Guard QuantumMPV-specific UI and player features during upstream integrations."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.is_file():
        raise AssertionError(f"Required feature file is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require(pattern: str, text: str, description: str) -> None:
    if not re.search(pattern, text, flags=re.MULTILINE | re.DOTALL):
        raise AssertionError(f"Missing protected feature behavior: {description}")


def main() -> int:
    """Check protected UI and player features, returning zero or raising AssertionError."""
    appearance_model = read("app/src/main/java/com/quantummpv/app/preferences/AppearancePreferences.kt")
    design_tokens = read("app/src/main/java/com/quantummpv/app/ui/theme/DesignTokens.kt")
    appearance_screen = read("app/src/main/java/com/quantummpv/app/ui/preferences/AppearancePreferencesScreen.kt")
    searchable_preferences = read("app/src/main/java/com/quantummpv/app/ui/preferences/SearchablePreference.kt")
    search_navigation = read("app/src/main/java/com/quantummpv/app/ui/preferences/SettingsSearchNavigation.kt")
    main_screen = read("app/src/main/java/com/quantummpv/app/ui/browser/MainScreen.kt")
    video_card = read("app/src/main/java/com/quantummpv/app/ui/browser/cards/VideoCard.kt")
    glass_components = read("app/src/main/java/com/quantummpv/app/ui/theme/GlassComponents.kt")
    audio_properties_sheet = read("app/src/main/java/com/quantummpv/app/ui/player/controls/components/sheets/AudioPropertiesSheet.kt")
    equalizer_sheet = read("app/src/main/java/com/quantummpv/app/ui/player/controls/components/sheets/EqualizerSheet.kt")
    strings = read("app/src/main/res/values/strings.xml")

    require(
        r'val surfaceStyle = preferenceStore\.getEnum\("surface_style",\s*SurfaceStyle\.LIQUID_GLASS\)',
        appearance_model,
        "persisted global Liquid Glass default",
    )
    require(r'if \(dynamicTintMode\.get\(\) != DynamicTintMode\.OFF && dynamicTintMode\.get\(\) != DynamicTintMode\.THEME\) \{\s*dynamicTintMode\.set\(DynamicTintMode\.OFF\)',
            appearance_model, "legacy unsupported tint modes are normalized before rendering")
    require(
        r'dynamicTint = storedSurfaceStyle\.effectiveDynamicTint\(dynamicTintMode\.get\(\)\),\s*'
        r'edgeHighlight = storedSurfaceStyle\.effectiveEdgeHighlight\(edgeHighlightMode\.get\(\)\)',
        appearance_model,
        "preference-based glass config suppresses legacy Cinema effects before migration",
    )
    require(
        r'fun effectiveDynamicTint\(mode: DynamicTintMode\): DynamicTintMode =\s*'
        r'if \(this == CINEMA\) \{\s*DynamicTintMode\.OFF\s*\} else \{\s*'
        r'mode\.takeIf \{ it == DynamicTintMode\.OFF \|\| it == DynamicTintMode\.THEME \}\s*'
        r'\?: DynamicTintMode\.OFF\s*\}',
        design_tokens,
        "Cinema and unsupported tint modes remain suppressed before migration",
    )
    require(
        r'fun effectiveEdgeHighlight\(mode: EdgeHighlightMode\): EdgeHighlightMode =\s*'
        r'if \(this == CINEMA\) EdgeHighlightMode\.OFF else mode',
        design_tokens,
        "Cinema suppresses restored edge highlights before migration",
    )
    require(
        r'dynamicTint = style\.effectiveDynamicTint\(dynamicTint\),\s*'
        r'edgeHighlight = style\.effectiveEdgeHighlight\(edgeHighlight\)',
        design_tokens,
        "Compose glass configuration applies the Cinema-aware effect filters",
    )
    require(r'legacyGlassBottomNavigation = preferenceStore\.getBoolean\("glass_bottom_navigation",\s*false\)',
            appearance_model, "persisted legacy navigation appearance compatibility")
    require(
        r'val storedSurfaceStyle by preferences\.surfaceStyle\.collectAsState\(\)\s+'
        r'val surfaceStyle = storedSurfaceStyle\.canonicalStyle',
        appearance_screen,
        "surface-style preference shown in Appearance settings",
    )
    require(
        r'val surfaceStyle = LocalGlassConfig\.current\.style\s+val glassBottomNavigation =\s*if \(appearancePreferences\.hasLegacyGlassBottomNavigation && !appearancePreferences\.hasStoredSurfaceStyle\) \{\s*legacyGlassBottomNavigation\s*\} else \{\s*surfaceStyle != SurfaceStyle\.CLASSIC && surfaceStyle != SurfaceStyle\.MINIMAL\s*\}',
        main_screen,
        "new global style takes over after preserving the user's saved legacy navigation style",
    )
    if len(re.findall(r'glass\s*=\s*glassBottomNavigation', main_screen)) != 2:
        raise AssertionError("global/legacy surface style must reach both navigation renderers")
    require(r'anchorItemIndex = 5,[\s\S]{0,250}pref_tree_flatten_depth_title', searchable_preferences,
            "file-browser search results target the shifted card")
    require(r'anchorItemIndex = 7,[\s\S]{0,250}pref_appearance_thumbnail_position_title', searchable_preferences,
            "thumbnail search results target the shifted card")
    require(r'anchorItemIndex = 9,[\s\S]{0,250}pref_nav_music_title', searchable_preferences,
            "navigation search results target the shifted card")
    require(r'anchorItemIndex = 13,[\s\S]{0,250}pref_anim_controls_style_title', searchable_preferences,
            "animation search results target the shifted card")
    require(r'pref_appearance_unlimited_name_lines_title, itemIndex = 5', search_navigation,
            "file-browser fallback anchors use the current list index")
    require(r'pref_appearance_show_video_thumbnails_title, itemIndex = 7', search_navigation,
            "thumbnail fallback anchors use the current list index")
    require(r'titleRes = R\.string\.pref_glass_intensity_title,\s*keywords', searchable_preferences,
            "glass opacity search avoids a formatted summary without arguments")
    require(r'LaunchedEffect\(dynamicTintMode\) \{\s*if \(dynamicTintMode != supportedDynamicTintMode\) \{\s*preferences\.dynamicTintMode\.set\(supportedDynamicTintMode\)',
            appearance_screen, "late-imported tint modes are normalized in the Appearance selector")
    require(r'value = supportedDynamicTintMode,[\s\S]{0,100}values = listOf\(DynamicTintMode\.OFF, DynamicTintMode\.THEME\)', appearance_screen,
            "the tint selector only exposes implemented, normalized tint modes")
    require(r'<string name="pref_glass_surface_style_title">Surface style</string>', strings,
            "visible surface-style settings label")
    print("PASS: global surface appearance remains visible and connected to navigation.")

    require(r'val useGlassCardSurface = surfaceStyle != SurfaceStyle\.CLASSIC && surfaceStyle != SurfaceStyle\.MINIMAL',
            video_card, "glass card surfaces are enabled only for glass-capable styles")
    require(r'if \(useGlassCardSurface && !isSelected\)\s*\{\s*GlassComponents\.GlassMediaCardSurface\([\s\S]*?shape = cardShape',
            video_card, "unselected glass-style cards use the shared glass surface with the app card shape")
    require(r'if \(isSelected\)\s*\{[\s\S]{0,150}tertiaryContainer', video_card,
            "selected cards retain their existing selection color instead of using the glass surface")
    require(r'if \(appTheme == AppTheme\.Aurora && !isSelected\)', video_card,
            "every unselected Aurora card keeps its gradient independently of glass style")
    require(
        r'fun GlassMediaCardSurface\([\s\S]*?shape: Shape = [^\n]+[\s\S]*?GlassContainer\(\s*modifier = modifier,\s*shape = shape',
        glass_components,
        "media-card glass surface forwards the caller-provided shape",
    )
    require(r'modifier\s*\.clip\(shape\)', glass_components, "glass clipping respects the caller-provided shape")
    require(r'Modifier\.hazeGlass\([\s\S]*?input = HazeInput\.Backdrop\(hazeState\)', glass_components,
            "glass surfaces render against the shared captured backdrop")
    require(r'effectiveStyle\.supportsEdgeHighlight\) config\.edgeHighlight else EdgeHighlightMode\.OFF',
            glass_components, "Cinema and unsupported styles suppress glass edge highlights")
    require(r'when \(effectiveEdgeHighlightMode\)', glass_components,
            "surface borders follow the capability-filtered edge-highlight mode")
    for sheet in (audio_properties_sheet, equalizer_sheet):
        require(r'contentWindowInsets = \{[\s\S]*?WindowInsetsSides\.Top \+ WindowInsetsSides\.Horizontal', sheet,
                "modal sheet leaves bottom inset available to its glass background")
        require(r'windowInsetsPadding\(WindowInsets\.navigationBars\.only\(WindowInsetsSides\.Bottom\)\)', sheet,
                "glass sheet background paints behind the navigation-bar inset")
    print("PASS: VideoCard glass rendering preserves selection, Classic/Minimal fallback, Aurora gradients, and card shape.")

    subtitle_installer = read("app/src/main/java/com/quantummpv/app/ui/player/SubtitleFontInstaller.kt")
    mpv_view = read("app/src/main/java/com/quantummpv/app/ui/player/MPVView.kt")
    native_checks = read("tools/test_native_artifacts.py")
    require(r'FONT_SHA256\s*=\s*"[0-9a-f]{64}"', subtitle_installer, "pinned Unicode fallback font checksum")
    require(r'SubtitleFontInstaller\.install\(context\.applicationContext\)', mpv_view,
            "Unicode fallback installed before mpv font discovery")
    require(r'GoNotoCurrent-Regular\.ttf', native_checks, "subtitle-font artifact regression test")
    print("PASS: multilingual subtitle fallback remains pinned, installed, and checked by CI.")

    require(r'forceRightToLeftSubtitles\s*=\s*preferenceStore\.getBoolean\("sub_force_rtl",\s*false\)',
            read("app/src/main/java/com/quantummpv/app/preferences/SubtitlesPreferences.kt"),
            "persisted RTL bidi-compatibility subtitle setting")
    require(r'sub-vsfilter-bidi-compat', mpv_view, "mpv bidi compatibility option remains wired")
    require(r'player_sheets_sub_force_rtl_title', strings, "RTL subtitle compatibility label remains available")
    print("PASS: RTL bidi compatibility remains available for right-to-left/script edge cases.")

    osd_font = read("app/src/main/java/com/quantummpv/app/ui/player/MpvOsdFont.kt")
    require(r'MpvOsdFont\.ensureInstalled\(context\.applicationContext\)', mpv_view,
            "bundled OSD font is installed before player initialization")
    require(r'configuredDirectory\s*=\s*configuredFontsDirectory[\s\S]{0,500}fileName\s*=\s*MpvOsdFont\.FONT_FILE_NAME',
            mpv_view, "OSD font is mirrored into mpv.conf-selected font directories")
    require(r'FONT_SHA256\s*=\s*"[0-9a-f]{64}"', osd_font, "bundled OSD font content verification")
    require(r'"osd-font"', read("app/src/main/java/com/quantummpv/app/preferences/MpvConfigOverride.kt"),
            "mpv.conf remains authoritative for OSD font configuration")
    print("PASS: OSD font choice is installed with integrity verification and respects mpv.conf ownership.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
