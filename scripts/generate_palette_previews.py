"""
Generate one two-row palette preview SVG per palette for the README.

Each SVG has two rows of swatches:
  Row 1 — TEXT mode: palette color drawn as text on the dark Karoo bg
          (#000000). Uses the contrast-tuned ("readable") palette variant
          when one exists, since that's what the app actually renders in
          text mode.
  Row 2 — FILL mode: palette color as cell fill with the APCA-picked
          black/white overlay text (mirrors `bestTextOnBackground` in
          ZoneColoring.kt).

Outputs are written to `docs/img/palette-{zone,grade}-<palette>.svg`.

Inline-friendly: each SVG is sized to fit comfortably in a README markdown
table cell (~300–550 px wide, ~60 px tall depending on band count).

Palette data is reused from `scripts/preview_bg_text_picks.py`
(`POWER_PALETTES`, `GRADE_PALETTES`). HR palettes are not enumerated in
the README — they live in `docs/color-palettes.md`.

Usage
-----
uv run scripts/generate_palette_previews.py
"""

from __future__ import annotations

from pathlib import Path

from preview_bg_text_picks import (
    GRADE_PALETTES,
    HR_PALETTES,
    HR_ZONE_LABELS,
    POWER_PALETTES,
    POWER_ZONE_LABELS,
    best_text_on_background,
)

DATAFIELD_BG = "#000000"

# README enumerates these power-zone palettes. Each entry is
# (output_slug, regular_key, readable_key_or_None). The readable variant is
# used for the TEXT-mode row when one exists.
POWER_PALETTE_ORDER = [
    ("karoo",     "Karoo",     "Karoo (readable)"),
    ("wahoo",     "Wahoo",     "Wahoo (readable)"),
    ("zwift",     "Zwift",     "Zwift (readable)"),
    ("intervals", "Intervals", "Intervals (readable)"),
    ("hsluv",     "HSLuv",     None),  # single variant by construction
]

# README enumerates these HR-zone palettes (5 zones each, parallel to the
# power-zone palette structure).
HR_PALETTE_ORDER = [
    ("karoo",     "Karoo",     "Karoo (readable)"),
    ("wahoo",     "Wahoo",     "Wahoo (readable)"),
    ("zwift",     "Zwift",     "Zwift (readable)"),
    ("intervals", "Intervals", "Intervals (readable)"),
    ("hsluv",     "HSLuv",     None),
]

# README enumerates these grade palettes. Each entry is
# (output_slug, regular_key, readable_key_or_None). Turbo and HSLuv have a
# single variant.
GRADE_PALETTE_ORDER = [
    ("karoo",  "Karoo",  "Karoo (readable)"),
    ("wahoo",  "Wahoo",  "Wahoo (readable)"),
    ("garmin", "Garmin", "Garmin (readable)"),
    ("zwift",  "Zwift",  "Zwift (readable)"),
    ("hsluv",  "HSLuv",  None),
    ("turbo",  "Turbo",  None),
]

# README-style band labels, descent → neutral → steep. Counts match the band
# counts in GRADE_PALETTES. Replaces the dash-form labels in GRADE_PALETTES
# (which are ambiguous for negative grades — e.g. "-6–-3%") with the half-open
# interval notation already used in the README's grade palettes table.
GRADE_LABELS_README = {
    "karoo":  ["[0, 5)", "[5, 8)", "[8, 13)", "[13, 16)", "[16, 20)", "[20, 24)", "[24, ∞)"],
    "wahoo":  ["[0, 4)", "[4, 8)", "[8, 12)", "[12, 20)", "[20, ∞)"],
    "garmin": ["[0, 3)", "[3, 6)", "[6, 9)", "[9, 12)", "[12, ∞)"],
    "zwift":  ["[0, 3)", "[3, 6)", "[6, 9)", "[9, ∞)"],
    "hsluv":  ["[0, 3)", "[3, 6)", "[6, 9)", "[9, 12)", "[12, 15)", "[15, 18)", "[18, ∞)"],
    "turbo":  [
        "(-∞, -9)", "[-9, -6)", "[-6, -3)", "[-3, 0)",
        "[0, 3)", "[3, 6)", "[6, 9)", "[9, 12)", "[12, 15)", "[15, ∞)",
    ],
}


# ---------------------------------------------------------------------------
# SVG rendering
# ---------------------------------------------------------------------------

CELL_W_DEFAULT = 56   # min cell width; widened to fit the longest label
CELL_H = 26           # per-row cell height
ROW_GAP = 0           # vertical gap between the two rows (flush)
H_PADDING = 0         # SVG horizontal padding
V_PADDING = 0         # SVG vertical padding
FONT_FAMILY = "-apple-system, system-ui, sans-serif"
FONT_SIZE = 13
FONT_WEIGHT = 600


def _esc(text: str) -> str:
    return (
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    )


def _cell_width_for(labels: list[str]) -> int:
    """Pick a cell width that fits the widest label at the chosen font size.

    Approximation: average glyph advance for the system sans at 13px ≈ 7.2 px.
    Add 16 px breathing room on either side.
    """
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
            f'<rect x="{x}" y="{y}" width="{cell_w}" height="{CELL_H}" '
            f'fill="{bg}" />'
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
    text_hexes: list[str],
    labels: list[str],
) -> str:
    """Render the two-row preview SVG.

    Row 1 (text mode):
        bg per cell = DATAFIELD_BG, text per cell = `text_hexes[i]`.
    Row 2 (fill mode):
        bg per cell = `fill_hexes[i]`, text per cell = best_text_on_background(fill).

    `labels` is one string per cell; same length as the palette.
    `text_hexes` uses the readable variant of the palette when one exists.
    """
    assert len(fill_hexes) == len(text_hexes) == len(labels), (
        "palette lists must have equal length"
    )
    n = len(fill_hexes)
    cell_w = _cell_width_for(labels)
    width = H_PADDING * 2 + n * cell_w
    height = V_PADDING * 2 + 2 * CELL_H + ROW_GAP

    # Row 1 — text mode: palette color as text on dark
    row1_bg = [DATAFIELD_BG] * n
    row1_text = text_hexes
    row1 = _row_svg(V_PADDING, cell_w, row1_bg, row1_text, labels)

    # Row 2 — fill mode: palette color as fill, APCA-picked overlay text
    row2_bg = fill_hexes
    row2_text = [best_text_on_background(h) for h in fill_hexes]
    row2_y = V_PADDING + CELL_H + ROW_GAP
    row2 = _row_svg(row2_y, cell_w, row2_bg, row2_text, labels)

    header = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">'
    )
    return f"{header}\n{row1}\n{row2}\n</svg>\n"


def _resolve_text_palette(
    palettes: dict[str, list[str]],
    regular_key: str,
    readable_key: str | None,
) -> list[str]:
    """Pick the palette hexes to use as text-mode colors.

    If a readable variant exists, use it (it's what the app renders in text
    mode). Otherwise fall back to the regular palette.
    """
    if readable_key and readable_key in palettes:
        return palettes[readable_key]
    return palettes[regular_key]


def _write_power_palettes(out_dir: Path) -> list[Path]:
    written: list[Path] = []
    for slug, regular_key, readable_key in POWER_PALETTE_ORDER:
        fill_hexes = POWER_PALETTES[regular_key]
        text_hexes = _resolve_text_palette(POWER_PALETTES, regular_key, readable_key)
        labels = POWER_ZONE_LABELS
        svg = render_palette_svg(fill_hexes, text_hexes, labels)
        path = out_dir / f"palette-power-{slug}.svg"
        path.write_text(svg, encoding="utf-8")
        written.append(path)
    return written


def _write_hr_palettes(out_dir: Path) -> list[Path]:
    written: list[Path] = []
    for slug, regular_key, readable_key in HR_PALETTE_ORDER:
        fill_hexes = HR_PALETTES[regular_key]
        text_hexes = _resolve_text_palette(HR_PALETTES, regular_key, readable_key)
        labels = HR_ZONE_LABELS
        svg = render_palette_svg(fill_hexes, text_hexes, labels)
        path = out_dir / f"palette-hr-{slug}.svg"
        path.write_text(svg, encoding="utf-8")
        written.append(path)
    return written


def _write_grade_palettes(out_dir: Path) -> list[Path]:
    written: list[Path] = []
    for slug, regular_key, readable_key in GRADE_PALETTE_ORDER:
        # GRADE_PALETTES entries are list[(label, hex)] ordered steep → descent;
        # the README catalog renders descent → steep, so reverse here and use
        # the README-style interval-notation labels from GRADE_LABELS_README.
        regular_entries = list(reversed(GRADE_PALETTES[regular_key]))
        fill_hexes = [hex_ for _, hex_ in regular_entries]
        if readable_key and readable_key in GRADE_PALETTES:
            readable_entries = list(reversed(GRADE_PALETTES[readable_key]))
            text_hexes = [hex_ for _, hex_ in readable_entries]
        else:
            text_hexes = fill_hexes
        labels = GRADE_LABELS_README[slug]
        assert len(labels) == len(fill_hexes), (
            f"{slug}: README labels ({len(labels)}) must match band count ({len(fill_hexes)})"
        )
        svg = render_palette_svg(fill_hexes, text_hexes, labels)
        path = out_dir / f"palette-grade-{slug}.svg"
        path.write_text(svg, encoding="utf-8")
        written.append(path)
    return written


def main() -> None:
    out_dir = Path(__file__).parent.parent / "docs" / "img"
    out_dir.mkdir(parents=True, exist_ok=True)

    written = []
    written.extend(_write_power_palettes(out_dir))
    written.extend(_write_hr_palettes(out_dir))
    written.extend(_write_grade_palettes(out_dir))

    for path in written:
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
