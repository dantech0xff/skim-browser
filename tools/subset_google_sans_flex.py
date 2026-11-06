#!/usr/bin/env python3
"""Build the Google Sans Flex file the app ships.

The UI sets only the wght axis, at 400, 500, and 600, with a normal (upright)
style. This pins optical size, width, roundness, and slant to the font's
defaults and keeps Latin, Vietnamese, combining diacritics, and the
punctuation the UI already draws.

Reproduce the committed app font:

    python3 -m pip install 'fonttools>=4.54' 'uharfbuzz>=0.37'
    python3 tools/subset_google_sans_flex.py
    python3 tools/subset_google_sans_flex.py --check

The input is tools/fonts/GoogleSansFlex-full.ttf (SHA-256
a9ac770798857d5e8ac593ced42b22e40a7c08cec98319f92b0eccc2783a77ba),
which is the unmodified Version 5.000 file. The output is
app/src/main/res/font/google_sans_flex.ttf. The OFL 1.1 text stays at
app/licenses/OFL-GoogleSansFlex.txt. No Reserved Font Name is declared, so
the family name is unchanged.
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/fonts/GoogleSansFlex-full.ttf"
OUTPUT = ROOT / "app/src/main/res/font/google_sans_flex.ttf"
SOURCE_SHA256 = "a9ac770798857d5e8ac593ced42b22e40a7c08cec98319f92b0eccc2783a77ba"

# Defaults from the source fvar table. Type.kt does not set these axes.
PINNED_AXES = {"opsz": 18, "wdth": 100, "ROND": 0, "slnt": 0}

# Latin, Vietnamese (precomposed in Latin Extended Additional), combining
# marks, and the punctuation / symbols already in the UI font.
UNICODE_RANGES = [
    (0x0000, 0x024F),
    (0x02B0, 0x02FF),
    (0x0300, 0x036F),
    (0x1E00, 0x1EFF),
    (0x2000, 0x206F),
    (0x20AC, 0x20AC),
    (0x2122, 0x2122),
    (0x2212, 0x2212),
    (0x25CC, 0x25CC),
    (0xFB00, 0xFB04),
]

VIETNAMESE = "Tiếng Việt: Hà Nội, Nguyễn Huệ, đường phố, học sinh đến trường."
# Decomposed: dot-below and acute as combining marks, not precomposed characters.
DECOMPOSED = "Vie\u0323t Nam ho\u0323c sinh \u0111e\u0302\u0301n"
REQUIRED = VIETNAMESE + DECOMPOSED + "AÁÀÃẠĂẮƠỚƯỨĐđ—“”…"


def unicodes() -> list[int]:
    points: list[int] = []
    for start, end in UNICODE_RANGES:
        points.extend(range(start, end + 1))
    return points


def build() -> TTFont:
    digest = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        raise SystemExit(f"source font hash is {digest}, expected {SOURCE_SHA256}")
    font = TTFont(SOURCE)
    options = Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.glyph_names = True
    subsetter = Subsetter(options=options)
    subsetter.populate(unicodes=unicodes())
    subsetter.subset(font)
    instantiateVariableFont(font, PINNED_AXES, inplace=True)
    return font


def axes(font: TTFont) -> list[str]:
    if "fvar" not in font:
        return []
    return [axis.axisTag for axis in font["fvar"].axes]


def missing_required(font: TTFont) -> list[str]:
    cmap = font.getBestCmap() or {}
    missing = []
    for char in dict.fromkeys(REQUIRED):
        if ord(char) not in cmap:
            missing.append(f"U+{ord(char):04X} {char}")
    return missing


def shaping_failures(font_path: Path) -> list[str]:
    try:
        import uharfbuzz as hb
    except ImportError as error:
        raise SystemExit("uharfbuzz is required for --check. pip install uharfbuzz") from error
    blob = hb.Blob.from_file_path(str(font_path))
    face = hb.Face(blob)
    font = hb.Font(face)
    buffer = hb.Buffer()
    buffer.add_str(VIETNAMESE + DECOMPOSED)
    buffer.guess_segment_properties()
    hb.shape(font, buffer)
    infos = buffer.glyph_infos
    failures = []
    for info in infos:
        if info.codepoint == 0:
            failures.append("glyph-0")
    return failures


def check(path: Path) -> None:
    font = TTFont(path)
    found = axes(font)
    if found != ["wght"]:
        raise SystemExit(f"{path} axes are {found}, expected ['wght']")
    missing = missing_required(font)
    if missing:
        raise SystemExit("missing glyphs:\n" + "\n".join(missing))
    failures = shaping_failures(path)
    if failures:
        raise SystemExit(f"Vietnamese text shaped to .notdef: {failures}")
    print(f"{path} has the wght axis and shapes Vietnamese")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the committed app font")
    args = parser.parse_args(argv)
    if args.check:
        check(OUTPUT)
        return 0
    font = build()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUTPUT)
    check(OUTPUT)
    print(f"wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
