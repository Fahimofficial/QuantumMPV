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

# Keep the decimal encoding monotonic while leaving ample room under Android's
# maximum versionCode. Each component gets a fixed-width slot.
MINOR_PATCH_RADIX = 1_000
MAJOR_RADIX = 1_000_000
MAX_ANDROID_VERSION_CODE = 2_147_483_647


def encode_version_code(major: int, minor: int, patch: int) -> int:
    if major >= MAJOR_RADIX or minor >= MINOR_PATCH_RADIX or patch >= MINOR_PATCH_RADIX:
        raise SystemExit(
            f"version components must each be below {MINOR_PATCH_RADIX}; Android versionCode would overflow",
        )
    release_code = major * MAJOR_RADIX + minor * MINOR_PATCH_RADIX + patch
    if release_code >= MAX_ANDROID_VERSION_CODE // 10_000:
        raise SystemExit("semantic version is too large for the Android versionCode encoding")
    return release_code


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="stable semantic version, for example 2.5.1")
    args = parser.parse_args()
    match = SEMVER_RE.fullmatch(args.version)
    if not match:
        raise SystemExit("version must have the form MAJOR.MINOR.PATCH, for example 2.5.1")

    major, minor, patch = map(int, match.groups())
    release_code = encode_version_code(major, minor, patch)
    text = BUILD_FILE.read_text(encoding="utf-8")
    updated, version_count = VERSION_RE.subn(rf"\g<prefix>{args.version}\g<suffix>", text, count=1)
    updated, code_count = CODE_RE.subn(rf"\g<prefix>{release_code}\g<suffix>", updated, count=1)
    if version_count != 1 or code_count != 1:
        raise SystemExit("could not update exactly one versionName and releaseVersionCode")
    BUILD_FILE.write_text(updated, encoding="utf-8")
    print(f"Prepared QuantumMPV {args.version} with releaseVersionCode {release_code}")


if __name__ == "__main__":
    main()
