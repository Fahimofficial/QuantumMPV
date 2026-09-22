#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parents[1]
for relative in (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values/media3_session_strings.xml",
):
    path = root / relative
    ElementTree.parse(path)
    print(f"PASS: XML {relative}")
for relative in (".github/workflows/static-analysis.yml", "config/detekt/detekt.yml"):
    path = root / relative
    assert path.is_file() and path.stat().st_size > 0, relative
    print(f"PASS: config {relative}")
