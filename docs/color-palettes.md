# Color palettes

Every Barberfish field has a color mode — Text, Fill, or None — set per field.
The mode determines how the chosen palette is rendered, and each mode handles
contrast on the dark Karoo screen automatically. No global readable/original
choice; pick any palette and both modes stay legible.

The Karoo datafield background is pitch black (`#000000`); the ride screen
around it is slightly lighter (`#1B2D2D`). All contrast calculations below use
the datafield background.

## Text mode: auto contrast-tuning

Several brand zone colors are too dark to read as text on the Karoo screen.
Wahoo's navy Z2 (`#253070`) e.g. is [very hard to read](https://apcacontrast.com/?BG=000000&TXT=253070&DEV=G4g&BUF=A22).

Barberfish adjusts each affected palette using [APCA](https://apcacontrast.com/),
the Accessible Perceptual Contrast Algorithm. Colors below Lc 45 (the minimum
for large bold text) have their [HSLuv](https://www.hsluv.org/) lightness
raised until they pass, keeping the original hue and saturation intact. HSLuv
is perceptually uniform, so raising lightness does not shift the apparent
hue. Colors that already pass are left unchanged. The contrast-tuned values
are pre-computed via `scripts/apca_hsluv.py` and used automatically whenever
a field renders in Text mode.

A known limitation: two colors with the same hue but different dark shades
can converge to the same tuned color, since their distinction was encoded
entirely in darkness. The Wahoo and Garmin grade palettes are affected
e.g. their steepest two bands map to the same color in Text mode. Fill mode
preserves the original distinction.

## Fill mode: auto-picked text color

When the field paints the palette color across the cell, the brand colors
are kept exactly as designed and the text drawn on top is auto-picked per
cell — whichever of black or white yields the higher APCA `|Lc|` against
that specific fill. So Turbo's deep crimson `#8E1201` keeps white text
(`Lc≈-94`), Turbo's lime `#B0F94D` flips to black text (`Lc≈+90`), and
mid-luminance grays settle on whichever side wins.

The rule lives in `bestTextOnBackground` in
`app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ZoneColoring.kt`.
For a visual sweep across every palette under both rules — picker on,
old white-only default, and Text mode — run
`uv run scripts/preview_bg_text_picks.py` and open the SVGs it writes to
`scripts/output/`.

## HSLuv palette

The [HSLuv](https://www.hsluv.org/) palette is inspired by the perceptually
uniform colormaps available in [seaborn](https://seaborn.pydata.org/tutorial/color_palettes.html).
It was designed from the start with equidistant lightness steps across all
zones such that every color is already readable on the Karoo screen without
modification — the same values render in both Text and Fill modes. The hue
and saturation were tuned to produce a color progression that follows the
Wahoo palette's character from cool grey to green to redish pink.

The HSLuv grade palette uses the same colors as the HSLuv power palette,
assigned to grade bands with Garmin-style spacing (seven bands from flat to
steep).

## Zone color palettes

| Palette       | Power Fill mode             | Power Text mode                          | HR Fill mode                       | HR Text mode                                |
| ------------- | --------------------------- | ---------------------------------------- | ---------------------------------- | ------------------------------------------- |
| Karoo         | ![](palette-karoo.svg)      | ![](palette-karoo-readable.svg)          | ![](palette-karoo-hr.svg)          | ![](palette-karoo-hr-readable.svg)          |
| Wahoo         | ![](palette-wahoo.svg)      | ![](palette-wahoo-readable.svg)          | ![](palette-wahoo-hr.svg)          | ![](palette-wahoo-hr-readable.svg)          |
| Zwift         | ![](palette-zwift.svg)      | ![](palette-zwift-readable.svg)          | ![](palette-zwift-hr.svg)          | ![](palette-zwift-hr-readable.svg)          |
| Intervals.icu | ![](palette-intervals.svg)  | ![](palette-intervals-readable.svg)      | ![](palette-intervals-hr.svg)      | ![](palette-intervals-hr-readable.svg)      |
| HSLuv         | ![](palette-hsluv.svg)      | ![](palette-hsluv.svg)                   | ![](palette-hsluv-hr.svg)          | ![](palette-hsluv-hr.svg)                   |

## Grade color palettes 

| Palette | Bands (flat → steep)                                  | Fill mode                          | Text mode                                   |
| ------- | ----------------------------------------------------- | ---------------------------------- | ------------------------------------------- |
| Karoo   | 0–5% · 5–8% · 8–13% · 13–16% · 16–20% · 20–24% · ≥24% | ![](palette-grade-karoo.svg)  | ![](palette-grade-karoo-readable.svg)  |
| Wahoo   | 0–4% · 4–8% · 8–12% · 12–20% · ≥20%                   | ![](palette-grade-wahoo.svg)  | ![](palette-grade-wahoo-readable.svg)  |
| Garmin  | 0–3% · 3–6% · 6–9% · 9–12% · ≥12%                     | ![](palette-grade-garmin.svg) | ![](palette-grade-garmin-readable.svg) |
| HSLuv   | 0–3% · 3–6% · 6–9% · 9–12% · 12–15% · 15–18% · ≥18%   | ![](palette-grade-hsluv.svg)  | ![](palette-grade-hsluv.svg)           |
