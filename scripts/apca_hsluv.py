"""
APCA contrast correction in HSLuv space — emit Kotlin blocks for both
night-mode (#000000) and day-mode (#FFFFFF) backgrounds.

For every base palette in ``ZoneColoring.kt`` and every literal-hex grade
band in ``FieldColors.kt``, HSLuv lightness is moved (hue and saturation
kept fixed) via binary search until ``|Lc| >= 45`` against the target
background. The result is printed as ready-to-paste Kotlin:

    internal val karooPowerColorsReadableDark = listOf(...)
    internal val karooPowerColorsReadableLight = listOf(...)
    ...
    private val WAHOO_GRADE_BANDS_READABLE_DARK = listOf(...)
    private val WAHOO_GRADE_BANDS_READABLE_LIGHT = listOf(...)

Skipped:

- HSLuv palettes (already perceptually designed; one variant fits both modes).
- HR palettes (derived from power palettes in Kotlin via ``.take(N)`` /
  ``listOf(...).map { it }``).
- KAROO grade bands (reference ``karooPowerColors*[i]`` — updates flow
  through from the power palette).

Usage
-----
uv run scripts/apca_hsluv.py
"""

from __future__ import annotations

from palettes import (
    DATAFIELD_BG_DARK,
    DATAFIELD_BG_LIGHT,
    GRADE_BANDS_BY_KOTLIN_NAME,
    MIN_LC,
    PALETTES_BY_KOTLIN_NAME,
    adjust_for_readability,
    base_palette_names,
    format_threshold,
    grade_band_names,
)


# Grade bands handled separately — see module docstring.
_GRADE_SKIP = {"KAROO", "HSLUV"}


def _emit_palette(name: str, colors: list[str], bg: str, suffix: str) -> None:
    """Print a corrected Kotlin power-palette block.

    Parameters
    ----------
    name : str
        Source Kotlin variable name (e.g. ``karooPowerColors``).
    colors : list[str]
        Source colors as ``#RRGGBB``.
    bg : str
        Background hex for contrast correction.
    suffix : str
        Trailing word for the output name (``"Dark"`` or ``"Light"``).
    """
    out_name = name.replace("Colors", f"ColorsReadable{suffix}")
    print(f"internal val {out_name} = listOf(")
    for original in colors:
        corrected = adjust_for_readability(original, bg, MIN_LC)
        annotation = f"  // was {original}" if corrected != original.upper() else ""
        print(f"    Color(0xFF{corrected.lstrip('#')}),{annotation}")
    print(")")
    print()


def _emit_grade_bands(
    name: str,
    entries: list[tuple[float, str]],
    bg: str,
    suffix: str,
) -> None:
    """Print a corrected Kotlin grade-band block.

    Parameters
    ----------
    name : str
        Source Kotlin variable name (e.g. ``WAHOO_GRADE_BANDS``).
    entries : list[tuple[float, str]]
        Source bands as ``(threshold, "#RRGGBB")``.
    bg : str
        Background hex for contrast correction.
    suffix : str
        Trailing word for the output name (``"DARK"`` or ``"LIGHT"``).
    """
    out_name = f"{name}_READABLE_{suffix}"
    print(f"private val {out_name} = listOf(")
    for threshold, original in entries:
        corrected = adjust_for_readability(original, bg, MIN_LC)
        annotation = f"  // was {original}" if corrected != original.upper() else ""
        print(
            f"    {format_threshold(threshold)} to Color(0xFF{corrected.lstrip('#')}),"
            f"{annotation}"
        )
    print(")")
    print()


def main() -> None:
    """Emit Kotlin blocks for night- and day-mode readable palette variants."""
    power_names = base_palette_names("Power")
    grade_names = [n for n in grade_band_names() if not any(s in n for s in _GRADE_SKIP)]

    for label, bg, suffix in [
        ("Night-mode readable palettes (target bg #000000)", DATAFIELD_BG_DARK, "Dark"),
        ("Day-mode readable palettes (target bg #FFFFFF)", DATAFIELD_BG_LIGHT, "Light"),
    ]:
        print(f"// {label}")
        print()
        for name in power_names:
            _emit_palette(name, PALETTES_BY_KOTLIN_NAME[name], bg, suffix)
        for name in grade_names:
            _emit_grade_bands(name, GRADE_BANDS_BY_KOTLIN_NAME[name], bg, suffix.upper())


if __name__ == "__main__":
    main()
