"""
Generate threshold strips for docs/data-fields.md.

Two swatch rows in the style of the zone palette previews: seven cells per
strip, each filled with the threshold color at a sample speed and labelled
with that speed, using the exact colors and easing from FieldColors.kt.
Target mode fades red -> cell black -> green through the target; range mode
runs red -> orange at min -> green -> orange at max -> red. Grey markers
under the strip name the target / min / max cells.

Outputs land in ``docs/palettes/threshold-legend-{target,range}.svg``.

Usage
-----
uv run scripts/generate_threshold_legends.py
"""

from __future__ import annotations

import math
from pathlib import Path

from palettes import best_text_on_background

# FieldColors.kt
RDYLGN_RED = (0xD7, 0x30, 0x27)
RDYLGN_GREEN = (0x1A, 0x98, 0x50)
DANGER_ORANGE = (0xFF, 0xA7, 0x26)
CELL_BLACK = (0x00, 0x00, 0x00)

CELL_W = 56
CELL_H = 26
HEIGHT = 44
FONT = "-apple-system, system-ui, sans-serif"

TARGET_KPH = 25.0
RANGE_MIN_KPH, RANGE_MAX_KPH = 20.0, 30.0
RANGE_PERCENT = 10.0


def lerp(a: tuple, b: tuple, t: float) -> tuple:
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def hexc(c: tuple) -> str:
    return "#{:02X}{:02X}{:02X}".format(*c)


def target_color(kph: float) -> tuple:
    factor = (kph - TARGET_KPH) / TARGET_KPH * 100.0 / RANGE_PERCENT
    factor = max(-1.0, min(1.0, factor))
    if factor >= 0:
        return lerp(CELL_BLACK, RDYLGN_GREEN, math.sqrt(factor))
    return lerp(CELL_BLACK, RDYLGN_RED, math.sqrt(-factor))


def range_color(kph: float) -> tuple:
    band_below = RANGE_MIN_KPH * RANGE_PERCENT / 100.0
    band_above = RANGE_MAX_KPH * RANGE_PERCENT / 100.0
    if kph < RANGE_MIN_KPH:
        outside = min(1.0, (RANGE_MIN_KPH - kph) / band_below)
        return lerp(DANGER_ORANGE, RDYLGN_RED, math.sqrt(outside))
    if kph > RANGE_MAX_KPH:
        outside = min(1.0, (kph - RANGE_MAX_KPH) / band_above)
        return lerp(DANGER_ORANGE, RDYLGN_RED, math.sqrt(outside))
    proximity = max(
        0.0,
        1.0 - (kph - RANGE_MIN_KPH) / band_below,
        1.0 - (RANGE_MAX_KPH - kph) / band_above,
    )
    return lerp(RDYLGN_GREEN, DANGER_ORANGE, math.sqrt(proximity))


def strip_svg(kphs: list[float], color_at, markers: dict[int, str]) -> str:
    width = CELL_W * len(kphs)
    cells = []
    for i, kph in enumerate(kphs):
        fill = hexc(color_at(kph))
        text = best_text_on_background(fill)
        cells.append(
            f'<rect x="{i * CELL_W}" y="0" width="{CELL_W}" height="{CELL_H}" fill="{fill}" />'
            f'<text x="{i * CELL_W + CELL_W / 2:.1f}" y="{CELL_H / 2 + 4:.1f}" '
            f'font-family="{FONT}" font-size="13" font-weight="600" '
            f'fill="{text}" text-anchor="middle">{kph:.1f}</text>'
        )
    for i, label in markers.items():
        cells.append(
            f'<text x="{i * CELL_W + CELL_W / 2:.1f}" y="{HEIGHT - 3}" '
            f'font-family="{FONT}" font-size="12" '
            f'fill="#808080" text-anchor="middle">{label}</text>'
        )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{HEIGHT}" '
        f'viewBox="0 0 {width} {HEIGHT}">' + "".join(cells) + "</svg>\n"
    )


def main() -> None:
    out = Path(__file__).resolve().parent.parent / "docs" / "palettes"
    out.mkdir(exist_ok=True)
    (out / "threshold-legend-target.svg").write_text(
        strip_svg(
            [22.0, 23.5, 24.5, 25.0, 25.5, 26.5, 28.0],
            target_color,
            markers={3: "target"},
        )
    )
    (out / "threshold-legend-range.svg").write_text(
        strip_svg(
            [18.5, 20.0, 21.5, 25.0, 28.5, 30.0, 31.5],
            range_color,
            markers={1: "min", 5: "max"},
        )
    )
    print("wrote threshold-legend-target.svg, threshold-legend-range.svg")


if __name__ == "__main__":
    main()
