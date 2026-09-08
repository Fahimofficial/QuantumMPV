from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
NAVY = (10, 16, 35, 255)
CYAN = (49, 220, 226, 255)
MAGENTA = (236, 72, 153, 255)
WHITE = (255, 255, 255, 255)


def draw_mark(size: int, transparent: bool, legacy: bool = False) -> Image.Image:
    scale = 4
    canvas = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0) if transparent else NAVY)
    d = ImageDraw.Draw(canvas)
    s = size * scale
    if legacy:
        margin = int(s * 0.08)
        d.rounded_rectangle((margin, margin, s - margin, s - margin), radius=int(s * 0.22), fill=NAVY)
    cx, cy, radius, width = s / 2, s / 2, s * 0.34, max(4, int(s * 0.105))
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    d.arc(box, start=42, end=322, fill=CYAN, width=width)
    d.line((cx + radius * 0.48, cy + radius * 0.55, cx + radius * 0.94, cy + radius * 1.00), fill=MAGENTA, width=width, joint="curve")
    tri = [(cx - s * 0.095, cy - s * 0.15), (cx - s * 0.095, cy + s * 0.15), (cx + s * 0.17, cy)]
    d.polygon(tri, fill=WHITE)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


for density, size in DENSITIES.items():
    folder = ROOT / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    draw_mark(size * 2, transparent=True).save(folder / "ic_launcher_foreground.webp", "WEBP", lossless=True, quality=100)
    draw_mark(size, transparent=False, legacy=True).save(folder / "ic_launcher.webp", "WEBP", lossless=True, quality=100)
    draw_mark(size, transparent=False, legacy=True).save(folder / "ic_launcher_round.webp", "WEBP", lossless=True, quality=100)

print("Generated QuantumMPV launcher icons for", ", ".join(DENSITIES))
