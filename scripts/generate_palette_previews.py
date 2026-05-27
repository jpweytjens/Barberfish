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
    POWER_PALETTES,
    POWER_ZONE_LABELS,
    best_text_on_background,
)

DATAFIELD_BG = "#000000"

# README enumerates these zone palettes (power-zone variant only). Each entry
# is (output_slug, regular_key, readable_key_or_None). The readable variant is
# used for the TEXT-mode row when one exists.
ZONE_PALETTES = [
    ("karoo",     "Karoo",     "Karoo (readable)"),
    ("wahoo",     "Wahoo",     "Wahoo (readable)"),
    ("zwift",     "Zwift",     "Zwift (readable)"),
    ("intervals", "Intervals", "Intervals (readable)"),
    ("hsluv",     "HSLuv",     None),  # single variant by construction
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


def main() -> None:
    raise NotImplementedError("filled in by later tasks")


if __name__ == "__main__":
    main()
