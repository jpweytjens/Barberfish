"""
Generate threshold legend SVGs for docs/data-fields.md.

Two strips mirroring the config screen's ThresholdLegend, drawn with the
exact field colors from FieldColors.kt: target mode fades red -> cell
black -> green through the target; range mode runs red -> orange at min ->
green -> orange at max -> red. The sqrt easing of the on-device lerp is
baked into the gradient stops.

Outputs land in ``docs/palettes/threshold-legend-{target,range}.svg``.

Usage
-----
uv run scripts/generate_threshold_legends.py
"""

from __future__ import annotations

import math
from pathlib import Path

# FieldColors.kt
RDYLGN_RED = (0xD7, 0x30, 0x27)
RDYLGN_GREEN = (0x1A, 0x98, 0x50)
DANGER_ORANGE = (0xFF, 0xA7, 0x26)
CELL_BLACK = (0x00, 0x00, 0x00)

WIDTH = 392
BAR_H = 24
HEIGHT = 44
STOPS = 96


def lerp(a: tuple, b: tuple, t: float) -> tuple:
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def hexc(c: tuple) -> str:
    return "#{:02X}{:02X}{:02X}".format(*c)


def target_color(x: float) -> tuple:
    # Target at 0.5; fully saturated beyond +/- 0.4 (the +/- range% band).
    factor = max(-1.0, min(1.0, (x - 0.5) / 0.4))
    if factor >= 0:
        return lerp(CELL_BLACK, RDYLGN_GREEN, math.sqrt(factor))
    return lerp(CELL_BLACK, RDYLGN_RED, math.sqrt(-factor))


RANGE_MIN, RANGE_MAX = 0.22, 0.78
BAND_OUT, BAND_IN = 0.10, 0.16


def range_color(x: float) -> tuple:
    if x < RANGE_MIN:
        outside = min(1.0, (RANGE_MIN - x) / BAND_OUT)
        return lerp(DANGER_ORANGE, RDYLGN_RED, math.sqrt(outside))
    if x > RANGE_MAX:
        outside = min(1.0, (x - RANGE_MAX) / BAND_OUT)
        return lerp(DANGER_ORANGE, RDYLGN_RED, math.sqrt(outside))
    proximity = max(
        0.0,
        1.0 - (x - RANGE_MIN) / BAND_IN,
        1.0 - (RANGE_MAX - x) / BAND_IN,
    )
    return lerp(RDYLGN_GREEN, DANGER_ORANGE, math.sqrt(proximity))


def legend_svg(color_at, markers: list[tuple[float, str]]) -> str:
    grad_id = "g"
    stops = "".join(
        f'<stop offset="{i / (STOPS - 1):.4f}" stop-color="{hexc(color_at(i / (STOPS - 1)))}" />'
        for i in range(STOPS)
    )
    ticks = "".join(
        f'<line x1="{x * WIDTH:.0f}" y1="0" x2="{x * WIDTH:.0f}" y2="{BAR_H + 4}" '
        f'stroke="#808080" stroke-width="1.5" />'
        f'<text x="{x * WIDTH:.0f}" y="{HEIGHT - 3}" '
        f'font-family="-apple-system, system-ui, sans-serif" font-size="12" '
        f'fill="#808080" text-anchor="middle">{label}</text>'
        for x, label in markers
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" '
        f'viewBox="0 0 {WIDTH} {HEIGHT}">'
        f'<defs><linearGradient id="{grad_id}">{stops}</linearGradient></defs>'
        f'<rect x="0" y="0" width="{WIDTH}" height="{BAR_H}" fill="url(#{grad_id})" />'
        f"{ticks}</svg>\n"
    )


def main() -> None:
    out = Path(__file__).resolve().parent.parent / "docs" / "palettes"
    out.mkdir(exist_ok=True)
    (out / "threshold-legend-target.svg").write_text(
        legend_svg(target_color, [(0.5, "target")])
    )
    (out / "threshold-legend-range.svg").write_text(
        legend_svg(range_color, [(RANGE_MIN, "min"), (RANGE_MAX, "max")])
    )
    print("wrote threshold-legend-target.svg, threshold-legend-range.svg")


if __name__ == "__main__":
    main()
