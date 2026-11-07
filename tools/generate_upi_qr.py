#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Generates the styled UPI support QR (docs/assets/support/upi-qr.png) and checks it decodes.

Dark card with slightly rounded, transparent corners, white panel, dot modules, green finder eyes and the UPI ID as a caption.
Runs offline; no QR web service is used.

    pip install "qrcode[pil]" zxing-cpp
    python3 <this script>

The URI has no amount (`am`), so the payer chooses how much to send.
"""
import sys
from pathlib import Path

import qrcode
import zxingcpp
from PIL import Image, ImageDraw, ImageFont

UPI_ID = "SimplyFahim@sbi"
UPI_URI = "upi://pay?pa=SimplyFahim@sbi&pn=Md%20Fahim&cu=INR"
OUT = Path(__file__).resolve().parent.parent / "docs/assets/support/upi-qr.png"

INK = (27, 29, 28)
GREEN = (0, 158, 59)  # dark enough that scanners still read the finder centres as dark
WHITE = (255, 255, 255)
W, H, PAD, BAND = 908, 1024, 62, 180
FONTS = [
    "/usr/share/fonts/google-noto/NotoSans-ExtraBold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
]


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONTS:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    variable = Path("/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf")
    if variable.exists():
        f = ImageFont.truetype(str(variable), size)
        f.set_variation_by_axes([800])
        return f
    return ImageFont.load_default(size)


def render() -> Image.Image:
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=0)
    qr.add_data(UPI_URI)
    qr.make(fit=True)
    m = qr.get_matrix()
    n = len(m)

    s = 4  # supersample for smooth dots and corners
    img = Image.new("RGBA", (W * s, H * s), (0, 0, 0, 0))  # transparent outside the card
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, W * s - 1, H * s - 1), 56 * s, fill=INK)
    panel = W - 2 * PAD
    d.rounded_rectangle((PAD * s, PAD * s, (W - PAD) * s, (PAD + panel) * s), 36 * s, fill=WHITE)

    cell = panel / (n + 5)  # 2.5-module quiet zone on each side
    ox = oy = PAD + 2.5 * cell
    eyes = [(0, 0), (n - 7, 0), (0, n - 7)]

    def in_eye(r: int, c: int) -> bool:
        return any(er <= r < er + 7 and ec <= c < ec + 7 for er, ec in eyes)

    for r in range(n):
        for c in range(n):
            if m[r][c] and not in_eye(r, c):
                cx, cy, rad = (ox + (c + 0.5) * cell) * s, (oy + (r + 0.5) * cell) * s, 0.46 * cell * s
                d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=INK)
    for er, ec in eyes:
        x0, y0 = (ox + ec * cell) * s, (oy + er * cell) * s
        size = 7 * cell * s
        d.rounded_rectangle((x0, y0, x0 + size, y0 + size), 2.2 * cell * s, fill=INK)
        d.rounded_rectangle((x0 + cell * s, y0 + cell * s, x0 + size - cell * s, y0 + size - cell * s),
                            1.4 * cell * s, fill=WHITE)
        cx, cy, rad = x0 + size / 2, y0 + size / 2, 1.55 * cell * s
        d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=GREEN)

    f = font(64 * s)
    d.text((W * s / 2, (H - BAND / 2) * s + 4 * s), UPI_ID, font=f, fill=WHITE, anchor="mm")
    return img.resize((W, H), Image.LANCZOS)


def main() -> int:
    img = render()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, optimize=True)
    saved = Image.alpha_composite(Image.new("RGBA", Image.open(OUT).size, WHITE + (255,)), Image.open(OUT).convert("RGBA")).convert("RGB")
    # Check the saved file at full size and at the sizes the READMEs and websites show it.
    for test in (saved, saved.resize((240, 271), Image.LANCZOS), saved.resize((220, 248), Image.LANCZOS)):
        results = zxingcpp.read_barcodes(test)
        if len(results) != 1 or results[0].text != UPI_URI:
            print(f"Decoded {[r.text for r in results]!r} at {test.size}, expected {UPI_URI!r}", file=sys.stderr)
            return 1
    print(f"{OUT}: {saved.size[0]}x{saved.size[1]}, decodes to {UPI_URI}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
