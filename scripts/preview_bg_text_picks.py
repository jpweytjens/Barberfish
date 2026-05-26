"""
Diagnostic: visualize Barberfish palette readability under three rules.

Outputs three SVGs to `scripts/output/` so you can eyeball them side by side:

1. `bg_text_picks.svg`
   BACKGROUND color mode with the runtime picker (`bestTextOnBackground` in
   `app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ZoneColoring.kt`).
   Each fill gets whichever of white/black has the higher APCA |Lc|.

2. `bg_text_default.svg`
   BACKGROUND color mode with the old behavior — always-white text (dark-mode
   default). Side-by-side with (1) to show where the picker matters most and
   which "readable" palette variants are now redundant.

3. `text_mode_palettes.svg`
   TEXT color mode — palette color used as text on the actual datafield bg
   (`#000000`). Shows where the readable variants still pay off.

Not a code generator. Not a README asset. Open the SVGs in a browser.

Usage
-----
uv run scripts/preview_bg_text_picks.py
"""

from __future__ import annotations

import math
from pathlib import Path
from typing import Callable

from apca_hsluv import apca_contrast

# ---------------------------------------------------------------------------
# Picker (mirror of `bestTextOnBackground` in ZoneColoring.kt)
# ---------------------------------------------------------------------------

WHITE = "#FFFFFF"
BLACK = "#000000"
# The actual datafield bg in the Karoo rideapp (confirmed in apca_hsluv.py).
DATAFIELD_BG = BLACK


def best_text_on_background(bg: str) -> str:
    """Return whichever of `WHITE`/`BLACK` has higher APCA |Lc| against `bg`."""
    lc_w = abs(apca_contrast(WHITE, bg))
    lc_b = abs(apca_contrast(BLACK, bg))
    return WHITE if lc_w >= lc_b else BLACK


# ---------------------------------------------------------------------------
# Palette data — duplicated from ZoneColoring.kt / FieldColors.kt.
# Update here when palettes change. Diagnostic only; not consumed by the app.
# ---------------------------------------------------------------------------

POWER_ZONE_LABELS = [f"Z{i}" for i in range(1, 8)]
HR_ZONE_LABELS = [f"Z{i}" for i in range(1, 6)]

POWER_PALETTES = {
    "Karoo": [
        "#1A8C3A", "#40D078", "#F0D800", "#F08868", "#F06020", "#D01020", "#9020A0",
    ],
    "Karoo (readable)": [
        "#22AA48", "#40D078", "#F0D800", "#F08868", "#F86421", "#FC5C61", "#DE5AF3",
    ],
    "Wahoo": [
        "#C0C0C0", "#253070", "#4E90CC", "#48B830", "#F0D818", "#E06818", "#E03020",
    ],
    "Wahoo (readable)": [
        "#C0C0C0", "#868FDC", "#549AD9", "#48B830", "#F0D818", "#ED6F1A", "#F86159",
    ],
    "Intervals": [
        "#3DB39F", "#3DB33F", "#FCD549", "#FC9C49", "#E34074", "#8963D8", "#797388",
    ],
    "Intervals (readable)": [
        "#3DB39F", "#3DB33F", "#FCD549", "#FC9C49", "#EB688B", "#A086E2", "#9793A3",
    ],
    "Zwift": [
        "#7B7E80", "#368AF4", "#59B962", "#F0C649", "#F06B45", "#F8431F", "#F8431F",
    ],
    "Zwift (readable)": [
        "#929698", "#5594F5", "#59B962", "#F0C649", "#F06B45", "#FA604D", "#FA604D",
    ],
    "HSLuv": [
        "#9395A1", "#00A5B8", "#00AA86", "#71A500", "#BB9000", "#FF5F68", "#FF41DF",
    ],
}

# HR palettes are index subsets of their power palette. Mirror the Kotlin
# `.take(5)` and `listOf(0, 1, 3, 5, 6).map { ... }` patterns.
_KAROO_HR_IDX = [0, 1, 2, 3, 5]
_WAHOO_HR_IDX = [0, 1, 3, 5, 6]
_INTERVALS_HR_IDX = [0, 1, 2, 3, 4]  # .take(5)
_ZWIFT_HR_IDX = [0, 1, 2, 3, 4]      # .take(5)
_HSLUV_HR_IDX = [0, 1, 3, 5, 6]

HR_PALETTES = {
    "Karoo": [POWER_PALETTES["Karoo"][i] for i in _KAROO_HR_IDX],
    "Karoo (readable)": [POWER_PALETTES["Karoo (readable)"][i] for i in _KAROO_HR_IDX],
    "Wahoo": [POWER_PALETTES["Wahoo"][i] for i in _WAHOO_HR_IDX],
    "Wahoo (readable)": [POWER_PALETTES["Wahoo (readable)"][i] for i in _WAHOO_HR_IDX],
    "Intervals": [POWER_PALETTES["Intervals"][i] for i in _INTERVALS_HR_IDX],
    "Intervals (readable)": [POWER_PALETTES["Intervals (readable)"][i] for i in _INTERVALS_HR_IDX],
    "Zwift": [POWER_PALETTES["Zwift"][i] for i in _ZWIFT_HR_IDX],
    "Zwift (readable)": [POWER_PALETTES["Zwift (readable)"][i] for i in _ZWIFT_HR_IDX],
    "HSLuv": [POWER_PALETTES["HSLuv"][i] for i in _HSLUV_HR_IDX],
}

# Grade bands — (label, hex). Order = highest grade band first to match the Kotlin lists.
GRADE_PALETTES: dict[str, list[tuple[str, str]]] = {
    "Karoo": [
        (">23.5%",   POWER_PALETTES["Karoo"][6]),
        ("19.6–23.5%", POWER_PALETTES["Karoo"][5]),
        ("15.6–19.5%", POWER_PALETTES["Karoo"][4]),
        ("12.6–15.5%", POWER_PALETTES["Karoo"][3]),
        ("7.6–12.5%",  POWER_PALETTES["Karoo"][2]),
        ("4.6–7.5%",   POWER_PALETTES["Karoo"][1]),
        ("<4.6%",      POWER_PALETTES["Karoo"][0]),
    ],
    "Karoo (readable)": [
        (">23.5%",   POWER_PALETTES["Karoo (readable)"][6]),
        ("19.6–23.5%", POWER_PALETTES["Karoo (readable)"][5]),
        ("15.6–19.5%", POWER_PALETTES["Karoo (readable)"][4]),
        ("12.6–15.5%", POWER_PALETTES["Karoo (readable)"][3]),
        ("7.6–12.5%",  POWER_PALETTES["Karoo (readable)"][2]),
        ("4.6–7.5%",   POWER_PALETTES["Karoo (readable)"][1]),
        ("<4.6%",      POWER_PALETTES["Karoo (readable)"][0]),
    ],
    "Wahoo": [
        ("20%+",   "#540000"),
        ("12–20%", "#AA0200"),
        ("8–12%",  "#FF5501"),
        ("4–8%",   "#FEFF00"),
        ("0–4%",   "#04FE00"),
    ],
    "Wahoo (readable)": [
        ("20%+",   "#FF5959"),
        ("12–20%", "#FF5958"),
        ("8–12%",  "#FF5C23"),
        ("4–8%",   "#FEFF00"),
        ("0–4%",   "#04FE00"),
    ],
    "Garmin": [
        ("HC >12%", "#ED1B24"),
        ("Cat1 9–12%", "#F36C72"),
        ("Cat2 6–9%",  "#FBAD41"),
        ("Cat3 3–6%",  "#F9EE44"),
        ("Cat4 0–3%",  "#6EBE43"),
    ],
    "Garmin (readable)": [
        ("HC >12%", "#FA5E60"),
        ("Cat1 9–12%", "#F36C72"),
        ("Cat2 6–9%",  "#FBAD41"),
        ("Cat3 3–6%",  "#F9EE44"),
        ("Cat4 0–3%",  "#6EBE43"),
    ],
    "Zwift": [
        ("9%+",  "#EA5147"),
        ("6–9%", "#FE8253"),
        ("3–6%", "#F2C510"),
        ("0–3%", "#39A7D6"),
    ],
    "Zwift (readable)": [
        ("9%+",  "#EB6D66"),
        ("6–9%", "#FE8253"),
        ("3–6%", "#F2C510"),
        ("0–3%", "#39A7D6"),
    ],
    "HSLuv": [
        (">18%",   POWER_PALETTES["HSLuv"][6]),
        ("15–18%", POWER_PALETTES["HSLuv"][5]),
        ("12–15%", POWER_PALETTES["HSLuv"][4]),
        ("9–12%",  POWER_PALETTES["HSLuv"][3]),
        ("6–9%",   POWER_PALETTES["HSLuv"][2]),
        ("3–6%",   POWER_PALETTES["HSLuv"][1]),
        ("<3%",    POWER_PALETTES["HSLuv"][0]),
    ],
    "Turbo": [
        ("15%+",     "#8E1201"),
        ("12–15%",   "#BC2900"),
        ("9–12%",    "#DD4700"),
        ("6–9%",     "#FE932C"),
        ("3–6%",     "#F1D749"),
        ("0–3%",     "#B0F94D"),
        ("-3–0%",    "#30F0A9"),
        ("-6–-3%",   "#2BC7F0"),
        ("-9–-6%",   "#5783E9"),
        ("<-9%",     "#401C4C"),
    ],
}

# Threshold / danger constants from FieldColors.kt
_RDYLGN_RED = "#D73027"
_RDYLGN_GREEN = "#1A9850"
_DANGER_ORANGE = "#FFA726"

THRESHOLD_FACTORS = [i / 10 for i in range(-10, 11)]  # -1.0 .. +1.0 step 0.1


def _hex_to_rgb(h: str) -> tuple[float, float, float]:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))  # type: ignore[return-value]


def _rgb_to_hex(r: float, g: float, b: float) -> str:
    return "#{:02X}{:02X}{:02X}".format(
        max(0, min(255, round(r * 255))),
        max(0, min(255, round(g * 255))),
        max(0, min(255, round(b * 255))),
    )


def _lerp_hex(a_hex: str, b_hex: str, t: float) -> str:
    a, b = _hex_to_rgb(a_hex), _hex_to_rgb(b_hex)
    return _rgb_to_hex(*[a[i] + (b[i] - a[i]) * t for i in range(3)])


def _threshold_bg(factor: float, is_night: bool) -> str:
    """Mirrors `thresholdBackgroundColor` in FieldColors.kt."""
    neutral = BLACK if is_night else WHITE
    end = _RDYLGN_GREEN if factor >= 0 else _RDYLGN_RED
    return _lerp_hex(neutral, end, math.sqrt(abs(factor)))


def _threshold_text(factor: float, is_night: bool) -> str:
    """Mirrors `thresholdTextColor` in FieldColors.kt — note neutral is swapped."""
    neutral = WHITE if is_night else BLACK
    end = _RDYLGN_GREEN if factor >= 0 else _RDYLGN_RED
    return _lerp_hex(neutral, end, math.sqrt(abs(factor)))


def _danger_color(outside: float, border: float, has_safe: bool) -> str:
    """Mirrors `dangerZoneColor` in FieldColors.kt. Used as bg in BG mode and as text in TEXT mode."""
    if outside > 0:
        return _lerp_hex(_DANGER_ORANGE, _RDYLGN_RED, math.sqrt(outside))
    base = _RDYLGN_GREEN if has_safe else WHITE
    return _lerp_hex(base, _DANGER_ORANGE, math.sqrt(border))


def _threshold_palettes_bg() -> dict[str, list[tuple[str, str]]]:
    return {
        "Threshold bg (dark neutral, factor -1.0 → +1.0)": [
            (f"{f:+.1f}", _threshold_bg(f, is_night=True)) for f in THRESHOLD_FACTORS
        ],
        "Threshold bg (light neutral, factor -1.0 → +1.0)": [
            (f"{f:+.1f}", _threshold_bg(f, is_night=False)) for f in THRESHOLD_FACTORS
        ],
    }


def _threshold_palettes_text() -> dict[str, list[tuple[str, str]]]:
    return {
        "Threshold text (dark neutral, factor -1.0 → +1.0)": [
            (f"{f:+.1f}", _threshold_text(f, is_night=True)) for f in THRESHOLD_FACTORS
        ],
        "Threshold text (light neutral, factor -1.0 → +1.0)": [
            (f"{f:+.1f}", _threshold_text(f, is_night=False)) for f in THRESHOLD_FACTORS
        ],
    }


def _danger_palettes() -> dict[str, list[tuple[str, str]]]:
    samples = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
    return {
        "DangerZone outside (0 → 1, ORANGE → RED)": [
            (f"{t:.1f}", _danger_color(outside=t, border=0.0, has_safe=True)) for t in samples
        ],
        "DangerZone border (safe zone, 0 → 1, GREEN → ORANGE)": [
            (f"{t:.1f}", _danger_color(outside=0.0, border=t, has_safe=True)) for t in samples
        ],
        "DangerZone border (one-sided, 0 → 1, WHITE → ORANGE)": [
            (f"{t:.1f}", _danger_color(outside=0.0, border=t, has_safe=False)) for t in samples
        ],
    }


# ---------------------------------------------------------------------------
# Section assembly
# ---------------------------------------------------------------------------

# Each section: (title, list of rows). Each row: (label, list of (col_label, palette_hex)).
# In BG-mode views, palette_hex is the cell fill color.
# In TEXT-mode views, palette_hex is the text color on a fixed dark bg.
Section = tuple[str, list[tuple[str, list[tuple[str, str]]]]]


def _zone_sections(palettes: dict[str, list[str]], col_labels: list[str]) -> list[tuple[str, list[tuple[str, str]]]]:
    return [(name, list(zip(col_labels, hexes))) for name, hexes in palettes.items()]


def bg_view_sections() -> list[Section]:
    return [
        ("Power zones",          _zone_sections(POWER_PALETTES, POWER_ZONE_LABELS)),
        ("HR zones",             _zone_sections(HR_PALETTES, HR_ZONE_LABELS)),
        ("Grade bands",          list(GRADE_PALETTES.items())),
        ("Threshold gradient",   list(_threshold_palettes_bg().items())),
        ("DangerZone gradient",  list(_danger_palettes().items())),
    ]


def text_view_sections() -> list[Section]:
    # Same palette colors as BG view for power/HR/grade — they're used as text instead.
    # Threshold uses the text formula (different neutral); danger formula is shared.
    return [
        ("Power zones",          _zone_sections(POWER_PALETTES, POWER_ZONE_LABELS)),
        ("HR zones",             _zone_sections(HR_PALETTES, HR_ZONE_LABELS)),
        ("Grade bands",          list(GRADE_PALETTES.items())),
        ("Threshold gradient",   list(_threshold_palettes_text().items())),
        ("DangerZone gradient",  list(_danger_palettes().items())),
    ]


# ---------------------------------------------------------------------------
# Swatch transforms — map a palette hex to (bg, text) for a given mode.
# ---------------------------------------------------------------------------

Transform = Callable[[str], tuple[str, str]]


def bg_picker(palette_hex: str) -> tuple[str, str]:
    """BG mode with the runtime APCA picker."""
    return palette_hex, best_text_on_background(palette_hex)


def bg_default_white(palette_hex: str) -> tuple[str, str]:
    """BG mode with the old always-white default (dark system mode)."""
    return palette_hex, WHITE


def text_on_datafield(palette_hex: str) -> tuple[str, str]:
    """TEXT mode — palette color drawn as text on the dark datafield bg."""
    return DATAFIELD_BG, palette_hex


# ---------------------------------------------------------------------------
# SVG rendering
# ---------------------------------------------------------------------------

SWATCH_W = 130
SWATCH_H = 80
LABEL_GAP = 16
ROW_GAP = 28
SECTION_GAP = 36
ROW_LABEL_W = 220
H_MARGIN = 24
V_MARGIN = 24
TITLE_H = 28
SECTION_HEADER_H = 24


def _esc(text: str) -> str:
    return (
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    )


def _swatch_svg(x: float, y: float, bg: str, text: str, col_label: str) -> str:
    lc = apca_contrast(text, bg)
    return "\n".join([
        f'<rect x="{x:.1f}" y="{y:.1f}" width="{SWATCH_W}" height="{SWATCH_H}" '
        f'fill="{bg}" stroke="#444" stroke-width="0.5" />',
        f'<text x="{x + SWATCH_W / 2:.1f}" y="{y - 6:.1f}" '
        f'font-family="-apple-system, system-ui, sans-serif" font-size="11" '
        f'fill="#222" text-anchor="middle">{_esc(col_label)}</text>',
        f'<text x="{x + SWATCH_W / 2:.1f}" y="{y + SWATCH_H / 2 - 4:.1f}" '
        f'font-family="-apple-system, system-ui, monospace" font-size="14" '
        f'font-weight="600" fill="{text}" text-anchor="middle">{bg.upper() if bg != DATAFIELD_BG else text.upper()}</text>',
        f'<text x="{x + SWATCH_W / 2:.1f}" y="{y + SWATCH_H / 2 + 14:.1f}" '
        f'font-family="-apple-system, system-ui, sans-serif" font-size="12" '
        f'fill="{text}" text-anchor="middle">Lc={lc:+.1f}</text>',
    ])


def _row_svg(y: float, label: str, entries: list[tuple[str, str]], transform: Transform) -> str:
    parts = [
        f'<text x="{H_MARGIN}" y="{y + SWATCH_H / 2 + 4:.1f}" '
        f'font-family="-apple-system, system-ui, sans-serif" font-size="13" '
        f'font-weight="600" fill="#222">{_esc(label)}</text>',
    ]
    for i, (col_label, palette_hex) in enumerate(entries):
        bg, text = transform(palette_hex)
        x = H_MARGIN + ROW_LABEL_W + i * (SWATCH_W + LABEL_GAP)
        parts.append(_swatch_svg(x, y, bg, text, col_label))
    return "\n".join(parts)


def _section_svg(
    y: float,
    title: str,
    rows: list[tuple[str, list[tuple[str, str]]]],
    transform: Transform,
) -> tuple[str, float]:
    parts = [
        f'<text x="{H_MARGIN}" y="{y + 16:.1f}" '
        f'font-family="-apple-system, system-ui, sans-serif" font-size="16" '
        f'font-weight="700" fill="#111">{_esc(title)}</text>',
    ]
    row_y = y + SECTION_HEADER_H + LABEL_GAP
    for label, entries in rows:
        parts.append(_row_svg(row_y, label, entries, transform))
        row_y += SWATCH_H + ROW_GAP
    return "\n".join(parts), row_y


def render_svg(title: str, subtitle: str, sections: list[Section], transform: Transform) -> str:
    max_cols = max(
        (len(entries) for _, rows in sections for _, entries in rows),
        default=1,
    )
    width = H_MARGIN * 2 + ROW_LABEL_W + max_cols * (SWATCH_W + LABEL_GAP) - LABEL_GAP

    parts: list[str] = []
    y = V_MARGIN
    parts.append(
        f'<text x="{H_MARGIN}" y="{y + 20:.1f}" '
        f'font-family="-apple-system, system-ui, sans-serif" font-size="20" '
        f'font-weight="700" fill="#111">{_esc(title)}</text>'
    )
    parts.append(
        f'<text x="{H_MARGIN}" y="{y + 40:.1f}" '
        f'font-family="-apple-system, system-ui, sans-serif" font-size="12" '
        f'fill="#444">{_esc(subtitle)}</text>'
    )
    y += TITLE_H + 28

    for section_title, rows in sections:
        section_svg, y = _section_svg(y, section_title, rows, transform)
        parts.append(section_svg)
        y += SECTION_GAP

    height = y + V_MARGIN
    header = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{int(width)}" height="{int(height)}" '
        f'viewBox="0 0 {int(width)} {int(height)}" '
        f'style="background:#FAFAFA">'
    )
    return header + "\n" + "\n".join(parts) + "\n</svg>\n"


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def main() -> None:
    out_dir = Path(__file__).parent / "output"
    out_dir.mkdir(exist_ok=True)

    outputs = [
        (
            "bg_text_picks.svg",
            "BACKGROUND mode — runtime picker (white vs black by max APCA |Lc|)",
            "Hex + Lc rendered with the picked text color. Unreadable text means the picker failed.",
            bg_view_sections(),
            bg_picker,
        ),
        (
            "bg_text_default.svg",
            "BACKGROUND mode — old default (always white, dark system mode)",
            "Same fills as bg_text_picks.svg, but text is forced to white. Compare side by side: where "
            "this one is hard to read but the picker version isn't, the picker matters. Readable palette "
            "variants exist to soften the worst cases here — but the picker now handles them automatically.",
            bg_view_sections(),
            bg_default_white,
        ),
        (
            "text_mode_palettes.svg",
            "TEXT mode — palette color drawn as text on the datafield bg (#000000)",
            "This is where the *_READABLE palette variants still pay off: text color IS the palette color, "
            "and the bg is fixed. Compare each palette's regular vs readable row.",
            text_view_sections(),
            text_on_datafield,
        ),
    ]

    for filename, title, subtitle, sections, transform in outputs:
        path = out_dir / filename
        path.write_text(render_svg(title, subtitle, sections, transform), encoding="utf-8")
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
