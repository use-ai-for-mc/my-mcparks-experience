#!/usr/bin/env python3
"""
Render rank "pill" badge PNGs for MCParks chat using the Tiny5 pixel TTF
(https://github.com/google/fonts/tree/main/ofl/tiny5, OFL). Tiny5 is
designed to rasterise crisp 1-pixel strokes at 10 pt with anti-aliasing
disabled — what we want because Minecraft samples the resulting bitmap
font nearest-neighbor.

Outputs:
  src/main/resources/assets/my-mcparks-experience/textures/ranks/<slug>.png
  src/main/resources/assets/my-mcparks-experience/font/ranks.json
  src/main/resources/assets/my-mcparks-experience/ranks/ranks.json
"""
import json
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

MOD_NS     = "my-mcparks-experience"
REPO_ROOT  = Path(__file__).resolve().parent.parent
ASSET_ROOT = REPO_ROOT / "src/main/resources/assets" / MOD_NS
TEXTURE_DIR = ASSET_ROOT / "textures/ranks"
FONT_DIR    = ASSET_ROOT / "font"
DATA_DIR    = ASSET_ROOT / "ranks"

TTF_PATH  = REPO_ROOT / "scripts/fonts/Tiny5-Regular.ttf"
TTF_SIZE  = 10          # Tiny5's native size — renders 5-to-6-px-tall glyphs with 1-px strokes
GLYPH_H   = 6           # upper-case ascender height (Tiny5 measures 6 from baseline)
PAD_X     = 2           # horizontal padding inside pill
PAD_Y     = 1           # vertical padding above/below glyph row
RADIUS    = 1           # rounded-corner radius (1 = clip 1 pixel per corner)
PILL_HEIGHT = GLYPH_H + PAD_Y * 2   # = 8
# Tiny5 uses a top-left anchor: glyph rendered pixels start 3 rows below the
# y-coordinate passed to dc.text(). To land glyph top at row PAD_Y, we pass
# y = PAD_Y - 3.
TINY5_TOP_BEARING = 3

# Sourced from actual MCParks chat logs (59 log files, 3,839 chat lines).
RANKS = [
    ("Executive",           "#8A2BE2", "#FFFFFF"),
    ("Imagineer",           "#FF69B4", "#FFFFFF"),
    ("Technician",          "#00CED1", "#0B1020"),
    ("Guest Relations",     "#FF6A00", "#FFFFFF"),
    ("Parks Experience",    "#FFB300", "#0B1020"),
    ("Cast Member",         "#C62828", "#FFFFFF"),
    ("Lead",                "#CD7F32", "#FFFFFF"),
    ("Earning My Ears",     "#4C6EF5", "#FFFFFF"),
    ("Club 33",             "#DAA520", "#0B1020"),
    ("AP",                  "#1E90FF", "#FFFFFF"),
    ("DVC",                 "#2E8B57", "#FFFFFF"),
    ("D23",                 "#9E9E9E", "#0B1020"),
    ("Guest",               "#707070", "#FFFFFF"),
    ("Retired",             "#546E7A", "#FFFFFF"),
    ("Resistance",          "#B71C1C", "#FFFFFF"),
    ("First Order Officer", "#212121", "#FFFFFF"),
]


def slugify(name: str) -> str:
    s = re.sub(r"[^a-zA-Z0-9]+", "_", name).strip("_").lower()
    return s or "rank"


def hex_to_rgba(h: str) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


LETTER_GAP = 1  # blank column between adjacent letters


def _measure_glyph(font, ch: str) -> tuple[int, int]:
    """Returns (width, x_offset) for a single Tiny5 glyph with AA off."""
    probe = Image.new("RGBA", (1, 1))
    dc = ImageDraw.Draw(probe)
    dc.fontmode = "1"
    b = dc.textbbox((0, 0), ch, font=font)
    return (b[2] - b[0], b[0])


def render_pill(text: str, fill: str, text_color: str, out_path: Path) -> int:
    # All-uppercase so mixed-case lowercase letters (with their busier
    # ascender/descender/curve geometry) don't clash with the clean
    # uppercase-only look of tags like AP / DVC.
    text = text.upper()
    font = ImageFont.truetype(str(TTF_PATH), TTF_SIZE)

    # Measure each glyph individually so we can draw them with explicit gaps.
    # Tiny5 has zero right-side bearing — without this, strokes of adjacent
    # letters butt into each other.
    widths = []
    for ch in text:
        if ch == " ":
            widths.append(2)  # narrow space
        else:
            w, _ = _measure_glyph(font, ch)
            widths.append(w)

    total_text_w = sum(widths) + LETTER_GAP * max(0, len(text) - 1)
    pill_w = total_text_w + PAD_X * 2
    pill_h = PILL_HEIGHT

    fill_rgba = hex_to_rgba(fill)
    text_rgba = hex_to_rgba(text_color)

    # Pill background: 1-bit mask with corner pixels knocked out for soft look.
    mask = Image.new("L", (pill_w, pill_h), 0)
    mdc2 = ImageDraw.Draw(mask)
    mdc2.rectangle([0, 0, pill_w - 1, pill_h - 1], fill=255)
    if RADIUS >= 1:
        for (cx, cy) in ((0, 0), (pill_w - 1, 0),
                         (0, pill_h - 1), (pill_w - 1, pill_h - 1)):
            mask.putpixel((cx, cy), 0)

    img = Image.new("RGBA", (pill_w, pill_h), (0, 0, 0, 0))
    fill_img = Image.new("RGBA", (pill_w, pill_h), fill_rgba)
    img.paste(fill_img, (0, 0), mask)

    # Draw each glyph at its own cursor position with AA disabled.
    dc = ImageDraw.Draw(img)
    dc.fontmode = "1"
    cursor = PAD_X
    for i, ch in enumerate(text):
        if ch != " ":
            _, x_off = _measure_glyph(font, ch)
            dc.text(
                (cursor - x_off, PAD_Y - TINY5_TOP_BEARING),
                ch, font=font, fill=text_rgba,
            )
        cursor += widths[i] + LETTER_GAP

    img.save(out_path, format="PNG", optimize=True)
    return pill_w


def main():
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    providers = []
    rank_map  = {}
    pua = 0xE800
    for display, fill, text_color in RANKS:
        slug = slugify(display)
        png_path = TEXTURE_DIR / f"{slug}.png"
        width = render_pill(display, fill, text_color, png_path)
        char = chr(pua)
        providers.append({
            "type": "bitmap",
            "file": f"{MOD_NS}:ranks/{slug}.png",
            "ascent": 7,
            "height": PILL_HEIGHT,
            "chars": [char],
        })
        rank_map[display.lower()] = {
            "display": display,
            "char": char,
            "fill": fill,
            "text": text_color,
            "width_px": width,
        }
        pua += 1

    (FONT_DIR / "ranks.json").write_text(json.dumps({"providers": providers}, indent=2))
    (DATA_DIR / "ranks.json").write_text(json.dumps(rank_map, indent=2))
    print(f"Rendered {len(RANKS)} rank badges -> {TEXTURE_DIR}")
    print(f"Pill height: {PILL_HEIGHT}px, glyph font: Tiny5 @ {TTF_SIZE}pt (5-px strokes, no AA)")


if __name__ == "__main__":
    main()
