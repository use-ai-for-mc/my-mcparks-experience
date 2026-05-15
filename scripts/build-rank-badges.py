#!/usr/bin/env python3
"""
Render rank "pill" badge PNGs for MCParks chat using the Monocraft pixel
TTF (https://github.com/IdreesInc/Monocraft, OFL). Monocraft is a
monospaced font designed to mirror Minecraft's default chat font, so
badge text reads with the same glyph shapes as the surrounding chat
instead of Tiny5's cramped 5-pixel forms (where letters like V and M
collapsed into ambiguous blobs).

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

TTF_PATH  = REPO_ROOT / "scripts/fonts/Monocraft.ttf"
TTF_SIZE  = 7           # Monocraft at 7pt: monospaced 5-px advance, 6-px caps
GLYPH_H   = 6           # upper-case cap height
PAD_X     = 2           # horizontal padding inside pill
PAD_Y     = 1           # vertical padding above/below glyph row
RADIUS    = 1           # rounded-corner radius (1 = clip 1 pixel per corner)
PILL_HEIGHT = GLYPH_H + PAD_Y * 2   # = 8
# Glyph top sits 1 row below the y passed to dc.text() at this size; to land
# glyph top at row PAD_Y we pass y = PAD_Y - 1.
FONT_TOP_BEARING = 1

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
    ("AP",                  "#FF5555", "#FFFFFF"),
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


LETTER_GAP = 0  # Monocraft is monospaced with a built-in trailing column,
                # so adding our own gap doubled the spacing visually
                # ("CAS T MEMBER" instead of "CAST MEMBER").


def _measure_glyph(font, ch: str) -> tuple[int, int]:
    """Returns (advance, x_offset) for a single glyph with AA off.

    Advance is the font-native horizontal advance (textlength), NOT the
    inked bbox width — for letters with a negative left bearing (T, Y in
    Monocraft) the bbox is one pixel wider than the cell, which would
    otherwise add a stray gap after every such glyph.
    """
    probe = Image.new("RGBA", (1, 1))
    dc = ImageDraw.Draw(probe)
    dc.fontmode = "1"
    b = dc.textbbox((0, 0), ch, font=font)
    advance = int(round(dc.textlength(ch, font=font)))
    return (advance, b[0])


def render_pill(text: str, fill: str, text_color: str, out_path: Path) -> int:
    # All-uppercase so mixed-case lowercase letters (with their busier
    # ascender/descender/curve geometry) don't clash with the clean
    # uppercase-only look of tags like AP / DVC.
    text = text.upper()
    font = ImageFont.truetype(str(TTF_PATH), TTF_SIZE)

    # Render the whole string in one PIL call to a tight crop, then trim
    # leading/trailing transparent columns so the pill sits flush around
    # the inked region. Drawing per-glyph and computing widths from each
    # glyph's individual bbox produced uneven inter-letter gaps because
    # Monocraft's per-glyph side bearings vary.
    text_rgba = hex_to_rgba(text_color)
    over_w = max(96, len(text) * (TTF_SIZE + 4)) + PAD_X * 4
    over_h = PILL_HEIGHT
    over = Image.new("RGBA", (over_w, over_h), (0, 0, 0, 0))
    odc = ImageDraw.Draw(over)
    odc.fontmode = "1"
    odc.text((PAD_X, PAD_Y - FONT_TOP_BEARING), text, font=font, fill=text_rgba)

    # Find tight x-range of inked columns so we can crop leading/trailing
    # transparent columns from the font's side bearings.
    px = over.load()
    left = None
    right = None
    for x in range(over_w):
        for y in range(over_h):
            if px[x, y][3] != 0:
                left = x if left is None else left
                right = x
                break
    if left is None:
        left = right = PAD_X
    text_left = max(0, left - PAD_X)
    text_right = min(over_w - 1, right + PAD_X)
    pill_w = text_right - text_left + 1
    pill_h = PILL_HEIGHT

    fill_rgba = hex_to_rgba(fill)

    # Pill background: 1-bit mask with corner pixels knocked out for soft look.
    mask = Image.new("L", (pill_w, pill_h), 0)
    mdc = ImageDraw.Draw(mask)
    mdc.rectangle([0, 0, pill_w - 1, pill_h - 1], fill=255)
    if RADIUS >= 1:
        for (cx, cy) in ((0, 0), (pill_w - 1, 0),
                         (0, pill_h - 1), (pill_w - 1, pill_h - 1)):
            mask.putpixel((cx, cy), 0)

    img = Image.new("RGBA", (pill_w, pill_h), (0, 0, 0, 0))
    fill_bg = Image.new("RGBA", (pill_w, pill_h), fill_rgba)
    img.paste(fill_bg, (0, 0), mask)
    # Composite the cropped text on top.
    text_crop = over.crop((text_left, 0, text_right + 1, over_h))
    img.alpha_composite(text_crop, (0, 0))

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
    print(f"Pill height: {PILL_HEIGHT}px, glyph font: {TTF_PATH.name} @ {TTF_SIZE}pt (no AA)")


if __name__ == "__main__":
    main()
