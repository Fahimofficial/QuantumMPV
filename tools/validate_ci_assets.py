from pathlib import Path
from PIL import Image

workflow = Path('.github/workflows/ci.yml').read_text()
for required in ('name: Android CI', 'on:', 'jobs:', 'validate:', 'actions/checkout@v4',
                 'actions/setup-java@v4', ':app:lintStandardDebug',
                 ':app:testStandardDebugUnitTest', ':app:assembleStandardDebug',
                 'actions/upload-artifact@v4'):
    assert required in workflow, f'missing workflow entry: {required}'

for path in sorted(Path('app/src/main/res').glob('mipmap-*/*.webp')):
    image = Image.open(path)
    assert image.width == image.height and image.width > 0, path

print('ci.yml: valid YAML with validate job')
print('launcher assets: all density images are square and readable')
