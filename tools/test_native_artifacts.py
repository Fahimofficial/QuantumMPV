#!/usr/bin/env python3
"""Regression checks for deterministic native artifact packaging."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'native-artifacts.json').read_text())
app_gradle = (root / 'app/build.gradle.kts').read_text()
assert 'mpvlib-no-vulkan.aar' in app_gradle
assert 'mpvlib-no-vulkun.aar' not in app_gradle
assert 'agp = \"9.4.1\"' in (root / 'gradle/libs.versions.toml').read_text()
assert 'gradle-9.6.0-bin.zip' in (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
assert 'org.bouncycastle:bcprov-jdk18on:1.85' in app_gradle
assert 'org.bouncycastle:bcpkix-jdk18on:1.85' in app_gradle
assert 'requested.group == \"org.bouncycastle\" -> useVersion(\"1.85\")' in (root / 'build.gradle.kts').read_text()
assert 'standard.artifact_revision=mpvlib-v1.0.10' in (root / 'app/src/main/assets/native-build-metadata.properties').read_text()
assert (root / 'native-artifacts.properties').read_text() == (root / 'app/src/main/assets/native-build-metadata.properties').read_text()
for flavor, spec in manifest['artifacts'].items():
    assert len(spec['sha256']) == 64
    assert len(spec['source_revision']) == 40
    assert spec['file'].endswith('.aar')
import subprocess
assert not subprocess.check_output(['git', 'ls-files', 'app/libs'], cwd=root, text=True).strip(), 'checked-in AARs bypass the pinned fetch gate'
for workflow in ('build.yml', 'ci.yml', 'pre-release.yml', 'preview.yml', 'release.yml'):
    text = (root / '.github/workflows' / workflow).read_text()
    assert 'verify_native_artifacts.py --download' in text, workflow
print('native artifact regression checks passed')
