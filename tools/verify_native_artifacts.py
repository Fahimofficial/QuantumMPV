#!/usr/bin/env python3
"""Fetch pinned mpvlib AARs and verify their provenance and final APK contents."""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "native-artifacts.json"
LIBS = ROOT / "app" / "libs"
ABI_DIRS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}
REQUIRED_NATIVE = {"libmpv.so", "libplayer.so", "libavcodec.so", "libavformat.so", "libavutil.so"}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_manifest() -> dict:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1 or set(data.get("artifacts", {})) != {"standard", "noVulkan", "fongmi"}:
        raise SystemExit("native-artifacts.json has an unsupported or incomplete schema")
    return data


def fetch_artifacts(manifest: dict) -> None:
    LIBS.mkdir(parents=True, exist_ok=True)
    for flavor, spec in manifest["artifacts"].items():
        destination = LIBS / spec["file"]
        with tempfile.NamedTemporaryFile(dir=LIBS, delete=False) as temp:
            temporary = Path(temp.name)
        try:
            print(f"Fetching pinned {flavor} artifact {spec['download']}")
            urllib.request.urlretrieve(spec["download"], temporary)
            actual = sha256(temporary)
            if actual != spec["sha256"]:
                raise SystemExit(f"SHA-256 mismatch for {flavor}: expected {spec['sha256']}, got {actual}")
            temporary.replace(destination)
        finally:
            temporary.unlink(missing_ok=True)


def verify_aars(manifest: dict) -> None:
    for flavor, spec in manifest["artifacts"].items():
        path = LIBS / spec["file"]
        if not path.is_file():
            raise SystemExit(f"Missing {path}; run tools/verify_native_artifacts.py --download")
        actual = sha256(path)
        if actual != spec["sha256"]:
            raise SystemExit(f"AAR checksum mismatch for {flavor}: expected {spec['sha256']}, got {actual}")
        with zipfile.ZipFile(path) as aar:
            names = set(aar.namelist())
            actual_abis = {name.split("/")[1] for name in names if name.startswith("jni/") and name.count("/") == 2}
            if actual_abis != ABI_DIRS:
                raise SystemExit(f"{flavor} AAR ABI set {sorted(actual_abis)} != {sorted(ABI_DIRS)}")
            for abi in ABI_DIRS:
                for library in REQUIRED_NATIVE:
                    if f"jni/{abi}/{library}" not in names:
                        raise SystemExit(f"{flavor} AAR is missing jni/{abi}/{library}")
        print(f"PASS AAR {flavor}: {spec['artifact_revision']} {actual}")


def expected_flavor(apk: Path) -> str:
    name = apk.name.lower()
    if "fongmi" in name:
        return "fongmi"
    if "novulkan" in name or "no-vulkan" in name:
        return "noVulkan"
    return "standard"


def verify_apk(manifest: dict, apk: Path) -> None:
    if not apk.is_file():
        raise SystemExit(f"Missing APK: {apk}")
    flavor = expected_flavor(apk)
    spec = manifest["artifacts"][flavor]
    with zipfile.ZipFile(apk) as package:
        names = set(package.namelist())
        metadata_name = "assets/native-build-metadata.properties"
        if metadata_name not in names:
            raise SystemExit(f"{apk}: missing {metadata_name}")
        metadata = package.read(metadata_name).decode("utf-8")
        required_metadata = {
            "artifact_revision": spec["artifact_revision"],
            "source_revision": spec["source_revision"],
            "mpv_revision": spec["mpv_revision"],
            "ffmpeg_revision": spec["ffmpeg_revision"],
            "yt_dlp_revision": spec["yt_dlp_revision"],
            "quickjs_revision": spec["quickjs_revision"],
        }
        for key, value in required_metadata.items():
            if f"{flavor}.{key}={value}" not in metadata:
                raise SystemExit(f"{apk}: metadata does not prove {flavor}.{key}={value}")
        apk_abis = {name.split("/")[1] for name in names if name.startswith("lib/") and name.count("/") >= 2}
        if not apk_abis or not apk_abis <= ABI_DIRS:
            raise SystemExit(f"{apk}: unexpected native ABI entries {sorted(apk_abis)}")
        aar_path = LIBS / spec["file"]
        with zipfile.ZipFile(aar_path) as aar:
            for abi in apk_abis:
                for library in REQUIRED_NATIVE:
                    apk_bytes = package.read(f"lib/{abi}/{library}")
                    aar_bytes = aar.read(f"jni/{abi}/{library}")
                    if hashlib.sha256(apk_bytes).digest() != hashlib.sha256(aar_bytes).digest():
                        raise SystemExit(f"{apk}: {abi}/{library} differs from pinned {spec['file']}")
        print(f"PASS APK {apk}: {flavor} {spec['artifact_revision']} ABIs={','.join(sorted(apk_abis))}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true", help="download exact manifest artifacts")
    parser.add_argument("--apk", action="append", type=Path, help="verify a final APK; may be repeated")
    args = parser.parse_args()
    manifest = load_manifest()
    if args.download:
        fetch_artifacts(manifest)
    verify_aars(manifest)
    for apk in args.apk or []:
        verify_apk(manifest, apk if apk.is_absolute() else ROOT / apk)


if __name__ == "__main__":
    try:
        main()
    except (OSError, urllib.error.URLError, zipfile.BadZipFile) as error:
        print(f"native artifact verification failed: {error}", file=sys.stderr)
        sys.exit(1)
