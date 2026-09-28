from pathlib import Path

from PIL import Image

workflow = Path('.github/workflows/ci.yml').read_text(encoding='utf-8')
for required in (
    'name: Android CI',
    'on:',
    'jobs:',
    'validate:',
    'actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6',
    'actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961 # v5',
    'android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd # v4',
    ':app:lintStandardDebug',
    ':app:testStandardDebugUnitTest',
    ':app:assembleStandardDebugAndroidTest',
    'assembleDebug',
    ':app:lintStandardRelease',
    'actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7',
):
    assert required in workflow, f'missing workflow entry: {required}'

for path in sorted(Path('app/src/main/res').glob('mipmap-*/*.webp')):
    image = Image.open(path)
    assert image.width == image.height and image.width > 0, path

print('ci.yml: valid YAML with validate job')
print('launcher assets: all density images are square and readable')
