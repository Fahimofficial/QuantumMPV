#!/usr/bin/env python3
"""Fail CI if upstream synchronization removes QuantumMPV-specific safeguards."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def source_contains(term: str) -> bool:
    return any(term in path.read_text(encoding="utf-8") for path in (ROOT / "app/src/main/java").rglob("*.kt"))


build = read("app/build.gradle.kts")
manifest = read("app/src/main/AndroidManifest.xml")
updater = read("app/src/main/java/com/quantummpv/app/domain/update/UpdateManager.kt")
release = read(".github/workflows/release.yml")
prepare = read("tools/prepare_release.py")
jellyfin_client = read("app/src/main/java/com/quantummpv/app/data/jellyfin/JellyfinClient.kt")
jellyfin_settings = read("app/src/main/java/com/quantummpv/app/ui/preferences/MediaServersPreferencesScreen.kt")
strings = read("app/src/main/res/values/strings.xml")

checks = {
    "QuantumMPV application id": 'applicationId = "com.quantummpv.app"' in build,
    "QuantumMPV app label": 'android:label="@string/app_name"' in manifest and '<string name="app_name"' in strings and '>QuantumMPV</string>' in strings,
    "QuantumMPV updater assets": 'asset.name.startsWith("QuantumMPV-"' in updater,
    "QuantumMPV stable endpoint": 'repos/Fahimofficial/QuantumMPV/releases/latest' in updater,
    "no upstream asset prefix in updater": 'startsWith("mpvRx-"' not in updater,
    "v1 release guard": 'version_tag" != v1.*' in release and 'QUANTUMMPV_MAJOR_VERSION = 1' in prepare,
    "Jellyfin HTTP preference": 'allowJellyfinHttp' in jellyfin_settings and 'media_server_allow_jellyfin_http' in read("app/src/main/java/com/quantummpv/app/preferences/MediaServerPreferences.kt"),
    "Jellyfin HTTP warning": 'pref_jellyfin_http_warning_message' in jellyfin_settings and 'Plaintext Jellyfin connections are disabled' in jellyfin_client,
    "libmpv remains the only playback implementation": not any(
        source_contains(term)
        for term in ("androidx.media3.exoplayer.ExoPlayer", "ExoPlayer.Builder", "SimpleExoPlayer")
    ),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    raise SystemExit("QuantumMPV identity checks failed: " + ", ".join(failed))
