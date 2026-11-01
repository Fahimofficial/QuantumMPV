#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Generate the README's UPI support QR code locally and verify it by decoding.

The payload is a UPI deep link without an amount, so the payer chooses how much
to send. Regenerate with:

    pip install "qrcode[pil]" opencv-python-headless
    python3 tools/generate_upi_qr.py

The script exits non-zero if the written PNG does not decode back to the exact
payload.
"""

from __future__ import annotations

import sys
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

import qrcode
from qrcode.constants import ERROR_CORRECT_M

UPI_ID = "SimplyFahim@sbi"
PAYEE_NAME = "SimplyFahim"
CURRENCY = "INR"
# No `am` (amount) parameter: contributions are voluntary and payer-chosen.
PAYLOAD = f"upi://pay?pa={UPI_ID}&pn={PAYEE_NAME}&cu={CURRENCY}"

OUTPUT = Path(__file__).resolve().parent.parent / "docs" / "assets" / "support" / "upi-qr.png"


def build_image():
    qr = qrcode.QRCode(
        error_correction=ERROR_CORRECT_M,
        box_size=16,  # large modules for reliable scanning from screens
        border=4,  # quiet zone required by the QR specification
    )
    qr.add_data(PAYLOAD)
    qr.make(fit=True)
    # Pure black on white gives the highest contrast for camera scanners.
    return qr.make_image(fill_color="black", back_color="white").convert("RGB")


def verify(path: Path) -> None:
    import cv2  # imported lazily so generation works without OpenCV

    image = cv2.imread(str(path))
    if image is None:
        raise SystemExit(f"Could not read {path}")
    decoded, _, _ = cv2.QRCodeDetector().detectAndDecode(image)
    if decoded != PAYLOAD:
        raise SystemExit(f"Decoded payload mismatch: {decoded!r} != {PAYLOAD!r}")

    parts = urlsplit(decoded)
    query = parse_qs(parts.query)
    expected = {"pa": [UPI_ID], "pn": [PAYEE_NAME], "cu": [CURRENCY]}
    if parts.scheme != "upi" or parts.netloc != "pay" or query != expected:
        raise SystemExit(f"Unexpected UPI fields: {parts.scheme}://{parts.netloc} {query}")


def main() -> int:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image = build_image()
    image.save(OUTPUT, optimize=True)
    print(f"Wrote {OUTPUT.relative_to(OUTPUT.parents[3])} ({image.size[0]}x{image.size[1]})")
    try:
        verify(OUTPUT)
    except ImportError:
        print("OpenCV not installed; skipped decode verification", file=sys.stderr)
        return 1
    print(f"Verified payload: {PAYLOAD}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
