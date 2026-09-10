#!/usr/bin/env python3
"""Update the Android version for the manually dispatched release-preparation workflow."""
from __future__ import annotations

import argparse
import re
from pathlib import Path

BUILD_FILE = Path("app/build.gradle.kts")
VERSION_RE = re.compile(r"^(?P<prefix>\s*versionName\s*=\s*\").*?(?P<suffix>\"\s*)$", re.MULTILINE)
CODE_RE = re.compile(r"^(?P<prefix>\s*val releaseVersionCode\s*=\s*)\d+(?P<suffix>\s*)$", re.MULTILINE)
SEMVER_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="stable semantic version, for example 2.5.1")
    args = parser.parse_args()
    match = SEMVER_RE.fullmatch(args.version)
    if not match:
        raise SystemExit("version must have the form MAJOR.MINOR.PATCH, for example 2.5.1")

    major, minor, patch = map(int, match.groups())
    release_code = major * 100 + minor * 10 + patch
    text = BUILD_FILE.read_text(encoding="utf-8")
    updated, version_count = VERSION_RE.subn(rf"\g<prefix>{args.version}\g<suffix>", text, count=1)
    updated, code_count = CODE_RE.subn(rf"\g<prefix>{release_code}\g<suffix>", updated, count=1)
    if version_count != 1 or code_count != 1:
        raise SystemExit("could not update exactly one versionName and releaseVersionCode")
    BUILD_FILE.write_text(updated, encoding="utf-8")
    print(f"Prepared QuantumMPV {args.version} with releaseVersionCode {release_code}")


if __name__ == "__main__":
    main()
