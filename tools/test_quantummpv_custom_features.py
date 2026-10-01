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
    appearance_model = read("app/src/main/java/com/quantummpv/app/preferences/AppearancePreferences.kt")
    appearance_screen = read("app/src/main/java/com/quantummpv/app/ui/preferences/AppearancePreferencesScreen.kt")
    main_screen = read("app/src/main/java/com/quantummpv/app/ui/browser/MainScreen.kt")
    strings = read("app/src/main/res/values/strings.xml")

    require(r'glassBottomNavigation\s*=\s*preferenceStore\.getBoolean\("glass_bottom_navigation",\s*false\)', appearance_model,
            "persisted Liquid Glass bottom-navigation preference")
    require(
        r'SwitchPreference\(\s*modifier\s*=\s*Modifier\.settingsSearchTarget\(R\.string\.pref_appearance_glass_navigation_title\),\s*value\s*=\s*glassBottomNavigation,\s*onValueChange\s*=\s*preferences\.glassBottomNavigation::set',
        appearance_screen,
        "searchable, persisted Liquid Glass toggle in Appearance settings",
    )
    require(r'glass\s*=\s*glassBottomNavigation', main_screen, "Liquid Glass preference reaches the navigation renderer")
    require(r'<string name="pref_appearance_glass_navigation_title">Liquid Glass bottom navigation</string>', strings,
            "visible Liquid Glass settings label")
    print("PASS: Liquid Glass appearance setting remains persisted, rendered, and connected to navigation.")

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
