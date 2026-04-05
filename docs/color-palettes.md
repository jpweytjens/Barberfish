# Color palettes

The Karoo ride screen uses a dark background (`#1B2D2D`). Palettes from other platforms were designed for white or light backgrounds, so several of their zone colors are too dark to read as text on the Karoo screen. Wahoo's navy Z2 (`#253070`) e.g. is [very hard to read](https://apcacontrast.com/?BG=1b2d2d&TXT=253070&DEV=G4g&BUF=A22).

## APCA adjustment

Barberfish adjusts each palette using [APCA](https://apcacontrast.com/), the Accessible Perceptual Contrast Algorithm. Colors below Lc 45 (the minimum for large bold text) have their [HSLuv](https://www.hsluv.org/) lightness raised until they pass, keeping the original hue and saturation intact. HSLuv is perceptually uniform, so raising lightness does not shift the apparent hue. Colors that already pass are left unchanged. Readable values are pre-computed via `scripts/apca_hsluv.py`.

APCA correction is most important for the text zone coloring mode, where colored text appears on the fixed dark Karoo background. In background-fill mode the text is always white, and white on a saturated color is generally easier to read than that same color as text on a dark background.

A known limitation: two colors with the same hue but different dark shades can converge to the same readable color, since their distinction was encoded entirely in darkness. The Wahoo and Garmin grade palettes are affected, e.g. their steepest two bands map to the same readable color.

## HSLuv palette

The [HSLuv](https://www.hsluv.org/) palette is inspired by the perceptually uniform colormaps available in [seaborn](https://seaborn.pydata.org/tutorial/color_palettes.html). It was designed from the start with equidistant lightness steps across all zones such that every color is already readable on the Karoo screen without modification. The hue and saturation were tuned to produce a color progression that follows the Wahoo palette's character from cool grey to green to redish pink.

The HSLuv grade palette uses the same colors as the HSLuv power palette, assigned to grade bands with Garmin-style spacing (seven bands from flat to steep).

## Zone color palettes

| Palette       | Power zones (Z1 – Z7) Original  | Power zones (Z1 – Z7) Readable           | HR zones (Z1 – Z5) Original        | HR zones (Z1 – Z5) Readable                 |
| ------------- | ------------------------------- | ---------------------------------------- | ---------------------------------- | ------------------------------------------- |
| Karoo         | ![](palette-karoo.svg)     | ![](palette-karoo-readable.svg)     | ![](palette-karoo-hr.svg)     | ![](palette-karoo-hr-readable.svg)     |
| Wahoo         | ![](palette-wahoo.svg)     | ![](palette-wahoo-readable.svg)     | ![](palette-wahoo-hr.svg)     | ![](palette-wahoo-hr-readable.svg)     |
| Zwift         | ![](palette-zwift.svg)     | ![](palette-zwift-readable.svg)     | ![](palette-zwift-hr.svg)     | ![](palette-zwift-hr-readable.svg)     |
| Intervals.icu | ![](palette-intervals.svg) | ![](palette-intervals-readable.svg) | ![](palette-intervals-hr.svg) | ![](palette-intervals-hr-readable.svg) |
| HSLuv         |                                 | ![](palette-hsluv.svg)              |                                    | ![](palette-hsluv-hr.svg)              |

## Grade color palettes 

| Palette | Bands (flat → steep)                                  | Original                           | Readable                                    |
| ------- | ----------------------------------------------------- | ---------------------------------- | ------------------------------------------- |
| Karoo   | 0–5% · 5–8% · 8–13% · 13–16% · 16–20% · 20–24% · ≥24% | ![](palette-grade-karoo.svg)  | ![](palette-grade-karoo-readable.svg)  |
| Wahoo   | 0–4% · 4–8% · 8–12% · 12–20% · ≥20%                   | ![](palette-grade-wahoo.svg)  | ![](palette-grade-wahoo-readable.svg)  |
| Garmin  | 0–3% · 3–6% · 6–9% · 9–12% · ≥12%                     | ![](palette-grade-garmin.svg) | ![](palette-grade-garmin-readable.svg) |
| HSLuv   | 0–3% · 3–6% · 6–9% · 9–12% · 12–15% · 15–18% · ≥18%   |                                    | ![](palette-grade-hsluv.svg)           |
