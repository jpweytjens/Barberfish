"""
Diagnostic: visualize Barberfish palette readability under three rules.

Outputs three SVGs to ``scripts/output/`` so you can eyeball them side by side:

1. ``bg_text_picks.svg``
   BACKGROUND color mode with the runtime picker (``bestTextOnBackground`` in
   ``app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ZoneColoring.kt``).
   Each fill gets whichever of white/black has the higher APCA |Lc|.

2. ``bg_text_default.svg``
   BACKGROUND color mode with the old behavior — always-white text (dark-mode
   default). Side-by-side with (1) to show where the picker matters most.

3. ``text_mode_palettes.svg``
   TEXT color mode — palette color used as text on the actual datafield bg
   (``#000000``). Shows where the readable variants still pay off.

Not a code generator. Not a README asset. Open the SVGs in a browser.

Usage
-----
uv run scripts/preview_bg_text_picks.py
"""

from __future__ import annotations

import math
from collections.abc import Callable
from pathlib import Path

from palettes import (
    BLACK,
    GRADE_BANDS_BY_KOTLIN_NAME,
    HR_PALETTES,
    HR_ZONE_LABELS,
    POWER_PALETTES,
    POWER_ZONE_LABELS,
    WHITE,
    apca_contrast,
    best_text_on_background,
)
from palettes import (
    DATAFIELD_BG_DARK as DATAFIELD_BG,
)

# ---------------------------------------------------------------------------
# Grade palette display labels — derived from thresholds in FieldColors.kt
# ---------------------------------------------------------------------------


def _grade_display_name(kotlin_name: str) -> str:
    """``KAROO_GRADE_BANDS`` -> ``"Karoo"``; ``..._READABLE_DARK`` -> ``"Karoo (readable dark)"``."""
    suffix = ""
    base = kotlin_name.replace("_GRADE_BANDS", "")
    if base.endswith("_READABLE_DARK"):
        base, suffix = base[: -len("_READABLE_DARK")], " (readable dark)"
    elif base.endswith("_READABLE_LIGHT"):
        base, suffix = base[: -len("_READABLE_LIGHT")], " (readable light)"
    elif base.endswith("_READABLE"):
        base, suffix = base[: -len("_READABLE")], " (readable)"
    return base.capitalize() + suffix


def _threshold_label(t: float) -> str:
    if t == float("-inf"):
        return "≪0%"
    return f"≥{t:g}%"


GRADE_PALETTES: dict[str, list[tuple[str, str]]] = {
    _grade_display_name(name): [(_threshold_label(t), hex_) for t, hex_ in entries]
    for name, entries in GRADE_BANDS_BY_KOTLIN_NAME.items()
}


# ---------------------------------------------------------------------------
# Threshold / danger gradient palettes (computed; not stored in Kotlin)
# ---------------------------------------------------------------------------

_RDYLGN_RED = "#D73027"
_RDYLGN_GREEN = "#1A9850"
_DANGER_ORANGE = "#FFA726"

THRESHOLD_FACTORS = [i / 10 for i in range(-10, 11)]  # -1.0 .. +1.0 step 0.1


def _hex_to_rgb(h: str) -> tuple[float, float, float]:
    h = h.lstrip("#")
    return tuple(int(h[i : i + 2], 16) / 255.0 for i in (0, 2, 4))  # type: ignore[return-value]


def _rgb_to_hex(r: float, g: float, b: float) -> str:
    return f"#{max(0, min(255, round(r * 255))):02X}{max(0, min(255, round(g * 255))):02X}{max(0, min(255, round(b * 255))):02X}"


def _lerp_hex(a_hex: str, b_hex: str, t: float) -> str:
    a, b = _hex_to_rgb(a_hex), _hex_to_rgb(b_hex)
    return _rgb_to_hex(*[a[i] + (b[i] - a[i]) * t for i in range(3)])


def _threshold_bg(factor: float, is_night: bool) -> str:
    """Mirrors ``thresholdBackgroundColor`` in FieldColors.kt."""
    neutral = BLACK if is_night else WHITE
    end = _RDYLGN_GREEN if factor >= 0 else _RDYLGN_RED
    return _lerp_hex(neutral, end, math.sqrt(abs(factor)))


def _threshold_text(factor: float, is_night: bool) -> str:
    """Mirrors ``thresholdTextColor`` in FieldColors.kt — neutral is swapped."""
    neutral = WHITE if is_night else BLACK
    end = _RDYLGN_GREEN if factor >= 0 else _RDYLGN_RED
    return _lerp_hex(neutral, end, math.sqrt(abs(factor)))


def _danger_color(outside: float, border: float, has_safe: bool) -> str:
    """Mirrors ``dangerZoneColor`` in FieldColors.kt."""
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
            (f"{t:.1f}", _danger_color(outside=t, border=0.0, has_safe=True))
            for t in samples
        ],
        "DangerZone border (safe zone, 0 → 1, GREEN → ORANGE)": [
            (f"{t:.1f}", _danger_color(outside=0.0, border=t, has_safe=True))
            for t in samples
        ],
        "DangerZone border (one-sided, 0 → 1, WHITE → ORANGE)": [
            (f"{t:.1f}", _danger_color(outside=0.0, border=t, has_safe=False))
            for t in samples
        ],
    }


# ---------------------------------------------------------------------------
# Section assembly
# ---------------------------------------------------------------------------

# Each section: (title, list of rows). Each row: (label, list of (col_label, palette_hex)).
Section = tuple[str, list[tuple[str, list[tuple[str, str]]]]]


def _zone_sections(
    palettes: dict[str, list[str]], col_labels: list[str]
) -> list[tuple[str, list[tuple[str, str]]]]:
    return [(name, list(zip(col_labels, hexes))) for name, hexes in palettes.items()]


def bg_view_sections() -> list[Section]:
    return [
        ("Power zones", _zone_sections(POWER_PALETTES, POWER_ZONE_LABELS)),
        ("HR zones", _zone_sections(HR_PALETTES, HR_ZONE_LABELS)),
        ("Grade bands", list(GRADE_PALETTES.items())),
        ("Threshold gradient", list(_threshold_palettes_bg().items())),
        ("DangerZone gradient", list(_danger_palettes().items())),
    ]


def text_view_sections() -> list[Section]:
    return [
        ("Power zones", _zone_sections(POWER_PALETTES, POWER_ZONE_LABELS)),
        ("HR zones", _zone_sections(HR_PALETTES, HR_ZONE_LABELS)),
        ("Grade bands", list(GRADE_PALETTES.items())),
        ("Threshold gradient", list(_threshold_palettes_text().items())),
        ("DangerZone gradient", list(_danger_palettes().items())),
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
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _swatch_svg(x: float, y: float, bg: str, text: str, col_label: str) -> str:
    lc = apca_contrast(text, bg)
    return "\n".join(
        [
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
        ]
    )


def _row_svg(
    y: float, label: str, entries: list[tuple[str, str]], transform: Transform
) -> str:
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


def render_svg(
    title: str, subtitle: str, sections: list[Section], transform: Transform
) -> str:
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
            "this one is hard to read but the picker version isn't, the picker matters.",
            bg_view_sections(),
            bg_default_white,
        ),
        (
            "text_mode_palettes.svg",
            "TEXT mode — palette color drawn as text on the datafield bg (#000000)",
            "This is where the readable palette variants still pay off: text color IS the palette color, "
            "and the bg is fixed. Compare each palette's regular vs readable row.",
            text_view_sections(),
            text_on_datafield,
        ),
    ]

    for filename, title, subtitle, sections, transform in outputs:
        path = out_dir / filename
        path.write_text(
            render_svg(title, subtitle, sections, transform), encoding="utf-8"
        )
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
