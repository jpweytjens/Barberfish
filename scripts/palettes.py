"""
Shared palette data + APCA/HSLuv helpers.

Parses palette literals out of ``ZoneColoring.kt`` and ``FieldColors.kt`` so
the Kotlin sources stay the single source of truth. Other scripts (palette
APCA correction, README SVG generator, diagnostic visualizer) import from
this module rather than duplicating palette data.

Backgrounds the project cares about:

- ``DATAFIELD_BG_DARK`` = ``#000000`` — the actual datafield background in the
  Karoo rideapp when the system is in night mode.
- ``DATAFIELD_BG_LIGHT`` = ``#FFFFFF`` — day-mode datafield background.

Usage
-----
>>> from palettes import POWER_PALETTES, adjust_for_readability, DATAFIELD_BG_LIGHT
>>> adjust_for_readability("#1A8C3A", DATAFIELD_BG_LIGHT)
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Literal

import hsluv

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

_SHARED = (
    Path(__file__).parent.parent
    / "app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared"
)

ZONE_COLORING_KT = _SHARED / "ZoneColoring.kt"
FIELD_COLORS_KT = _SHARED / "FieldColors.kt"

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

DATAFIELD_BG_DARK = "#000000"
DATAFIELD_BG_LIGHT = "#FFFFFF"
WHITE = "#FFFFFF"
BLACK = "#000000"
MIN_LC = 45.0

POWER_ZONE_LABELS = [f"Z{i}" for i in range(1, 8)]
HR_ZONE_LABELS = [f"Z{i}" for i in range(1, 6)]

# Legacy alias — pre-existing scripts referenced KAROO_DARK as the contrast
# background. Kept for compatibility.
KAROO_DARK = DATAFIELD_BG_DARK


# ---------------------------------------------------------------------------
# APCA-W3 v0.1.7
# ---------------------------------------------------------------------------


def _luminance(r: float, g: float, b: float) -> float:
    return r**2.4 * 0.2126729 + g**2.4 * 0.7151522 + b**2.4 * 0.0721750


def _soft_clamp(y: float) -> float:
    return y + (0.022 - y) ** 1.414 if y < 0.022 else y


def apca_contrast(text_hex: str, bg_hex: str) -> float:
    """Compute the APCA-W3 Lc contrast between two colors.

    Parameters
    ----------
    text_hex, bg_hex : str
        Colors as ``#RRGGBB``.

    Returns
    -------
    float
        Lc value. Positive = dark text on light bg; negative = light text on
        dark bg. ``|Lc| >= 45`` is the minimum for large text (~18 sp+).
    """
    yt = _soft_clamp(_luminance(*_hex_to_rgb(text_hex)))
    yb = _soft_clamp(_luminance(*_hex_to_rgb(bg_hex)))
    if abs(yb - yt) < 0.0005:
        return 0.0
    sapc = (yb**0.56 - yt**0.57) * 1.14 if yt < yb else (yb**0.65 - yt**0.62) * 1.14
    if abs(sapc) < 0.1:
        return 0.0
    return (sapc - 0.027) * 100 if sapc > 0 else (sapc + 0.027) * 100


def is_readable(
    text_hex: str,
    bg_hex: str | None = None,
    min_lc: float = MIN_LC,
) -> bool:
    """Return True if APCA |Lc| meets ``min_lc``. ``bg_hex`` defaults to dark."""
    return abs(apca_contrast(text_hex, bg_hex or DATAFIELD_BG_DARK)) >= min_lc


def best_text_on_background(bg_hex: str) -> str:
    """Return whichever of WHITE / BLACK has the higher APCA |Lc| against ``bg_hex``."""
    return WHITE if abs(apca_contrast(WHITE, bg_hex)) >= abs(apca_contrast(BLACK, bg_hex)) else BLACK


# ---------------------------------------------------------------------------
# Conversions
# ---------------------------------------------------------------------------


def _hex_to_rgb(hex_color: str) -> tuple[float, float, float]:
    h = hex_color.lstrip("#")
    return tuple(int(h[i : i + 2], 16) / 255.0 for i in (0, 2, 4))  # type: ignore[return-value]


def _rgb_to_hex(r: float, g: float, b: float) -> str:
    return "#{:02X}{:02X}{:02X}".format(int(r * 255), int(g * 255), int(b * 255))


def _rgb_to_hsl(r: float, g: float, b: float) -> tuple[float, float, float]:
    mx, mn = max(r, g, b), min(r, g, b)
    l = (mx + mn) / 2
    if mx == mn:
        return (0.0, 0.0, l)
    d = mx - mn
    s = d / (2 - mx - mn) if l > 0.5 else d / (mx + mn)
    if mx == r:
        h = ((g - b) / d + (6 if g < b else 0)) / 6
    elif mx == g:
        h = ((b - r) / d + 2) / 6
    else:
        h = ((r - g) / d + 4) / 6
    return (h, s, l)


def _hsl_to_rgb(h: float, s: float, l: float) -> tuple[float, float, float]:
    if s == 0:
        return (l, l, l)

    def _hue2rgb(p: float, q: float, t: float) -> float:
        if t < 0:
            t += 1
        if t > 1:
            t -= 1
        if t < 1 / 6:
            return p + (q - p) * 6 * t
        if t < 1 / 2:
            return q
        if t < 2 / 3:
            return p + (q - p) * (2 / 3 - t) * 6
        return p

    q = l * (1 + s) if l < 0.5 else l + s - l * s
    p = 2 * l - q
    return (_hue2rgb(p, q, h + 1 / 3), _hue2rgb(p, q, h), _hue2rgb(p, q, h - 1 / 3))


def _hex_to_hsl(hex_color: str) -> tuple[float, float, float]:
    return _rgb_to_hsl(*_hex_to_rgb(hex_color))


def _hsl_to_hex(h: float, s: float, l: float) -> str:
    return _rgb_to_hex(*_hsl_to_rgb(h, s, l))


def _hsluv_to_hex(h: float, s: float, l: float) -> str:
    return hsluv.hsluv_to_hex((h, s, l))


# ---------------------------------------------------------------------------
# Contrast correction
# ---------------------------------------------------------------------------


def adjust_for_readability(
    hex_color: str,
    bg_hex: str | None = None,
    min_lc: float = MIN_LC,
    space: Literal["hsl", "hsluv"] = "hsluv",
) -> str:
    """Move HSLuv lightness until ``|Lc| >= min_lc`` against ``bg_hex``.

    Hue and saturation stay fixed. The search direction depends on the
    background: for a dark background the input is too dark, so lightness is
    raised; for a light background it is too light, so lightness is lowered.

    Parameters
    ----------
    hex_color : str
        Color to adjust as ``#RRGGBB``.
    bg_hex : str or None, optional
        Background to test against. Defaults to ``DATAFIELD_BG_DARK``.
    min_lc : float, optional
        Minimum |Lc| required.
    space : {'hsluv', 'hsl'}, optional
        Color space for the lightness search.

    Returns
    -------
    str
        Corrected color as ``#RRGGBB``, uppercased. Returns the original
        unchanged if it already meets ``min_lc``.
    """
    bg = bg_hex or DATAFIELD_BG_DARK
    if is_readable(hex_color, bg, min_lc):
        return hex_color.upper()

    if space == "hsluv":
        to_hsl = hsluv.hex_to_hsluv
        to_hex = _hsluv_to_hex
        l_min, l_max = 0.0, 100.0
        iterations = 30
    else:
        to_hsl = _hex_to_hsl
        to_hex = _hsl_to_hex
        l_min, l_max = 0.0, 1.0
        iterations = 20

    h, s, l = to_hsl(hex_color)
    # Lighten when the bg is dark (text too dark); darken when bg is light.
    bg_is_dark = abs(apca_contrast(WHITE, bg)) >= abs(apca_contrast(BLACK, bg))
    if bg_is_dark:
        lo, hi = l, l_max
    else:
        lo, hi = l_min, l

    for _ in range(iterations):
        mid = (lo + hi) / 2
        if is_readable(to_hex(h, s, mid), bg, min_lc):
            if bg_is_dark:
                hi = mid
            else:
                lo = mid
        else:
            if bg_is_dark:
                lo = mid
            else:
                hi = mid

    final = hi if bg_is_dark else lo
    return to_hex(h, s, final).upper()


# ---------------------------------------------------------------------------
# Kotlin source parsers
# ---------------------------------------------------------------------------

# `val NAME = REST` — REST may be empty (body on next line), `listOf(...` (body
# opener on same line), or a derived expression (`PARENT.take(N)` /
# `listOf(i, j, ...).map { PARENT[it] }`).
_VAL_DECL_RE = re.compile(r"^(?:internal|private)\s+val\s+(\w+)\s*=\s*(.*)$")
_COLOR_RE = re.compile(r"Color\(0xFF([0-9A-Fa-f]{6})\)")
_TAKE_RE = re.compile(r"^(\w+)\.take\((\d+)\)\s*$")
_INDEX_MAP_RE = re.compile(r"^listOf\(\s*([\d,\s]+)\)\.map\s*\{\s*(\w+)\[it\]\s*\}\s*$")


def parse_palettes(path: Path = ZONE_COLORING_KT) -> dict[str, list[str]]:
    """Parse every Kotlin ``val NAME = listOf(Color(...), ...)`` block.

    Handles three declaration shapes:

    - Multi-line literal: ``val NAME =\\n    listOf(\\n        Color(0xFF...),\\n    )``
    - Single-line opener: ``val NAME = listOf(\\n        Color(0xFF...),\\n    )``
    - Derived: ``val NAME = PARENT.take(N)`` or
      ``val NAME = listOf(i, j, ...).map { PARENT[it] }``

    Returns
    -------
    dict[str, list[str]]
        Mapping ``kotlin_var_name -> ["#RRGGBB", ...]``.
    """
    palettes: dict[str, list[str]] = {}
    current_name: str | None = None

    for line in path.read_text(encoding="utf-8").splitlines():
        if current_name is None:
            decl = _VAL_DECL_RE.match(line)
            if not decl:
                continue
            name, rest = decl.group(1), decl.group(2).strip()

            if not rest:
                # Body on following line; tentatively open and let the next
                # iteration find `listOf(`.
                current_name = name
                palettes[name] = []
                continue

            take_match = _TAKE_RE.match(rest)
            if take_match:
                parent, n = take_match.group(1), int(take_match.group(2))
                if parent in palettes:
                    palettes[name] = palettes[parent][:n]
                continue

            idx_match = _INDEX_MAP_RE.match(rest)
            if idx_match:
                indices = [int(x) for x in idx_match.group(1).split(",") if x.strip()]
                parent = idx_match.group(2)
                if parent in palettes:
                    palettes[name] = [palettes[parent][i] for i in indices]
                continue

            if "listOf(" in rest:
                current_name = name
                palettes[name] = []
                # First color may already be on this line (rare).
                m = _COLOR_RE.search(rest)
                if m:
                    palettes[name].append(f"#{m.group(1).upper()}")
                continue

            # Anything else (function defs, scalar vals, etc.) — skip.
            continue

        # current_name is set — we are inside a literal block body.
        if "listOf(" in line and not palettes[current_name]:
            continue

        if line.strip().startswith(")"):
            if not palettes[current_name]:
                del palettes[current_name]
            current_name = None
            continue

        m = _COLOR_RE.search(line)
        if m:
            palettes[current_name].append(f"#{m.group(1).upper()}")

    return {name: colors for name, colors in palettes.items() if colors}


# ---------------------------------------------------------------------------
# Grade bands
# ---------------------------------------------------------------------------

_GRADE_OPEN_RE = re.compile(
    r"^\s*(?:private\s+)?val\s+(\w+_GRADE_BANDS\w*)\s*=\s*listOf\("
)
# Threshold can be a signed float (e.g. ``-9.0``) or the literal
# ``Double.NEGATIVE_INFINITY`` sentinel used by the Turbo palette.
_THRESHOLD = r"(-?\d+(?:\.\d+)?|Double\.NEGATIVE_INFINITY)"
_GRADE_BAND_RE = re.compile(rf"{_THRESHOLD}\s+to\s+Color\(0xFF([0-9A-Fa-f]{{6}})\)")
_GRADE_BAND_REF_RE = re.compile(rf"{_THRESHOLD}\s+to\s+(\w+)\[(\d+)\]")


def _parse_threshold(raw: str) -> float:
    """Parse a Kotlin threshold token as a Python float."""
    if raw == "Double.NEGATIVE_INFINITY":
        return float("-inf")
    return float(raw)


def format_threshold(value: float) -> str:
    """Format a Python float back as a Kotlin threshold token."""
    if value == float("-inf"):
        return "Double.NEGATIVE_INFINITY"
    return repr(value)


def parse_grade_bands(
    path: Path = FIELD_COLORS_KT,
    palettes: dict[str, list[str]] | None = None,
) -> dict[str, list[tuple[float, str]]]:
    """Parse grade band lists out of ``FieldColors.kt``.

    Resolves references to power palettes (e.g. ``karooPowerColorsReadableDark[3]``)
    using ``palettes`` (typically the result of :func:`parse_palettes`).

    Returns
    -------
    dict[str, list[tuple[float, str]]]
        Mapping band name → list of ``(threshold, "#RRGGBB")`` pairs in source
        order.
    """
    palettes = palettes or parse_palettes()
    bands: dict[str, list[tuple[float, str]]] = {}
    current_name: str | None = None

    for line in path.read_text(encoding="utf-8").splitlines():
        open_match = _GRADE_OPEN_RE.match(line)
        if open_match:
            current_name = open_match.group(1)
            bands[current_name] = []
            continue

        if current_name is None:
            continue

        if line.strip().startswith(")"):
            current_name = None
            continue

        literal = _GRADE_BAND_RE.search(line)
        if literal:
            bands[current_name].append(
                (_parse_threshold(literal.group(1)), f"#{literal.group(2).upper()}")
            )
            continue

        ref = _GRADE_BAND_REF_RE.search(line)
        if ref:
            threshold = _parse_threshold(ref.group(1))
            parent, idx = ref.group(2), int(ref.group(3))
            if parent in palettes:
                bands[current_name].append((threshold, palettes[parent][idx]))

    return {name: entries for name, entries in bands.items() if entries}


# ---------------------------------------------------------------------------
# High-level dicts built once on import (Kotlin-name keyed and display-keyed)
# ---------------------------------------------------------------------------

_ALL_PALETTES: dict[str, list[str]] = parse_palettes()
_ALL_GRADE_BANDS: dict[str, list[tuple[float, str]]] = parse_grade_bands(
    palettes=_ALL_PALETTES
)


def _display_name(kotlin_name: str) -> str:
    """Map Kotlin variable name → human label for legacy script keys.

    Examples
    --------
    karooPowerColors                  -> "Karoo"
    karooPowerColorsReadable          -> "Karoo (readable)"           # legacy
    karooPowerColorsReadableDark      -> "Karoo (readable dark)"
    karooPowerColorsReadableLight     -> "Karoo (readable light)"
    """
    name = kotlin_name
    # Strip the *Colors / *PowerColors / *HrColors suffix tail.
    name = re.sub(r"(Power|Hr)Colors", "Colors", name)
    if name.endswith("ColorsReadableDark"):
        base = name[: -len("ColorsReadableDark")]
        suffix = " (readable dark)"
    elif name.endswith("ColorsReadableLight"):
        base = name[: -len("ColorsReadableLight")]
        suffix = " (readable light)"
    elif name.endswith("ColorsReadable"):
        base = name[: -len("ColorsReadable")]
        suffix = " (readable)"
    elif name.endswith("Colors"):
        base = name[: -len("Colors")]
        suffix = ""
    else:
        base, suffix = name, ""
    return base[:1].upper() + base[1:] + suffix


def _by_kind(kind: Literal["Power", "Hr"]) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    for kotlin_name, colors in _ALL_PALETTES.items():
        if kind in kotlin_name:
            out[_display_name(kotlin_name)] = colors
    return out


POWER_PALETTES: dict[str, list[str]] = _by_kind("Power")
HR_PALETTES: dict[str, list[str]] = _by_kind("Hr")
PALETTES_BY_KOTLIN_NAME: dict[str, list[str]] = dict(_ALL_PALETTES)
GRADE_BANDS_BY_KOTLIN_NAME: dict[str, list[tuple[float, str]]] = dict(_ALL_GRADE_BANDS)


def base_palette_names(kind: Literal["Power", "Hr"]) -> list[str]:
    """Return Kotlin variable names of base (non-readable, non-HSLuv) palettes."""
    return [
        n
        for n in _ALL_PALETTES
        if kind in n
        and "Readable" not in n
        and "hsluv" not in n.lower()
    ]


def grade_band_names() -> list[str]:
    """Return Kotlin variable names of base (non-readable) grade band lists."""
    return [
        n
        for n in _ALL_GRADE_BANDS
        if not n.endswith("_READABLE")
        and not n.endswith("_READABLE_DARK")
        and not n.endswith("_READABLE_LIGHT")
        and "HSLUV" not in n
    ]
