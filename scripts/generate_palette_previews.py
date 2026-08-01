"""
Generate one three-row palette preview SVG per palette for the README.

Row 1 — TEXT mode (day): palette color drawn as text on ``#FFFFFF``, using
        the day-readable variant (``*ColorsReadableLight``).
Row 2 — TEXT mode (night): palette color drawn as text on ``#000000``, using
        the night-readable variant (``*ColorsReadableDark``).
Row 3 — FILL mode: original palette as cell fill with the APCA-picked
        black/white overlay text.

Each row is 26 px tall; the final SVG is 78 px tall. Cached
``*ColorsReadable*`` variants in Kotlin are preferred; missing variants
(typically the light set before it lands in source) are computed on the fly
via ``adjust_for_readability``.

Outputs land in ``docs/palettes/palette-{power,hr,grade}-<slug>.svg``.

Usage
-----
uv run scripts/generate_palette_previews.py
"""

from __future__ import annotations

import math
from pathlib import Path

from palettes import (
    DATAFIELD_BG_DARK,
    DATAFIELD_BG_LIGHT,
    GRADE_BANDS_BY_KOTLIN_NAME,
    HR_ZONE_LABELS,
    PALETTES_BY_KOTLIN_NAME,
    POWER_ZONE_LABELS,
    adjust_for_readability,
    best_text_on_background,
)

# ---------------------------------------------------------------------------
# README palette order — (slug, kotlin_power_name)
# ---------------------------------------------------------------------------

POWER_PALETTE_ORDER: list[tuple[str, str]] = [
    ("karoo", "karooPowerColors"),
    ("wahoo", "wahooPowerColors"),
    ("zwift", "zwiftPowerColors"),
    ("intervals", "intervalsPowerColors"),
    ("hsluv", "hsluvPowerColors"),
]

HR_PALETTE_ORDER: list[tuple[str, str]] = [
    ("karoo", "karooHrColors"),
    ("wahoo", "wahooHrColors"),
    ("zwift", "zwiftHrColors"),
    ("intervals", "intervalsHrColors"),
    ("hsluv", "hsluvHrColors"),
]

# Grade palette readable variants are keyed by *_GRADE_BANDS. None means
# "single variant" (HSLUV is perceptually designed; Turbo, Karoo bands
# resolve via the power palette / their own readable lists post-rename).
GRADE_PALETTE_ORDER: list[tuple[str, str]] = [
    ("barberfish", "BARBERFISH_GRADE_BANDS"),
    ("karoo", "KAROO_GRADE_BANDS"),
    ("wahoo", "WAHOO_GRADE_BANDS"),
    ("garmin", "GARMIN_GRADE_BANDS"),
    ("zwift", "ZWIFT_GRADE_BANDS"),
    ("hsluv", "HSLUV_GRADE_BANDS"),
    ("turbo", "TURBO_GRADE_BANDS"),
]


# ---------------------------------------------------------------------------
# SVG rendering
# ---------------------------------------------------------------------------

CELL_W_DEFAULT = 56
CELL_H = 26
ROW_GAP = 0
H_PADDING = 0
V_PADDING = 0
FONT_FAMILY = "-apple-system, system-ui, sans-serif"
FONT_SIZE = 13
FONT_WEIGHT = 600

GRADE_AXIS_MIN = -15.0
GRADE_AXIS_MAX = 25.0
GRADE_ROW_W = 720  # fixed content width so every grade row aligns across palettes
TICK_H = 18


def _esc(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _cell_width_for(labels: list[str]) -> int:
    """Pick a cell width that fits the widest label at the chosen font size."""
    longest = max((len(label) for label in labels), default=1)
    return max(CELL_W_DEFAULT, int(longest * 7.5) + 16)


def _row_svg(
    y: int,
    cell_w: int,
    bg_per_cell: list[str],
    text_per_cell: list[str],
    labels: list[str],
) -> str:
    parts: list[str] = []
    for i, (bg, text, label) in enumerate(zip(bg_per_cell, text_per_cell, labels)):
        x = H_PADDING + i * cell_w
        parts.append(
            f'<rect x="{x}" y="{y}" width="{cell_w}" height="{CELL_H}" fill="{bg}" />'
        )
        parts.append(
            f'<text x="{x + cell_w / 2:.1f}" y="{y + CELL_H / 2 + 4:.1f}" '
            f'font-family="{FONT_FAMILY}" font-size="{FONT_SIZE}" '
            f'font-weight="{FONT_WEIGHT}" fill="{text}" '
            f'text-anchor="middle">{_esc(label)}</text>'
        )
    return "\n".join(parts)


def render_palette_svg(
    fill_hexes: list[str],
    text_dark_hexes: list[str],
    text_light_hexes: list[str],
    labels: list[str],
) -> str:
    """Render the three-row preview SVG.

    Row 1: day-mode text   — palette color text on ``#FFFFFF``.
    Row 2: night-mode text — palette color text on ``#000000``.
    Row 3: fill mode       — palette color as fill with APCA-picked text.
    """
    n = len(fill_hexes)
    assert (
        len(text_dark_hexes) == n and len(text_light_hexes) == n and len(labels) == n
    ), "all input lists must have equal length"
    cell_w = _cell_width_for(labels)
    width = H_PADDING * 2 + n * cell_w
    height = V_PADDING * 2 + 3 * CELL_H + 2 * ROW_GAP

    row1_y = V_PADDING
    row1 = _row_svg(row1_y, cell_w, [DATAFIELD_BG_LIGHT] * n, text_light_hexes, labels)

    row2_y = row1_y + CELL_H + ROW_GAP
    row2 = _row_svg(row2_y, cell_w, [DATAFIELD_BG_DARK] * n, text_dark_hexes, labels)

    row3_y = row2_y + CELL_H + ROW_GAP
    row3 = _row_svg(
        row3_y,
        cell_w,
        fill_hexes,
        [best_text_on_background(h) for h in fill_hexes],
        labels,
    )

    header = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">'
    )
    return f"{header}\n{row1}\n{row2}\n{row3}\n</svg>\n"


def _grade_x(grade: float) -> float:
    """Axis position in px for a grade on the shared clamped axis."""
    frac = (grade - GRADE_AXIS_MIN) / (GRADE_AXIS_MAX - GRADE_AXIS_MIN)
    return H_PADDING + frac * GRADE_ROW_W


def _exemplar(mid: float) -> str:
    """Round half away from zero, so -12.5 reads -13 and 22.5 reads 23."""
    rounded = math.floor(mid + 0.5) if mid >= 0.0 else math.ceil(mid - 0.5)
    return str(int(rounded))


def _grade_cells(
    entries: list[tuple[float, str]],
) -> list[tuple[float, float, str, int]]:
    """(lo, hi, hex, band_index) cells on the clamped axis, ascending.

    ``entries`` ascending ``(lo_threshold, hex)``; the open low end arrives as
    ``float("-inf")``. A palette with no descent bands starts at 0 rather than
    the clamp, so missing coverage reads as absence. Each cell carries the index
    of the band it came from, so colour lookups stay aligned even if clamping
    ever drops a band.
    """
    first_lo = entries[0][0]
    floor_lo = (
        GRADE_AXIS_MIN
        if first_lo == float("-inf") or first_lo < 0.0
        else max(first_lo, 0.0)
    )
    cells = []
    for i, (lo, hex_) in enumerate(entries):
        lo_c = max(lo if lo != float("-inf") else GRADE_AXIS_MIN, GRADE_AXIS_MIN)
        if i == 0:
            lo_c = floor_lo
        hi = entries[i + 1][0] if i + 1 < len(entries) else GRADE_AXIS_MAX
        hi_c = min(hi, GRADE_AXIS_MAX)
        if hi_c > lo_c:
            cells.append((lo_c, hi_c, hex_, i))
    return cells


def render_grade_palette_svg(
    cells: list[tuple[float, float, str, int]],
    text_dark_hexes: list[str],
    text_light_hexes: list[str],
) -> str:
    """Three A1 rows (day text, night text, fill) plus one edge-tick axis."""
    width = H_PADDING * 2 + GRADE_ROW_W
    height = V_PADDING * 2 + 3 * CELL_H + 2 * ROW_GAP + TICK_H

    def row(y: int, cell_fill, text_color) -> str:
        parts = []
        for lo, hi, hex_, band in cells:
            xa, xb = _grade_x(lo), _grade_x(hi)
            parts.append(
                f'<rect x="{xa:.1f}" y="{y}" width="{xb - xa:.1f}" '
                f'height="{CELL_H}" fill="{cell_fill(band, hex_)}" />'
            )
            label = _exemplar((lo + hi) / 2.0)
            if (xb - xa) >= len(label) * 8 + 6:
                parts.append(
                    f'<text x="{(xa + xb) / 2:.1f}" y="{y + CELL_H / 2 + 4:.1f}" '
                    f'font-family="{FONT_FAMILY}" font-size="{FONT_SIZE}" '
                    f'font-weight="{FONT_WEIGHT}" fill="{text_color(band, hex_)}" '
                    f'text-anchor="middle">{_esc(label)}</text>'
                )
        return "\n".join(parts)

    row1_y = V_PADDING
    row2_y = row1_y + CELL_H + ROW_GAP
    row3_y = row2_y + CELL_H + ROW_GAP
    tick_y = row3_y + CELL_H

    rows = [
        row(
            row1_y, lambda _b, _h: DATAFIELD_BG_LIGHT, lambda b, _h: text_light_hexes[b]
        ),
        row(row2_y, lambda _b, _h: DATAFIELD_BG_DARK, lambda b, _h: text_dark_hexes[b]),
        row(row3_y, lambda _b, h: h, lambda _b, h: best_text_on_background(h)),
    ]

    stops = [lo for lo, _, _, _ in cells[1:]]
    if cells[0][0] >= 0.0:
        stops = [cells[0][0]] + stops
    ticks = []
    for stop in stops:
        x = _grade_x(stop)
        ticks.append(
            f'<line x1="{x:.1f}" y1="{tick_y}" x2="{x:.1f}" y2="{tick_y + 3}" stroke="#999" />'
        )
        label = f"{stop:g}"
        ticks.append(
            f'<text x="{x:.1f}" y="{tick_y + 14}" font-family="{FONT_FAMILY}" '
            f'font-size="10" fill="#777" text-anchor="middle">{_esc(label)}</text>'
        )

    header = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
    )
    return header + "\n" + "\n".join(rows) + "\n" + "\n".join(ticks) + "\n</svg>\n"


# ---------------------------------------------------------------------------
# Text-palette resolution (prefers cached Kotlin values; falls back on-the-fly)
# ---------------------------------------------------------------------------


def _readable_variant(power_kotlin_name: str, suffix: str) -> str:
    """``karooPowerColors`` + ``Dark`` → ``karooPowerColorsReadableDark``."""
    return power_kotlin_name.replace("Colors", f"ColorsReadable{suffix}")


def _text_palette(power_kotlin_name: str, bg: str) -> list[str]:
    """Return text-mode palette hexes for the given background.

    Resolution order:
    1. Variant-specific cache (``*ReadableDark`` / ``*ReadableLight``).
    2. Legacy ``*ColorsReadable`` for the dark variant (pre-rename).
    3. On-the-fly APCA correction from the base palette.
    """
    suffix = "Dark" if bg == DATAFIELD_BG_DARK else "Light"
    cached = _readable_variant(power_kotlin_name, suffix)
    if cached in PALETTES_BY_KOTLIN_NAME:
        return PALETTES_BY_KOTLIN_NAME[cached]
    if bg == DATAFIELD_BG_DARK:
        legacy = power_kotlin_name.replace("Colors", "ColorsReadable")
        if legacy in PALETTES_BY_KOTLIN_NAME:
            return PALETTES_BY_KOTLIN_NAME[legacy]
    base = PALETTES_BY_KOTLIN_NAME[power_kotlin_name]
    return [adjust_for_readability(c, bg) for c in base]


def _grade_text_bands(power_kotlin_name: str, fills: list[str], bg: str) -> list[str]:
    """Return text-mode band colors for the given background.

    For grade bands the Kotlin readable variants live under
    ``*_GRADE_BANDS_READABLE_DARK`` / ``*_GRADE_BANDS_READABLE_LIGHT``; for the
    Karoo palette the bands reference ``karooPowerColors*[i]`` instead.
    Falls back to on-the-fly correction otherwise.
    """
    suffix = "READABLE_DARK" if bg == DATAFIELD_BG_DARK else "READABLE_LIGHT"
    cached_name = f"{power_kotlin_name}_{suffix}"
    if cached_name in GRADE_BANDS_BY_KOTLIN_NAME:
        return [hex_ for _, hex_ in GRADE_BANDS_BY_KOTLIN_NAME[cached_name]]
    legacy_name = f"{power_kotlin_name}_READABLE"
    if bg == DATAFIELD_BG_DARK and legacy_name in GRADE_BANDS_BY_KOTLIN_NAME:
        return [hex_ for _, hex_ in GRADE_BANDS_BY_KOTLIN_NAME[legacy_name]]
    return [adjust_for_readability(c, bg) for c in fills]


# ---------------------------------------------------------------------------
# Per-section writers
# ---------------------------------------------------------------------------


def _write_zone_palettes(
    out_dir: Path,
    order: list[tuple[str, str]],
    labels: list[str],
    file_prefix: str,
) -> list[Path]:
    written: list[Path] = []
    for slug, kotlin_name in order:
        fills = PALETTES_BY_KOTLIN_NAME[kotlin_name]
        text_dark = _text_palette(kotlin_name, DATAFIELD_BG_DARK)
        text_light = _text_palette(kotlin_name, DATAFIELD_BG_LIGHT)
        svg = render_palette_svg(fills, text_dark, text_light, labels)
        path = out_dir / f"palette-{file_prefix}-{slug}.svg"
        path.write_text(svg, encoding="utf-8")
        written.append(path)
    return written


def _write_grade_palettes(out_dir: Path) -> list[Path]:
    written: list[Path] = []
    for slug, kotlin_name in GRADE_PALETTE_ORDER:
        # Kotlin bands are ordered steep → descent; the axis renders descent → steep.
        entries = [
            (thr, hex_)
            for thr, hex_ in reversed(GRADE_BANDS_BY_KOTLIN_NAME[kotlin_name])
        ]
        cells = _grade_cells(entries)
        fills = [hex_ for _, hex_ in entries]
        text_dark = list(
            reversed(_grade_text_bands(kotlin_name, fills[::-1], DATAFIELD_BG_DARK))
        )
        text_light = list(
            reversed(_grade_text_bands(kotlin_name, fills[::-1], DATAFIELD_BG_LIGHT))
        )
        svg = render_grade_palette_svg(cells, text_dark, text_light)
        path = out_dir / f"palette-grade-{slug}.svg"
        path.write_text(svg, encoding="utf-8")
        written.append(path)
    return written


def main() -> None:
    out_dir = Path(__file__).parent.parent / "docs" / "palettes"
    out_dir.mkdir(parents=True, exist_ok=True)

    written: list[Path] = []
    written.extend(
        _write_zone_palettes(out_dir, POWER_PALETTE_ORDER, POWER_ZONE_LABELS, "power")
    )
    written.extend(
        _write_zone_palettes(out_dir, HR_PALETTE_ORDER, HR_ZONE_LABELS, "hr")
    )
    written.extend(_write_grade_palettes(out_dir))

    for path in written:
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
