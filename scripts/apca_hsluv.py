"""
APCA contrast correction in HSLuv space.

Reads palette colors from ZoneColoring.kt, raises HSLuv L (keeping H and S
fixed) via binary search until |Lc| >= 45 against the Karoo dark background,
and prints corrected *ColorsReadable = listOf(...) blocks ready to paste back.

Usage
-----
uv run scripts/apca_hsluv.py
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Literal
import hsluv

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

_SHARED = Path(__file__).parent.parent / "app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared"

ZONE_COLORING_KT = _SHARED / "ZoneColoring.kt"
FIELD_COLORS_KT = _SHARED / "FieldColors.kt"

# ---------------------------------------------------------------------------
# APCA-W3 v0.1.7
# ---------------------------------------------------------------------------

KAROO_DARK = "#1B2D2D"


def _luminance(r: float, g: float, b: float) -> float:
    return r**2.4 * 0.2126729 + g**2.4 * 0.7151522 + b**2.4 * 0.0721750


def _soft_clamp(y: float) -> float:
    return y + (0.022 - y) ** 1.414 if y < 0.022 else y


def apca_contrast(text_hex: str, bg_hex: str) -> float:
    """Compute the APCA-W3 Lc contrast value between two colors.

    Parameters
    ----------
    text_hex : str
        Foreground color as '#RRGGBB'.
    bg_hex : str
        Background color as '#RRGGBB'.

    Returns
    -------
    float
        Lc value. Positive = dark text on light bg; negative = light text on
        dark bg. |Lc| >= 45 is the minimum for large text (~18 sp+).
    """
    yt = _soft_clamp(_luminance(*_hex_to_rgb(text_hex)))
    yb = _soft_clamp(_luminance(*_hex_to_rgb(bg_hex)))
    if abs(yb - yt) < 0.0005:
        return 0.0
    sapc = (
        (yb**0.56 - yt**0.57) * 1.14
        if yt < yb
        else (yb**0.65 - yt**0.62) * 1.14
    )
    if abs(sapc) < 0.1:
        return 0.0
    return (sapc - 0.027) * 100 if sapc > 0 else (sapc + 0.027) * 100


def is_readable(
    text_hex: str,
    bg_hex: str | None = None,
    min_lc: float = 45.0,
) -> bool:
    """Return True if the APCA contrast meets the minimum threshold.

    Parameters
    ----------
    text_hex : str
        Foreground color as '#RRGGBB'.
    bg_hex : str or None, optional
        Background color as '#RRGGBB'. Defaults to `KAROO_DARK` when None.
    min_lc : float, optional
        Minimum |Lc| required. Default is 45.0.

    Returns
    -------
    bool
    """
    return abs(apca_contrast(text_hex, bg_hex or KAROO_DARK)) >= min_lc


# ---------------------------------------------------------------------------
# Conversion
# ---------------------------------------------------------------------------

def _hex_to_rgb(hex_color: str) -> tuple[float, float, float]:
    """Parse a hex color string into RGB components.

    Parameters
    ----------
    hex_color : str
        Color as '#RRGGBB' (leading '#' optional).

    Returns
    -------
    tuple[float, float, float]
        (r, g, b) each in [0, 1].
    """
    h = hex_color.lstrip("#")
    return tuple(int(h[i : i + 2], 16) / 255.0 for i in (0, 2, 4))  # type: ignore[return-value]


def _rgb_to_hex(r: float, g: float, b: float) -> str:
    """Format RGB components as an uppercase hex color string.

    Parameters
    ----------
    r, g, b : float
        Channel values each in [0, 1].

    Returns
    -------
    str
        Color as '#RRGGBB', uppercase.
    """
    return "#{:02X}{:02X}{:02X}".format(int(r * 255), int(g * 255), int(b * 255))


def _rgb_to_hsl(r: float, g: float, b: float) -> tuple[float, float, float]:
    """Convert RGB to HSL.

    Parameters
    ----------
    r, g, b : float
        Channel values each in [0, 1].

    Returns
    -------
    tuple[float, float, float]
        (h, s, l) where h is in [0, 1), s and l are in [0, 1].
    """
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
    """Convert HSL to RGB.

    Parameters
    ----------
    h : float
        Hue in [0, 1).
    s : float
        Saturation in [0, 1].
    l : float
        Lightness in [0, 1].

    Returns
    -------
    tuple[float, float, float]
        (r, g, b) each in [0, 1].
    """
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
    """Convert a hex color string to HSL.

    Parameters
    ----------
    hex_color : str
        Color as '#RRGGBB'.

    Returns
    -------
    tuple[float, float, float]
        (h, s, l) where h is in [0, 1), s and l are in [0, 1].
    """
    return _rgb_to_hsl(*_hex_to_rgb(hex_color))


def _hsl_to_hex(h: float, s: float, l: float) -> str:
    """Convert HSL to a hex color string.

    Parameters
    ----------
    h : float
        Hue in [0, 1).
    s : float
        Saturation in [0, 1].
    l : float
        Lightness in [0, 1].

    Returns
    -------
    str
        Color as '#RRGGBB', uppercase.
    """
    return _rgb_to_hex(*_hsl_to_rgb(h, s, l))


def _hsluv_to_hex(h: float, s: float, l: float) -> str:
    """Convert HSLuv components to a hex color string.

    Parameters
    ----------
    h : float
        Hue in [0, 360].
    s : float
        Saturation in [0, 100].
    l : float
        Lightness in [0, 100].

    Returns
    -------
    str
        Color as '#RRGGBB'.
    """
    return hsluv.hsluv_to_hex((h, s, l))


# ---------------------------------------------------------------------------
# Contrast correction
# ---------------------------------------------------------------------------

def adjust_for_readability(
    hex_color: str,
    bg_hex: str | None = None,
    min_lc: float = 45.0,
    space: Literal["hsl", "hsluv"] = "hsluv",
) -> str:
    """Raise lightness until |Lc| >= min_lc. Returns original if already readable.

    The binary search keeps hue and saturation fixed and raises only the
    lightness component. `space` selects the color space: `'hsluv'`
    (default) is perceptually uniform and preserves hue fidelity;
    `'hsl'` mirrors the original Kotlin `adjustedForReadability()`.

    Parameters
    ----------
    hex_color : str
        Color to adjust, as '#RRGGBB'.
    bg_hex : str or None, optional
        Background to test against. Defaults to `KAROO_DARK` when None.
    min_lc : float, optional
        Minimum |Lc| required. Default is 45.0.
    space : {'hsluv', 'hsl'}, optional
        Color space for the lightness search. Default is `'hsluv'`.

    Returns
    -------
    str
        Corrected color as '#RRGGBB', uppercased.
    """
    if is_readable(hex_color, bg_hex, min_lc):
        return hex_color

    if space == "hsluv":
        to_hsl = hsluv.hex_to_hsluv
        to_hex = _hsluv_to_hex
        l_max = 100.0
        iterations = 30
    else:
        to_hsl = _hex_to_hsl
        to_hex = _hsl_to_hex
        l_max = 1.0
        iterations = 20

    h, s, l = to_hsl(hex_color)
    lo, hi = l, l_max
    for _ in range(iterations):
        mid = (lo + hi) / 2
        if is_readable(to_hex(h, s, mid), bg_hex, min_lc):
            hi = mid
        else:
            lo = mid
    return to_hex(h, s, hi).upper()


# ---------------------------------------------------------------------------
# Parse ZoneColoring.kt
# ---------------------------------------------------------------------------

# Matches e.g. `internal val karooPowerColors =`
_VAL_RE = re.compile(r"^\s*(?:internal\s+)?val\s+(\w+)\s*=\s*$")
# Matches a Color(0xFFRRGGBB) entry with an optional trailing comment
_COLOR_RE = re.compile(
    r"Color\(0xFF([0-9A-Fa-f]{6})\)"
    r"(?:[^/]*)?"
    r"(//.*)?$"
)


def parse_palettes(path: Path) -> dict[str, list[tuple[str, str]]]:
    """Extract color palettes from a ZoneColoring.kt source file.

    Skips HSLuv palettes (already perceptually designed) and any palette
    whose name ends in `Readable` (already corrected).

    Parameters
    ----------
    path : Path
        Path to `ZoneColoring.kt`.

    Returns
    -------
    dict[str, list[tuple[str, str]]]
        Mapping of palette variable name to a list of `(hex, comment)`
        pairs, where `hex` is uppercase 'RRGGBB' and `comment` is the
        trailing inline comment (empty string if absent).
    """
    palettes: dict[str, list[tuple[str, str]]] = {}
    current_name: str | None = None
    in_list = False

    for line in path.read_text(encoding="utf-8").splitlines():
        # Detect `val <name> =` (standalone — value is on the next line)
        val_match = _VAL_RE.match(line)
        if val_match:
            name = val_match.group(1)
            # Skip HSLuv palettes and already-readable palettes
            if "hsluv" in name.lower() or name.endswith("Readable"):
                current_name = None
            elif name.endswith("Colors"):
                current_name = name
            else:
                current_name = None
            in_list = False
            continue

        if current_name is None:
            continue

        if "listOf(" in line:
            in_list = True
            palettes[current_name] = []
            continue

        if in_list:
            if line.strip().startswith(")"):
                in_list = False
                current_name = None
                continue
            color_match = _COLOR_RE.search(line)
            if color_match:
                hex_val = color_match.group(1).upper()
                comment = (color_match.group(2) or "").strip()
                palettes[current_name].append((hex_val, comment))

    return palettes


# Matches e.g. `private val WAHOO_GRADE_BANDS = listOf(` (listOf on same line)
_GRADE_VAL_RE = re.compile(r"^\s*(?:private\s+)?val\s+(\w+_GRADE_BANDS)\s*=\s*listOf\(")
# Matches `X.X to Color(0xFFRRGGBB)` with an optional trailing comment
_GRADE_BAND_RE = re.compile(
    r"([\d.]+)\s+to\s+Color\(0xFF([0-9A-Fa-f]{6})\)"
    r"(?:[^/]*)?"
    r"(//.*)?$"
)


def parse_grade_bands(path: Path) -> dict[str, list[tuple[float, str, str]]]:
    """Extract grade band palettes from a FieldColors.kt source file.

    Skips HSLuv bands (already perceptually designed), Karoo bands (colors
    are references to ``karooPowerColors``, not inline hex), and any band
    whose name ends in ``READABLE`` (already corrected).

    Parameters
    ----------
    path : Path
        Path to ``FieldColors.kt``.

    Returns
    -------
    dict[str, list[tuple[float, str, str]]]
        Mapping of band variable name to a list of ``(threshold, hex, comment)``
        triples, where ``threshold`` is the grade percentage lower bound,
        ``hex`` is uppercase 'RRGGBB', and ``comment`` is the trailing inline
        comment (empty string if absent).
    """
    bands: dict[str, list[tuple[float, str, str]]] = {}
    current_name: str | None = None
    in_list = False

    for line in path.read_text(encoding="utf-8").splitlines():
        val_match = _GRADE_VAL_RE.match(line)
        if val_match:
            name = val_match.group(1)
            if "HSLUV" in name or "KAROO" in name or name.endswith("READABLE"):
                current_name = None
                in_list = False
            else:
                current_name = name
                in_list = True
                bands[name] = []
            continue

        if current_name is None:
            continue

        if in_list:
            if line.strip().startswith(")"):
                in_list = False
                current_name = None
                continue
            band_match = _GRADE_BAND_RE.search(line)
            if band_match:
                threshold = float(band_match.group(1))
                hex_val = band_match.group(2).upper()
                comment = (band_match.group(3) or "").strip()
                bands[current_name].append((threshold, hex_val, comment))

    return {name: entries for name, entries in bands.items() if entries}


# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

def _print_comparison_rows(hex_values: list[str], bg: str, min_lc: float) -> None:
    header = f"{'Original':<10}  {'HSL':<10}  {'HSLuv':<10}  {'Δ':<4}  {'Lc(HSL)':<10}  Lc(HSLuv)"
    sep = "-" * len(header)
    print(sep)
    print(header)
    print(sep)
    for original in hex_values:
        hsl_result = adjust_for_readability(original, bg, min_lc, space="hsl")
        hsluv_result = adjust_for_readability(original, bg, min_lc, space="hsluv")
        lc_hsl = apca_contrast(hsl_result, bg)
        lc_hsluv = apca_contrast(hsluv_result, bg)
        same = "=" if hsl_result.upper() == hsluv_result.upper() else "≠"
        print(
            f"{original:<10}  {hsl_result:<10}  {hsluv_result:<10}"
            f"  {same:<4}  {lc_hsl:<10.1f}  {lc_hsluv:.1f}"
        )


def print_comparison(
    palettes: dict[str, list[tuple[str, str]]],
    grade_bands: dict[str, list[tuple[float, str, str]]] | None = None,
    bg_hex: str | None = None,
    min_lc: float = 45.0,
) -> None:
    """Print a side-by-side comparison of HSL vs HSLuv APCA correction.

    Parameters
    ----------
    palettes : dict[str, list[tuple[str, str]]]
        Zone palette data as returned by `parse_palettes`.
    grade_bands : dict[str, list[tuple[float, str, str]]] or None, optional
        Grade band data as returned by `parse_grade_bands`. Printed after
        zone palettes when provided.
    bg_hex : str or None, optional
        Background to test against. Defaults to `KAROO_DARK` when None.
    min_lc : float, optional
        Minimum |Lc| required. Default is 45.0.
    """
    bg = bg_hex or KAROO_DARK
    for name, colors in palettes.items():
        print(f"\n{name}")
        _print_comparison_rows([f"#{hex_val}" for hex_val, _ in colors], bg, min_lc)
    if grade_bands:
        for name, entries in grade_bands.items():
            print(f"\n{name}")
            _print_comparison_rows([f"#{hex_val}" for _, hex_val, _ in entries], bg, min_lc)


def main() -> None:
    """Parse palettes, apply HSLuv APCA correction, and print Kotlin output.

    Reads `ZoneColoring.kt` and `FieldColors.kt`, corrects each palette color
    so that `|Lc| >= 45` against the Karoo dark background, and prints
    `*ColorsReadable = listOf(...)` / `*_READABLE = listOf(...)` blocks to
    stdout. Colors that already pass are printed unchanged; corrected colors
    include a `// was #XXXXXX` annotation.
    """
    palettes = parse_palettes(ZONE_COLORING_KT)
    for name, colors in palettes.items():
        readable_name = name.replace("Colors", "ColorsReadable")
        print(f"internal val {readable_name} = listOf(")
        for hex_val, comment in colors:
            original = f"#{hex_val}"
            corrected = adjust_for_readability(original)
            corrected_hex = corrected.lstrip("#").upper()
            changed = corrected_hex != hex_val
            suffix = f"  // was #{hex_val}" if changed else ""
            comment_str = f"  {comment}" if comment else ""
            print(f"    Color(0xFF{corrected_hex}),{comment_str}{suffix}")
        print(")")
        print()

    grade_bands = parse_grade_bands(FIELD_COLORS_KT)
    for name, entries in grade_bands.items():
        readable_name = name + "_READABLE"
        print(f"private val {readable_name} = listOf(")
        for threshold, hex_val, comment in entries:
            original = f"#{hex_val}"
            corrected = adjust_for_readability(original)
            corrected_hex = corrected.lstrip("#").upper()
            changed = corrected_hex != hex_val
            suffix = f"  // was #{hex_val}" if changed else ""
            comment_str = f"  {comment}" if comment else ""
            print(f"    {threshold} to Color(0xFF{corrected_hex}),{comment_str}{suffix}")
        print(")")
        print()

    # print_comparison(palettes, grade_bands)


if __name__ == "__main__":
    main()
