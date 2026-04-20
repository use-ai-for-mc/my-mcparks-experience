#!/usr/bin/env python3
"""
Twemoji asset pipeline (Phase 1 + Phase 2 combined).

Inputs:
  /tmp/twemoji/assets/72x72/*.png   (from `git clone https://github.com/jdecked/twemoji.git /tmp/twemoji`)
  /tmp/gemoji.json                  (from https://raw.githubusercontent.com/github/gemoji/master/db/emoji.json)

Outputs (under src/main/resources/assets/my-mcparks-experience/):
  textures/emoji/<hex>.png          (filtered, resized to SIZE x SIZE)
  font/emoji.json                   (bitmap font provider registered as my-mcparks-experience:emoji)
  twemoji/shortcodes.json           (alias -> PUA char)
  twemoji/unicode.json              (emoji char -> PUA char)

Skin-tone variants (1f3fb..1f3ff, and any sequence containing them) are filtered out.
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: pip install Pillow")

SRC_PNGS  = Path("/tmp/twemoji/assets/72x72")
GEMOJI    = Path("/tmp/gemoji.json")
MOD_NS    = "my-mcparks-experience"
REPO_ROOT = Path(__file__).resolve().parent.parent
ASSET_ROOT = REPO_ROOT / "src/main/resources/assets" / MOD_NS
EMOJI_DIR  = ASSET_ROOT / "textures/emoji"
FONT_DIR   = ASSET_ROOT / "font"
DATA_DIR   = ASSET_ROOT / "twemoji"
SIZE = 18
SKIN_TONES = {"1f3fb", "1f3fc", "1f3fd", "1f3fe", "1f3ff"}


def is_skin_variant(stem: str) -> bool:
    return any(t in stem.split("-") for t in SKIN_TONES)


def emoji_to_filename(emoji: str) -> str:
    # Twemoji filenames: hex codepoints joined by '-', with U+FE0F typically stripped.
    cps = [f"{ord(c):x}" for c in emoji]
    filtered = [cp for cp in cps if cp != "fe0f"] or cps
    return "-".join(filtered)


def phase1_resize():
    if not SRC_PNGS.is_dir():
        sys.exit(f"Missing {SRC_PNGS}. Run: git clone --depth 1 https://github.com/jdecked/twemoji.git /tmp/twemoji")

    EMOJI_DIR.mkdir(parents=True, exist_ok=True)

    # If we already ran once, skip resizing — it's the slow part.
    existing = list(EMOJI_DIR.glob("*.png"))
    if len(existing) > 1000:
        print(f"[phase1] {len(existing)} PNGs already present under {EMOJI_DIR} — skipping resize")
    else:
        count = 0
        for src in sorted(SRC_PNGS.glob("*.png")):
            if is_skin_variant(src.stem):
                continue
            dest = EMOJI_DIR / src.name
            with Image.open(src) as img:
                img = img.convert("RGBA")
                img = img.resize((SIZE, SIZE), Image.LANCZOS)
                img.save(dest, format="PNG", optimize=True)
            count += 1
        print(f"[phase1] wrote {count} emoji to {EMOJI_DIR}")
    phase1b_quantize()


def phase1b_quantize():
    """Run pngquant over the resized PNGs for ~6x size reduction.

    Each emoji uses very few distinct colours once downsized to 18×18, so
    palette quantization with a conservative quality range is visually
    lossless here. No-op (with a warning) if pngquant isn't on PATH.
    """
    if not shutil.which("pngquant"):
        print("[phase1b] pngquant not found on PATH — skipping quantization")
        return

    pngs = sorted(EMOJI_DIR.glob("*.png"))
    if not pngs:
        return

    before = sum(p.stat().st_size for p in pngs)
    # Batch in chunks to keep argv under ARG_MAX.
    CHUNK = 500
    for i in range(0, len(pngs), CHUNK):
        batch = [str(p) for p in pngs[i:i + CHUNK]]
        subprocess.run([
            "pngquant",
            "--quality=65-90",
            "--skip-if-larger",
            "--strip",
            "--ext", ".png",
            "--force",
            "--speed", "1",
            *batch,
        ], check=False)
    after = sum(p.stat().st_size for p in pngs)
    print(f"[phase1b] pngquant: {before/1024:.1f} KiB -> {after/1024:.1f} KiB "
          f"({100 * (before-after) / before:.1f}% saved)")


def phase2_resources():
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    available = {p.stem for p in EMOJI_DIR.glob("*.png") if not is_skin_variant(p.stem)}
    print(f"[phase2] available emoji PNGs: {len(available)}")

    with open(GEMOJI) as f:
        gemoji = json.load(f)

    shortcodes = {}
    unicode_map = {}   # filename -> pua int
    providers = []
    used_filenames = set()
    pua = 0xE000

    for entry in gemoji:
        emoji_char = entry["emoji"]
        filename = emoji_to_filename(emoji_char)
        if filename not in available:
            alt = "-".join(f"{ord(c):x}" for c in emoji_char)
            if alt in available:
                filename = alt
            else:
                continue
        if is_skin_variant(filename):
            continue

        if filename not in used_filenames:
            used_filenames.add(filename)
            providers.append({
                "type": "bitmap",
                "file": f"{MOD_NS}:emoji/{filename}.png",
                "ascent": 7,
                "height": 8,
                "chars": [chr(pua)],
            })
            unicode_map[filename] = pua
            pua += 1
            assert pua < 0xF900, "Ran out of PUA space"

        for alias in entry["aliases"]:
            shortcodes[alias] = unicode_map[filename]

    (FONT_DIR / "emoji.json").write_text(json.dumps({"providers": providers}, indent=2))
    print(f"[phase2] wrote {len(providers)} font providers -> font/emoji.json")

    shortcode_out = {k: chr(v) for k, v in shortcodes.items()}
    (DATA_DIR / "shortcodes.json").write_text(json.dumps(shortcode_out, indent=2))
    print(f"[phase2] wrote {len(shortcode_out)} shortcodes -> twemoji/shortcodes.json")

    unicode_out = {}
    for entry in gemoji:
        fn = emoji_to_filename(entry["emoji"])
        if fn in unicode_map:
            unicode_out[entry["emoji"]] = chr(unicode_map[fn])
    (DATA_DIR / "unicode.json").write_text(json.dumps(unicode_out, indent=2, ensure_ascii=False))
    print(f"[phase2] wrote {len(unicode_out)} unicode mappings -> twemoji/unicode.json")


if __name__ == "__main__":
    phase1_resize()
    phase2_resources()
