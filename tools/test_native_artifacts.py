#!/usr/bin/env python3
"""Regression checks for deterministic native artifact packaging and subtitle fonts."""
from hashlib import sha256
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'native-artifacts.json').read_text())
app_gradle = (root / 'app/build.gradle.kts').read_text()
assert 'mpvlib-no-vulkan.aar' in app_gradle
assert 'mpvlib-no-vulkun.aar' not in app_gradle
assert 'agp = "9.4.1"' in (root / 'gradle/libs.versions.toml').read_text()
assert 'gradle-9.6.0-bin.zip' in (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
assert 'org.bouncycastle:bcprov-jdk18on:1.85' in app_gradle
assert 'org.bouncycastle:bcpkix-jdk18on:1.85' in app_gradle
assert 'requested.group == "org.bouncycastle" -> useVersion("1.85")' in (root / 'build.gradle.kts').read_text()
assert 'configurations.classpath' in (root / 'build.gradle.kts').read_text()
assert 'standard.artifact_revision=mpvlib-v1.0.10' in (root / 'app/src/main/assets/native-build-metadata.properties').read_text()
assert (root / 'native-artifacts.properties').read_text() == (root / 'app/src/main/assets/native-build-metadata.properties').read_text()
for flavor, spec in manifest['artifacts'].items():
    assert len(spec['sha256']) == 64
    assert len(spec['source_revision']) == 40
    assert spec['file'].endswith('.aar')
assert not subprocess.check_output(['git', 'ls-files', 'app/libs'], cwd=root, text=True).strip(), 'checked-in AARs bypass the pinned fetch gate'
for workflow in ('build.yml', 'ci.yml', 'pre-release.yml', 'preview.yml', 'release.yml'):
    text = (root / '.github/workflows' / workflow).read_text()
    assert 'verify_native_artifacts.py --download' in text, workflow

font = root / 'app/src/main/assets/subtitle-fonts/GoNotoCurrent-Regular.ttf'
font_license = root / 'app/src/main/assets/subtitle-fonts/OFL.txt'
installer = root / 'app/src/main/java/com/quantummpv/app/ui/player/SubtitleFontInstaller.kt'
player = root / 'app/src/main/java/com/quantummpv/app/ui/player/MPVView.kt'
installer_text = installer.read_text()
player_text = player.read_text()
assert font.is_file(), 'bundled Unicode subtitle fallback font is missing'
assert font.stat().st_size == 14_700_060, 'unexpected bundled subtitle font size'
assert sha256(font.read_bytes()).hexdigest() == '882afbab965608c2d2bc627fd8016b962aa5a6be2d358f9de24a7b5967c5632e', 'bundled subtitle font checksum mismatch'
assert font_license.is_file() and 'SIL OPEN FONT LICENSE Version 1.1' in font_license.read_text()
assert 'ASSET_PATH = "subtitle-fonts/GoNotoCurrent-Regular.ttf"' in installer_text
assert 'SubtitleFontInstaller.install(context.applicationContext)' in player_text
assert 'SHA-256' in installer_text and 'sub-fonts-dir' in player_text
print('native artifact and subtitle font regression checks passed')
