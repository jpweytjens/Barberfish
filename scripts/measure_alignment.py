"""Measure baseline alignment between native Karoo and Barberfish data fields.

Reads ``screencaps/<page>.jpg`` for the layouts listed in
``scripts/walk_layouts.sh`` (1x1n, 1x1b, 2x1, 3x1, 4x1, 5x1, 2x2, 3x2, 4x2,
5x2 — `<rows>x<cols>` with optional `n`/`b` suffix for the single-cell
native/Barberfish reference pages), detects each cell, finds the header and
value text bands, classifies each header as 1-line or 2-line, estimates the
value font size from the value band height, pairs native vs Barberfish, and
reports per-cell deltas under the comparison rules:

- Header tops compared only when both sides have the same line count.
- Value tops compared only when both sides have the same value font (within 1 sp).
- When fonts differ: compare value baselines and flag CLIPPING if the smaller
  value's band height is not smaller in proportion to the size difference.

Outputs (under ``screencaps/``):

- ``alignment_metrics.csv``         one row per measured cell
- ``alignment_summary.md``          per-page deltas (line/font-aware)
- ``<page>.annotated.jpg``          overlay of cell rectangles + baseline lines

Usage
-----
``uv run scripts/measure_alignment.py``                 # default
``uv run scripts/measure_alignment.py --probe``         # use header_ref marker

In ``--probe`` mode, the script looks for the marker tint that Barberfish
renders in the (otherwise invisible) ``header_ref`` TextView when the
``layoutProbeMode`` debug flag is on. The marker band's bottom edge is used as
the authoritative 1-line header anchor for that cell, independent of how many
lines the visible ``field_header`` actually wrapped to.

Notes
-----
The user can enable Karoo OS data boundaries (thin grey borders around each
cell) to make grid detection trivial. If borders aren't visible in the
screenshot, the script falls back to uniform subdivision of the detected
content area according to the rows×cols implied by the page filename.

Why pixel analysis over ADB: see ``docs/baseline-alignment.md`` and the plan
under ``~/.claude/plans/``. Briefly: no ADB tool exposes the rendered baseline
directly, ``uiautomator dump`` is dead on Karoo, and computing the baseline
from view bounds + font metrics requires reverse-engineered constants per
Karoo OS version. Screenshots are ground truth and version-agnostic.
"""

from __future__ import annotations

import argparse
import csv
import re
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

SCREENCAPS = Path(__file__).parent.parent / "screencaps"
TOLERANCE_PX = 2

# Pixel-luminance thresholds tuned for Karoo dark-bg screencaps.
TEXT_LUMINANCE = 180
BORDER_LUMINANCE_LO = 25
BORDER_LUMINANCE_HI = 110

# The Karoo rideapp draws each data field as a black (#000000) rectangle on
# top of a uniform mid-grey backdrop (#484848 ≈ luminance 72). The cell edge
# is the transition between those two colours — a thin band of pure-grey
# rows separating cells. We classify a row as a "gap" row when at least
# `GAP_ROW_GREY_PCT` of its pixels fall in the divider band, and use the
# MIDPOINT of each gap interval as the cell boundary. Same idea per column.
GAP_GREY_LO = 50
GAP_GREY_HI = 100
GAP_ROW_GREY_PCT = 0.80
GAP_MIN_ROWS = 3        # ignore noise blips shorter than this
CELL_MIN_RUN_PX = 30    # ignore detected cell runs shorter than this

# Approximate icon-zone width on the LEFT of a header. The header icon is
# square at `headerIconSize` dp; we exclude the leftmost portion of the
# header band so the icon doesn't merge with the label text band.
ICON_INSET_PX = 36

# Karoo bottom nav strip (pause / refresh icons). Excluded from the data-grid
# content area so the pause icon isn't mistaken for a value baseline. Tuned
# for K3 portrait (480×800); K2 will need a different value.
BOTTOM_NAV_PX = 50

# Minimum height (px) for a text band to count. Filters out the icon-only
# strips and isolated artefacts.
MIN_BAND_HEIGHT = 12

# Karoo 3 display density (300 dpi / 160 = 1.875). Used to convert measured
# px → sp for diagnostic font-size estimation.
DENSITY = 1.875

# Multiplier applied to the textSize_px to estimate the visible band height
# of a single line of text rendered with `includeFontPadding=false`. The IBM
# Plex Sans Condensed header glyphs (allCaps) are roughly 0.72 × textSize tall
# from cap-top to baseline. Same ratio works for the Relative monospace digits
# used in field_value (no descenders).
GLYPH_HEIGHT_RATIO = 0.72

# Threshold (band_height / single_line_height) above which we classify the
# header as 2-line. Native lineSpacingMultiplier=0.7 makes a 2-line band
# roughly 1.7× a 1-line band; 1.4× is a safe midpoint.
TWO_LINE_RATIO = 1.4

# Marker colour the Barberfish app paints onto `header_ref` when the
# `layoutProbeMode` debug flag is on. Detected in --probe mode. ARGB written
# in the app is currently 0xFFFF00FF (fully opaque magenta); detect by
# (R,B both high, G low). The band must start near the cell top and be short
# (a 1-line header probe), otherwise it's a false positive (e.g. a
# zone-coloured cell with a lot of red+blue).
PROBE_R_MIN = 140
PROBE_G_MAX = 90
PROBE_B_MIN = 140
PROBE_MAX_TOP_OFFSET_PX = 8   # probe must start within this many px of cell top
PROBE_MAX_HEIGHT_PX = 60      # probe must be ≤ this tall (1-line header height)

PAGE_RE = re.compile(r"^(\d+)x(\d+)([nb]?)$")


# --- Karoo native size lookups ---------------------------------------------
# Mirror of the rideapp's hardcoded `(colSpan, rowSpan)` → label pixel size
# table (CLAUDE.md "Native font sizing" section). Used to compute the
# expected single-line header band height per cell, which lets us classify a
# detected band as 1-line or 2-line without guessing.


def native_label_size_px(col_span: int, row_span: int) -> int:
    """Native rideapp header label size in px on Karoo 3 (density 1.875)."""
    if col_span >= 60 and row_span >= 15:
        return 36  # 1-col 3- or 4-row (incl. 1×1)
    if col_span >= 60 and row_span >= 12:
        return 33  # 1-col 5-row
    if col_span >= 30 and row_span >= 15:
        return 33  # 2-col 4-row
    if col_span >= 30 and row_span >= 12:
        return 29  # 2-col 5-row
    return 33  # safe default; no narrower layouts are tested


def native_value_size_sp(col_span: int, row_span: int) -> float:
    """Native rideapp value font size in sp (CLAUDE.md "Native font sizing").

    Used as the "is the box big enough to fit native font?" reference. For
    1-col layouts the rideapp uses a range; we pick the upper bound as the
    worst-case test of whether our centering region can host it.
    """
    if col_span >= 60 and row_span >= 30:
        return 96.0   # 1×1 / 1×2: huge value, rideapp stretches
    if col_span >= 60 and row_span >= 15:
        return 80.0   # 1-col 3- or 4-row: midpoint of 69-96
    if col_span >= 60 and row_span >= 12:
        return 55.0   # 1-col 5-row
    if col_span >= 30 and row_span >= 15:
        return 50.0   # 2-col 4-row (3x2, 4x2)
    if col_span >= 30 and row_span >= 20:
        return 50.0   # 2-col 3-row (3x2 if rowSpan=20)
    if col_span >= 30 and row_span >= 30:
        return 50.0   # 2-col 2-row (2x2)
    if col_span >= 30 and row_span >= 12:
        return 47.0   # 2-col 5-row (5x2)
    return 47.0  # safe default


def expected_one_line_band_px(col_span: int, row_span: int) -> float:
    """Expected single-line header band height (px) for this cell."""
    return native_label_size_px(col_span, row_span) * GLYPH_HEIGHT_RATIO


def header_lines_from_band(band_height_px: int, col_span: int, row_span: int) -> int:
    """Classify a detected header band as 1-line or 2-line."""
    one_line = expected_one_line_band_px(col_span, row_span)
    return 2 if band_height_px > one_line * TWO_LINE_RATIO else 1


def value_size_sp_from_band(band_height_px: int) -> float:
    """Estimate value font size in sp from the rendered value band height.

    Relative monospace digits without descenders give band height ≈
    `textSize_px × GLYPH_HEIGHT_RATIO`. Inverted, then converted px→sp.
    """
    if band_height_px <= 0:
        return 0.0
    text_size_px = band_height_px / GLYPH_HEIGHT_RATIO
    return text_size_px / DENSITY


def page_grid_spans(rows: int, cols: int) -> tuple[int, int]:
    """Return (col_span, row_span) in the rideapp's 60-unit grid."""
    return (60 // cols if cols else 60, 60 // rows if rows else 60)


# --- dumpsys parser --------------------------------------------------------
# `walk_layouts.sh` captures `<page>.dumpsys.txt` per page via
# `adb shell dumpsys activity top`. Each line in that file describes one
# Android view at a given indent depth. Parent bounds need to be added to
# child local coords to get screen-absolute coords.

DUMP_LINE_RE = re.compile(
    r"^(?P<indent>\s+)\S+?\{\w+\s+\S+\s+\S+\s+"
    r"(?P<x0>-?\d+),(?P<y0>-?\d+)-(?P<x1>-?\d+),(?P<y1>-?\d+)"
    r"(?:\s+#\w+\s+app:id/(?P<id>\S+?))?\}\s*$"
)


@dataclass
class DumpView:
    id: str | None
    indent: int
    x0: int
    y0: int
    x1: int
    y1: int

    @property
    def w(self) -> int:
        return self.x1 - self.x0

    @property
    def h(self) -> int:
        return self.y1 - self.y0


def parse_dumpsys(path: Path) -> list[DumpView]:
    """Parse a dumpsys file into a flat list of `DumpView` records with
    screen-absolute bounds (parent offsets accumulated via indent stack).
    Lines that don't look like view dumps are skipped.
    """
    if not path.exists():
        return []
    out: list[DumpView] = []
    stack: list[tuple[int, int, int]] = []  # (indent, abs_x0, abs_y0)
    for raw in path.read_text().splitlines():
        m = DUMP_LINE_RE.match(raw)
        if not m:
            continue
        indent = len(m["indent"])
        while stack and stack[-1][0] >= indent:
            stack.pop()
        px = stack[-1][1] if stack else 0
        py = stack[-1][2] if stack else 0
        x0 = px + int(m["x0"])
        y0 = py + int(m["y0"])
        x1 = px + int(m["x1"])
        y1 = py + int(m["y1"])
        out.append(DumpView(id=m["id"], indent=indent, x0=x0, y0=y0, x1=x1, y1=y1))
        stack.append((indent, x0, y0))
    return out


def match_visible_cells(
    page: Page, dump: list[DumpView]
) -> list[DumpView]:
    """Return the rows×cols `dataElementRoot` entries that correspond to the
    visible page, sorted by (y0, x0).

    The rideapp pre-renders multiple data pages — every dump contains a
    superset of cells. We pick the entries whose dimensions match the page's
    expected on-device cell size (derived from screencap detection on the
    same capture, with a small tolerance for the cell-edge gap).
    """
    if not page.cells:
        return []
    # Use the screencap-detected cell as a dimension target. Subtract 2 px
    # for the grey-divider gap that lives between dump cells but is split
    # across screencap cell boundaries.
    target_w = max(c.bounds[2] - c.bounds[0] for c in page.cells) - 2
    target_h = max(c.bounds[3] - c.bounds[1] for c in page.cells) - 2

    candidates = [
        v for v in dump
        if v.id == "dataElementRoot"
        and abs(v.w - target_w) <= 8
        and abs(v.h - target_h) <= 12
    ]
    # The dump may list duplicates of identical bounds (one visible, one
    # offscreen-prerendered with the same translation). Dedupe by (x0, y0).
    seen: set[tuple[int, int]] = set()
    unique: list[DumpView] = []
    for v in candidates:
        key = (v.x0, v.y0)
        if key in seen:
            continue
        seen.add(key)
        unique.append(v)
    unique.sort(key=lambda v: (v.y0, v.x0))
    return unique[: page.rows * page.cols]


def cell_descendants(
    cell: DumpView, dump: list[DumpView]
) -> dict[str, DumpView]:
    """Return the named children of `cell` keyed by view-id, walking the
    indent tree until we exit the cell's subtree."""
    out: dict[str, DumpView] = {}
    if cell not in dump:
        return out
    start = dump.index(cell)
    for v in dump[start + 1:]:
        if v.indent <= cell.indent:
            break
        if v.id and v.id not in out:
            out[v.id] = v
    return out


@dataclass
class Cell:
    page: str
    row: int  # 0-indexed top to bottom
    col: int  # 0-indexed left to right
    side: str  # "native" or "barberfish"
    bounds: tuple[int, int, int, int]  # x0, y0, x1, y1 (screen px)
    col_span: int = 60  # 60-unit grid (60/cols)
    row_span: int = 60  # 60-unit grid (60/rows)
    header_top: int | None = None
    header_bottom: int | None = None
    header_lines: int | None = None  # 1 or 2 (None = no band detected)
    value_top: int | None = None
    value_baseline: int | None = None
    value_band_x0: int | None = None
    value_band_x1: int | None = None
    value_size_sp: float | None = None  # estimated from value band height
    probe_top: int | None = None  # header_ref top (only set in --probe mode)
    probe_bottom: int | None = None  # header_ref bottom (--probe only)
    # --- dumpsys-derived bounds (deterministic; screen-absolute) ---
    # Shared between native and Barberfish:
    dump_cell_y0: int | None = None
    dump_cell_y1: int | None = None
    dump_header_y0: int | None = None
    dump_header_y1: int | None = None
    dump_value_y0: int | None = None    # dataTextView (native) / field_value (BF)
    dump_value_y1: int | None = None
    # Barberfish-only:
    dump_field_root_y0: int | None = None
    dump_field_root_y1: int | None = None
    dump_baseline_box_y0: int | None = None
    dump_baseline_box_y1: int | None = None
    note: str = ""

    # --- derived geometry (filled in by measure_cell after raw bands) ---
    @property
    def cell_height_px(self) -> int:
        return self.bounds[3] - self.bounds[1]

    @property
    def top_to_header_top(self) -> int | None:
        if self.header_top is None:
            return None
        return self.header_top - self.bounds[1]

    @property
    def top_to_value_top(self) -> int | None:
        if self.value_top is None:
            return None
        return self.value_top - self.bounds[1]

    @property
    def header_to_value_gap(self) -> int | None:
        if self.value_top is None or self.header_bottom is None:
            return None
        return self.value_top - self.header_bottom

    @property
    def value_baseline_to_bottom(self) -> int | None:
        if self.value_baseline is None:
            return None
        return self.bounds[3] - self.value_baseline

    @property
    def value_band_height(self) -> int | None:
        if self.value_top is None or self.value_baseline is None:
            return None
        return self.value_baseline - self.value_top

    @property
    def box_height(self) -> int | None:
        """Centering region height. For Barberfish: cell_bottom − probe_bottom.
        For native (no probe): cell_bottom − header_bottom (best proxy).
        Returns None if we have neither anchor."""
        cell_bottom = self.bounds[3]
        if self.probe_bottom is not None:
            return cell_bottom - self.probe_bottom
        if self.header_bottom is not None:
            return cell_bottom - self.header_bottom
        return None

    @property
    def dump_value_h(self) -> int | None:
        if self.dump_value_y0 is None or self.dump_value_y1 is None:
            return None
        return self.dump_value_y1 - self.dump_value_y0

    @property
    def dump_baseline_box_h(self) -> int | None:
        if self.dump_baseline_box_y0 is None or self.dump_baseline_box_y1 is None:
            return None
        return self.dump_baseline_box_y1 - self.dump_baseline_box_y0

    @property
    def dump_value_size_sp(self) -> float | None:
        """Effective field_value sp inferred from its wrap_content height.
        For Karoo's Relative monospace at includeFontPadding=false,
        view_h ≈ 1.2 × textSize_px, so sp ≈ view_h / (1.2 × density)."""
        h = self.dump_value_h
        if h is None or h <= 0:
            return None
        return round(h / (1.2 * DENSITY), 1)


@dataclass
class Page:
    name: str
    rows: int
    cols: int
    suffix: str  # "" | "n" | "b"
    image: Image.Image
    gray: np.ndarray
    rgb: np.ndarray
    cells: list[Cell] = field(default_factory=list)


def load_pages() -> list[Page]:
    pages = []
    for jpg in sorted(SCREENCAPS.glob("*.jpg")):
        if jpg.name.endswith(".annotated.jpg"):
            continue
        m = PAGE_RE.match(jpg.stem)
        if not m:
            continue
        rows, cols, suffix = int(m.group(1)), int(m.group(2)), m.group(3)
        img = Image.open(jpg).convert("RGB")
        gray = np.array(img.convert("L"))
        rgb = np.array(img)
        pages.append(Page(jpg.stem, rows, cols, suffix, img, gray, rgb))
    return pages


# --- grid detection ---------------------------------------------------------


def detect_content_area(gray: np.ndarray) -> tuple[int, int]:
    """Find vertical content extent: y0 (after status bar) and y1 (before nav).

    Heuristic: status bar contains light text on dark bg with a fairly high
    mean brightness; the data grid is mostly black with isolated bright text.
    We pick the topmost long stretch of low-mean rows as the start of the
    content, and the bottom edge of the last such stretch as the end.
    """
    h, _ = gray.shape
    row_mean = gray.mean(axis=1)
    dark = row_mean < 25
    y0 = 0
    in_dark = False
    dark_start = 0
    for y in range(h):
        if dark[y]:
            if not in_dark:
                dark_start = y
                in_dark = True
            elif y - dark_start >= 15:
                y0 = dark_start
                break
        else:
            in_dark = False
    # Clamp the bottom to exclude the Karoo nav strip (pause/refresh icons),
    # otherwise their gray pixels register as a value text band.
    y1 = h - BOTTOM_NAV_PX
    return y0, y1


def _runs(mask: np.ndarray) -> list[tuple[int, int]]:
    out: list[tuple[int, int]] = []
    in_run = False
    start = 0
    for i, v in enumerate(mask):
        if v and not in_run:
            start = i
            in_run = True
        elif not v and in_run:
            out.append((start, i))
            in_run = False
    if in_run:
        out.append((start, len(mask)))
    return out


def _gap_midpoints(
    grey_pct: np.ndarray, min_rows: int = GAP_MIN_ROWS
) -> list[int]:
    """Return the midpoint of each contiguous run of grey-divider rows.

    A "row" here is a 1-D index along whichever axis we're scanning. Filters
    out runs shorter than `min_rows` (anti-aliasing noise).
    """
    gap_mask = grey_pct >= GAP_ROW_GREY_PCT
    return [(a + b) // 2 for a, b in _runs(gap_mask) if (b - a) >= min_rows]


CELL_ROW_GREY_MAX_PCT = 0.30


def detect_borders(
    gray: np.ndarray, content_y0: int, content_y1: int
) -> tuple[list[int], list[int]] | None:
    """Find cell boundaries from the grey-divider / black-cell transitions.

    The Karoo rideapp draws each data field as a black (#000000) rectangle
    on a mid-grey backdrop (#484848 ≈ luminance 72). A row is "cell
    content" when fewer than `CELL_ROW_GREY_MAX_PCT` of its pixels are at
    the divider colour — works for rows full of black background AND rows
    with bright text on black (the previous "dark > grey" classifier
    failed on text rows where almost no pixels were below 25 luminance).

    Outer cell-block edges are the first/last cell-row. Interior cell
    boundaries snap to the MIDPOINT of each gap between consecutive cell
    runs — the centre of the visible divider strip — instead of the ragged
    edge of the cell run, which used to vary by 20+ px between cells.

    Returns (row_breaks, col_breaks): row_breaks has length rows+1 (outer
    edges plus interior gap midpoints), col_breaks has length cols+1.
    Returns None if no qualifying cell-content runs are found.
    """
    grey_mask = (gray >= GAP_GREY_LO) & (gray <= GAP_GREY_HI)
    row_grey_pct = grey_mask.mean(axis=1)
    is_cell_row = row_grey_pct < CELL_ROW_GREY_MAX_PCT
    # The status bar is 100 % grey ⇒ no run there. The nav strip is short ⇒
    # filtered by CELL_MIN_RUN_PX. Don't rely on `content_y0`/`content_y1`
    # for further filtering: `detect_content_area`'s "first 15 dark rows"
    # heuristic finds dark stretches AFTER cells with bright text, so it
    # would drop the topmost cell on text-heavy pages (e.g. 4x1, 5x1).
    row_runs = [
        r for r in _runs(is_cell_row) if (r[1] - r[0]) >= CELL_MIN_RUN_PX
    ]
    if not row_runs:
        return None

    # Build row break-points: outer edges + gap midpoints between runs.
    row_breaks = [row_runs[0][0]]
    for a, b in zip(row_runs[:-1], row_runs[1:]):
        row_breaks.append((a[1] + b[0]) // 2)
    row_breaks.append(row_runs[-1][1])

    # Columns: same idea, scanned across the rows we just identified.
    y_lo, y_hi = row_breaks[0], row_breaks[-1]
    col_grey_pct = grey_mask[y_lo:y_hi, :].mean(axis=0)
    is_cell_col = col_grey_pct < CELL_ROW_GREY_MAX_PCT
    col_runs = [
        r for r in _runs(is_cell_col) if (r[1] - r[0]) >= CELL_MIN_RUN_PX
    ]
    if not col_runs:
        return None

    col_breaks = [col_runs[0][0]]
    for a, b in zip(col_runs[:-1], col_runs[1:]):
        col_breaks.append((a[1] + b[0]) // 2)
    col_breaks.append(col_runs[-1][1])

    return row_breaks, col_breaks


def cells_from_borders(
    row_breaks: list[int], col_breaks: list[int]
) -> list[tuple[int, int, int, int]]:
    cells = []
    for ri in range(len(row_breaks) - 1):
        for ci in range(len(col_breaks) - 1):
            cells.append(
                (col_breaks[ci], row_breaks[ri], col_breaks[ci + 1], row_breaks[ri + 1])
            )
    return cells


def cells_uniform(
    rows: int, cols: int, content_y0: int, content_y1: int, w: int
) -> list[tuple[int, int, int, int]]:
    """Subdivide [0..w] × [content_y0..content_y1] into rows × cols cells."""
    h_content = content_y1 - content_y0
    return [
        (
            int(c * w / cols),
            content_y0 + int(r * h_content / rows),
            int((c + 1) * w / cols),
            content_y0 + int((r + 1) * h_content / rows),
        )
        for r in range(rows)
        for c in range(cols)
    ]


def detect_cells(page: Page) -> tuple[list[tuple[int, int, int, int]], str]:
    """Return cell bounds + a note describing how they were derived."""
    h, w = page.gray.shape
    content_y0, content_y1 = detect_content_area(page.gray)

    borders = detect_borders(page.gray, content_y0, content_y1)
    if borders is not None:
        row_breaks, col_breaks = borders
        n_cells = (len(row_breaks) - 1) * (len(col_breaks) - 1)
        if n_cells == page.rows * page.cols:
            return cells_from_borders(row_breaks, col_breaks), "borders"

    cells = cells_uniform(page.rows, page.cols, content_y0, content_y1, w)
    return cells, f"uniform (content {content_y0}..{content_y1})"


# --- text-band measurement --------------------------------------------------


def find_bands(
    cell_gray: np.ndarray, icon_inset_px: int = 0, gap_tolerance: int = 3
) -> list[tuple[int, int, int, int]]:
    """Find Y-bands of bright text in `cell_gray`. Returns (top, bot, xL, xR)."""
    region = cell_gray[:, icon_inset_px:]
    bright = region >= TEXT_LUMINANCE
    row_has = bright.any(axis=1)

    if not row_has.any():
        return []

    # Close small vertical gaps (e.g. between header icon row and label).
    if gap_tolerance > 0:
        kernel = np.ones(2 * gap_tolerance + 1, dtype=int)
        smoothed = np.convolve(row_has.astype(int), kernel, mode="same") > 0
    else:
        smoothed = row_has

    bands: list[tuple[int, int, int, int]] = []
    h = cell_gray.shape[0]
    in_band = False
    start = 0
    for y in range(h):
        if smoothed[y] and not in_band:
            start = y
            in_band = True
        elif not smoothed[y] and in_band:
            actual = np.where(row_has[start:y])[0]
            if len(actual):
                top = start + int(actual[0])
                bot = start + int(actual[-1]) + 1
                cols = bright[top:bot, :].any(axis=0)
                xs = np.where(cols)[0]
                if len(xs):
                    bands.append(
                        (top, bot, int(xs[0]) + icon_inset_px, int(xs[-1]) + icon_inset_px)
                    )
            in_band = False
    if in_band:
        actual = np.where(row_has[start:])[0]
        if len(actual):
            top = start + int(actual[0])
            bot = start + int(actual[-1]) + 1
            cols = bright[top:bot, :].any(axis=0)
            xs = np.where(cols)[0]
            if len(xs):
                bands.append(
                    (top, bot, int(xs[0]) + icon_inset_px, int(xs[-1]) + icon_inset_px)
                )
    return bands


def find_probe_band(cell_rgb: np.ndarray) -> tuple[int, int] | None:
    """Detect the magenta `header_ref` marker band in --probe mode.

    Returns (top, bot) in cell-local coordinates, or None if not found.

    The band must:
    - START within `PROBE_MAX_TOP_OFFSET_PX` of cell top (it's `alignParentTop`),
      so a row of magenta further down is rejected.
    - Be a CONTIGUOUS run from that top (terminated at the first non-magenta
      row). This rejects scattered magenta from zone-coloured backgrounds.
    - Be ≤ `PROBE_MAX_HEIGHT_PX` tall. A 1-line header at native sp + minHeight
      maxes out at ~50 px; anything taller is a zone fill or render bug.
    """
    r, g, b = cell_rgb[..., 0], cell_rgb[..., 1], cell_rgb[..., 2]
    mask = (r >= PROBE_R_MIN) & (g <= PROBE_G_MAX) & (b >= PROBE_B_MIN)
    rows = mask.any(axis=1)
    if not rows.any():
        return None
    ys = np.where(rows)[0]
    top = int(ys[0])
    if top > PROBE_MAX_TOP_OFFSET_PX:
        return None
    bot = top
    h = len(rows)
    for y in range(top, h):
        if rows[y]:
            bot = y + 1
        else:
            break
    if bot - top > PROBE_MAX_HEIGHT_PX:
        return None
    return top, bot


def measure_cell(cell: Cell, gray: np.ndarray, rgb: np.ndarray | None = None) -> None:
    x0, y0, x1, y1 = cell.bounds
    # Skip the 1px gray cell border so we don't classify it as text. Larger
    # insets used to "be safe" actually mask real bottom-edge clipping —
    # bright value glyphs that run all the way to the cell border need to be
    # visible to the band detector so Δvalue_baseline reflects the true
    # rendered position, not a 3px-haircut version.
    inset = 1
    sub = gray[y0 + inset : y1 - inset, x0 + inset : x1 - inset]
    if sub.size == 0:
        cell.note = "empty crop"
        return

    # Probe pass (--probe mode only). Detect the magenta header_ref marker on
    # Barberfish cells; it gives an authoritative 1-line header anchor that's
    # independent of how field_header actually wrapped.
    if rgb is not None:
        sub_rgb = rgb[y0 + inset : y1 - inset, x0 + inset : x1 - inset, :]
        probe = find_probe_band(sub_rgb)
        if probe is not None:
            ptop, pbot = probe
            cell.probe_top = ptop + y0 + inset
            cell.probe_bottom = pbot + y0 + inset

    # Header pass — exclude leftmost icon zone so the icon glyph doesn't get
    # merged into the label band.
    header_bands = [
        b for b in find_bands(sub, icon_inset_px=ICON_INSET_PX)
        if b[1] - b[0] >= MIN_BAND_HEIGHT
    ]
    if header_bands:
        top, bot, _, _ = header_bands[0]
        cell.header_top = top + y0 + inset
        cell.header_bottom = bot + y0 + inset
        cell.header_lines = header_lines_from_band(bot - top, cell.col_span, cell.row_span)

    # Value pass — full width, since values have no icon. Pick the bottommost
    # qualifying band that sits BELOW the detected header (or any qualifying
    # band if no header was found). Border-based cell detection already
    # excludes the nav strip, so we don't need to filter cell-bottom edges.
    value_bands = [
        b for b in find_bands(sub, icon_inset_px=0)
        if b[1] - b[0] >= MIN_BAND_HEIGHT
    ]
    header_bot_local = (cell.header_bottom - y0 - inset) if cell.header_bottom else 0
    below_header = [b for b in value_bands if b[0] >= header_bot_local]
    # Pick the largest qualifying band by area — robust against noise like
    # cell-corner anti-aliasing or wrapped-header second lines.
    chosen = (
        max(below_header, key=lambda b: (b[1] - b[0]) * (b[3] - b[2]))
        if below_header
        else None
    )
    if chosen is not None:
        top, bot, xL, xR = chosen
        cell.value_top = top + y0 + inset
        cell.value_baseline = bot + y0 + inset
        cell.value_band_x0 = xL + x0 + inset
        cell.value_band_x1 = xR + x0 + inset
        cell.value_size_sp = round(value_size_sp_from_band(bot - top), 1)
    else:
        cell.note = (cell.note + "; " if cell.note else "") + "no value band"


# --- side labelling ---------------------------------------------------------


def label_side(page: Page, cell: Cell, probe_mode: bool) -> str:
    """Label a cell as `native` or `barberfish`.

    In `--probe` mode, probe presence is the source of truth: a magenta
    `header_ref` band → barberfish; otherwise native. This works for stacked
    mixed pages (e.g. `5x1` rows interleave native and Barberfish) which the
    old filename/column heuristic mis-labelled as Barberfish-only.

    Without probe data we fall back to the legacy convention: filename
    suffix `n`/`b` for the single-cell pages, otherwise 2-col left=native
    right=Barberfish, single-col=Barberfish.
    """
    if probe_mode:
        return "barberfish" if cell.probe_bottom is not None else "native"
    if page.suffix == "n":
        return "native"
    if page.suffix == "b":
        return "barberfish"
    if page.cols >= 2:
        return "native" if cell.col == 0 else "barberfish"
    return "barberfish"


# --- output -----------------------------------------------------------------


def write_csv(pages: list[Page], path: Path) -> None:
    with path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(
            [
                "page",
                "row",
                "col",
                "side",
                "col_span",
                "row_span",
                "cell_x0",
                "cell_y0",
                "cell_x1",
                "cell_y1",
                "cell_h",
                "header_top",
                "header_bottom",
                "header_lines",
                "value_top",
                "value_baseline",
                "value_size_sp",
                "value_band_width_pct",
                "probe_top",
                "probe_bottom",
                # --- audit metrics (iteration 2) ---
                "top_to_header_top",
                "top_to_value_top",
                "header_to_value_gap",
                "value_baseline_to_bottom",
                "value_band_height",
                "box_height",
                # --- dumpsys-derived bounds (iteration 3) ---
                "dump_cell_y0",
                "dump_cell_y1",
                "dump_header_y0",
                "dump_header_y1",
                "dump_value_y0",
                "dump_value_y1",
                "dump_baseline_box_y0",
                "dump_baseline_box_y1",
                "dump_value_size_sp",
                "note",
            ]
        )
        for p in pages:
            for c in p.cells:
                cell_w = c.bounds[2] - c.bounds[0]
                if c.value_band_x0 is not None and c.value_band_x1 is not None:
                    band_w_pct = (c.value_band_x1 - c.value_band_x0) / cell_w
                else:
                    band_w_pct = 0.0
                w.writerow(
                    [
                        c.page,
                        c.row,
                        c.col,
                        c.side,
                        c.col_span,
                        c.row_span,
                        *c.bounds,
                        c.cell_height_px,
                        c.header_top,
                        c.header_bottom,
                        c.header_lines,
                        c.value_top,
                        c.value_baseline,
                        c.value_size_sp,
                        round(band_w_pct, 3),
                        c.probe_top,
                        c.probe_bottom,
                        c.top_to_header_top,
                        c.top_to_value_top,
                        c.header_to_value_gap,
                        c.value_baseline_to_bottom,
                        c.value_band_height,
                        c.box_height,
                        c.dump_cell_y0,
                        c.dump_cell_y1,
                        c.dump_header_y0,
                        c.dump_header_y1,
                        c.dump_value_y0,
                        c.dump_value_y1,
                        c.dump_baseline_box_y0,
                        c.dump_baseline_box_y1,
                        c.dump_value_size_sp,
                        c.note,
                    ]
                )


def pair_native_barberfish(pages: list[Page]):
    """Yield (label, native_cell, bf_cell) pairs.

    For 2-col layouts: pair native (col 0) and Barberfish (col 1) per row.
    For 1x1n + 1x1b: pair the two single-cell pages directly.
    For single-column pages with mixed native+BF rows (e.g. 5x1 with
    `TOTAL TIME` native rows and `ELAPSED TIME` BF rows): pair the first
    native row with the first BF row that have the same `(colSpan, rowSpan)`.
    Cells are vertically stacked, so per-cell `value_baseline_to_bottom`
    is the directly comparable quantity.
    """
    pages_by_name = {p.name: p for p in pages}

    if "1x1n" in pages_by_name and "1x1b" in pages_by_name:
        n_cells = pages_by_name["1x1n"].cells
        b_cells = pages_by_name["1x1b"].cells
        if n_cells and b_cells:
            yield "1x1 (n vs b)", n_cells[0], b_cells[0]

    for p in pages:
        if p.suffix:
            continue
        if p.cols >= 2:
            rows = defaultdict(list)
            for c in p.cells:
                rows[c.row].append(c)
            for r in sorted(rows):
                row_cells = rows[r]
                native = next((c for c in row_cells if c.side == "native"), None)
                barberfish = next((c for c in row_cells if c.side == "barberfish"), None)
                if native is not None and barberfish is not None:
                    yield f"{p.name} row {r}", native, barberfish
        elif p.cols == 1:
            # On stacked single-col mixed pages (e.g. 5x1: native TOTAL TIME +
            # BF ELAPSED TIME interleaved with stream-state rows), prefer the
            # cell on each side with the LARGEST value font — that's the row
            # holding an actual numeric value rather than a "Not available"
            # stream-state TextView (which the band detector reports at ~19 sp).
            def best(cs: list[Cell]) -> Cell | None:
                with_sp = [c for c in cs if c.value_size_sp is not None]
                return max(with_sp, key=lambda c: c.value_size_sp) if with_sp else None

            native = best([c for c in p.cells if c.side == "native"])
            barberfish = best([c for c in p.cells if c.side == "barberfish"])
            if native is not None and barberfish is not None:
                yield f"{p.name} (n r{native.row} vs b r{barberfish.row})", native, barberfish


def fmt_delta(a: int | None, b: int | None) -> tuple[str, int | None]:
    """Native - Barberfish, with PASS/FAIL marker against TOLERANCE_PX."""
    if a is None or b is None:
        return "—", None
    d = a - b
    mark = "PASS" if abs(d) <= TOLERANCE_PX else "FAIL"
    return f"{d:+d} {mark}", d


def fmt_na(reason: str) -> str:
    return f"N/A ({reason})"


FONT_MATCH_SP = 2.0


def fonts_match(n: Cell, b: Cell) -> bool:
    """True if value font sizes match within ±FONT_MATCH_SP."""
    if n.value_size_sp is None or b.value_size_sp is None:
        return False
    return abs(n.value_size_sp - b.value_size_sp) <= FONT_MATCH_SP


def lines_match(n: Cell, b: Cell) -> bool:
    if n.header_lines is None or b.header_lines is None:
        return False
    return n.header_lines == b.header_lines


# Clipping note
# ---
# A shorter band height than expected for the configured font size means the
# digit is being chopped (centering region too small). However, this script
# derives `value_size_sp` directly FROM the band height, so any cross-cell
# proportional check is tautological: a clipped native-sp render and an
# intentional smaller-sp render produce identical band heights.
#
# To detect clipping reliably we need the *configured* `valueFontSizeBase`
# per cell (read from `screencaps/<page>.logcat.txt` lines like `textSize=…`)
# and compare against the rendered band height. That's a separate pass; not
# implemented here. For now: when |Δvalue_top - Δvalue_baseline| > a few px
# on a row whose `meta` says the fonts match, suspect clipping and inspect
# the annotated screencap manually.


def write_summary(pages: list[Page], path: Path) -> None:
    lines = ["# Baseline alignment summary", ""]
    lines.append(f"Tolerance: ±{TOLERANCE_PX} px")
    lines.append("")
    lines.append("Comparison rules (per the user-specified protocol):")
    lines.append("- Header tops/bots: PASS/FAIL only when both sides have the same line count.")
    lines.append(f"- Value tops: PASS/FAIL only when both value fonts match within ±{FONT_MATCH_SP} sp.")
    lines.append("- Value baselines: PASS/FAIL whenever both bands are detected.")
    lines.append("")
    lines.append("Note: `value_size_sp` is derived FROM the band height, so screenshots alone")
    lines.append("cannot distinguish 'clipped native-sp render' from 'intentional smaller sp'.")
    lines.append("If `meta` says fonts match but Δvalue_top differs from Δvalue_baseline by")
    lines.append("more than a few px, suspect clipping and inspect the annotated screencap.")
    lines.append("")
    lines.append("Δ = native − Barberfish (in pixels). Negative Δvalue_baseline means")
    lines.append("Barberfish baseline sits BELOW native.")
    lines.append("")

    deltas_by_layout: dict[tuple[int, int], list[int]] = defaultdict(list)
    pair_rows = []
    for label, n, b in pair_native_barberfish(pages):
        ln_n = n.header_lines if n.header_lines is not None else "?"
        ln_b = b.header_lines if b.header_lines is not None else "?"
        sp_n = f"{n.value_size_sp:.1f}" if n.value_size_sp is not None else "?"
        sp_b = f"{b.value_size_sp:.1f}" if b.value_size_sp is not None else "?"
        meta = f"lines {ln_n}↔{ln_b}, sp {sp_n}↔{sp_b}"

        # When the paired cells are in DIFFERENT rows (single-col mixed
        # pages), absolute y comparisons are meaningless — they include the
        # row stride. Rebase using each cell's own bottom edge: Δ becomes
        # `(native.cell_bottom − native.measured_y) − (bf.cell_bottom −
        # bf.measured_y)`, which is cell-local and directly comparable.
        cross_row = n.row != b.row
        n_y0, b_y0 = n.bounds[1], b.bounds[1]
        n_y1, b_y1 = n.bounds[3], b.bounds[3]

        def offset(c_y0: int, c_y1: int, v: int | None, *, from_bottom: bool) -> int | None:
            if v is None:
                return None
            return (c_y1 - v) if from_bottom else (v - c_y0)

        def fmt_pair(nv: int | None, bv: int | None, *, from_bottom: bool) -> tuple[str, int | None]:
            if not cross_row:
                return fmt_delta(nv, bv)
            return fmt_delta(
                offset(n_y0, n_y1, nv, from_bottom=from_bottom),
                offset(b_y0, b_y1, bv, from_bottom=from_bottom),
            )

        if lines_match(n, b):
            ht_str, _ = fmt_pair(n.header_top, b.header_top, from_bottom=False)
            hb_str, _ = fmt_pair(n.header_bottom, b.header_bottom, from_bottom=False)
        else:
            ht_str = fmt_na("line-count mismatch")
            hb_str = fmt_na("line-count mismatch")

        if fonts_match(n, b):
            vt_str, _ = fmt_pair(n.value_top, b.value_top, from_bottom=False)
            vb_str, vb_d = fmt_pair(n.value_baseline, b.value_baseline, from_bottom=True)
        else:
            vt_str = fmt_na("font mismatch")
            vb_str, vb_d = fmt_pair(n.value_baseline, b.value_baseline, from_bottom=True)

        pair_rows.append((label, meta, ht_str, hb_str, vt_str, vb_str))

        # Only feed deltas back into the (HISTORICAL) auto-tune table when the
        # pair is actually comparable: same line count AND same value font.
        if (
            vb_d is not None
            and lines_match(n, b)
            and fonts_match(n, b)
        ):
            page_name = label.split()[0]
            m = PAGE_RE.match(page_name)
            if m:
                rows, cols = int(m.group(1)), int(m.group(2))
                key = page_grid_spans(rows, cols)
                deltas_by_layout[key].append(vb_d)

    lines.append("| Pair | meta | Δheader_top | Δheader_bot | Δvalue_top | Δvalue_baseline |")
    lines.append("|---|---|---|---|---|---|")
    for label, meta, ht, hb, vt, vb in pair_rows:
        lines.append(f"| {label} | {meta} | {ht} | {hb} | {vt} | {vb} |")
    lines.append("")

    lines.append("## Δvalue_baseline averages by layout (comparable rows only)")
    lines.append("")
    lines.append("Cross-reference for tuning `valueTranslationDp`. Positive Δ means BF")
    lines.append("baseline sits below native; bump translation more negative.")
    lines.append("")
    lines.append("| (colSpan, rowSpan) | n pairs | Δvalue_baseline avg (px) |")
    lines.append("|---|---|---|")
    if not deltas_by_layout:
        lines.append("| — | 0 | no comparable rows |")
    for (col_span, row_span), ds in sorted(deltas_by_layout.items()):
        avg = sum(ds) / len(ds)
        lines.append(f"| ({col_span}, {row_span}) | {len(ds)} | {avg:+.1f} |")
    lines.append("")

    # --- Per-layout consistency ------------------------------------------
    # Group by (col_span, row_span, side) for cell-anchored geometry that
    # should be constant regardless of value font (cell_height, header_top
    # offset). Group additionally by an sp bucket for value-side metrics
    # (val_baseline_to_bottom, value_band_height) — those legitimately
    # depend on font size, so mixing fonts inflates the stdev.
    def stat(xs: list[float]) -> str:
        if not xs:
            return "—"
        mu = sum(xs) / len(xs)
        sd = (sum((x - mu) ** 2 for x in xs) / len(xs)) ** 0.5
        flag = " ⚠️" if sd > 1.0 else ""
        return f"{mu:.1f}±{sd:.1f}{flag}"

    lines.append("## Per-layout consistency (cell-anchored, font-independent)")
    lines.append("")
    lines.append("Cells of the same `(colSpan, rowSpan, side)` should render with")
    lines.append("identical CELL geometry. `stdev > 1 px` ⚠️ → likely a script")
    lines.append("measurement bug (cell-edge detection) rather than a layout drift.")
    lines.append("")
    lines.append("| (colSpan, rowSpan, side) | n | cell_h μ±σ | top→hdr_top μ±σ |")
    lines.append("|---|---|---|---|")
    groups_cell: dict[tuple[int, int, str], list[Cell]] = defaultdict(list)
    for p in pages:
        for c in p.cells:
            groups_cell[(c.col_span, c.row_span, c.side)].append(c)
    for key in sorted(groups_cell):
        cells = groups_cell[key]
        ch = [c.cell_height_px for c in cells]
        th = [c.top_to_header_top for c in cells if c.top_to_header_top is not None]
        lines.append(f"| {key} | {len(cells)} | {stat(ch)} | {stat(th)} |")
    lines.append("")

    lines.append("### Native ↔ Barberfish header-top offset (per layout)")
    lines.append("")
    lines.append("Δ = native − Barberfish mean of `top_to_header_top`, averaged across")
    lines.append("all observed cells of that layout. Non-zero means our header sits")
    lines.append("at a different cell-relative position from native, regardless of")
    lines.append("which paired page we measure.")
    lines.append("")
    lines.append("| (colSpan, rowSpan) | native μ | barberfish μ | Δ |")
    lines.append("|---|---|---|---|")
    layouts = sorted({(cs, rs) for (cs, rs, _) in groups_cell})
    for cs, rs in layouts:
        nat = [c.top_to_header_top for c in groups_cell.get((cs, rs, "native"), []) if c.top_to_header_top is not None]
        bf = [c.top_to_header_top for c in groups_cell.get((cs, rs, "barberfish"), []) if c.top_to_header_top is not None]
        if not nat or not bf:
            continue
        nat_mu = sum(nat) / len(nat)
        bf_mu = sum(bf) / len(bf)
        delta = nat_mu - bf_mu
        flag = " ⚠️" if abs(delta) > TOLERANCE_PX else ""
        lines.append(f"| ({cs}, {rs}) | {nat_mu:.1f} | {bf_mu:.1f} | {delta:+.1f}{flag} |")
    lines.append("")

    lines.append("### Value-band consistency (grouped by sp bucket)")
    lines.append("")
    lines.append("Within a `(colSpan, rowSpan, side)` group, cells rendering at the")
    lines.append("SAME font size should land at the same baseline. We bucket by 5 sp")
    lines.append("so a 24.4 sp and a 25.2 sp cell sit in the same row.")
    lines.append("")
    lines.append("| (colSpan, rowSpan, side) | sp bucket | n | val_band_h μ±σ | baseline→bottom μ±σ |")
    lines.append("|---|---|---|---|---|")
    SP_BUCKET = 5.0
    groups_sp: dict[tuple[int, int, str, int], list[Cell]] = defaultdict(list)
    for p in pages:
        for c in p.cells:
            if c.value_size_sp is None:
                continue
            bucket = int(round(c.value_size_sp / SP_BUCKET) * SP_BUCKET)
            groups_sp[(c.col_span, c.row_span, c.side, bucket)].append(c)
    for key in sorted(groups_sp):
        cs, rs, side, bucket = key
        cells = groups_sp[key]
        bh = [c.value_band_height for c in cells if c.value_band_height is not None]
        vb = [c.value_baseline_to_bottom for c in cells if c.value_baseline_to_bottom is not None]
        lines.append(
            f"| ({cs}, {rs}, '{side}') | ~{bucket} sp | {len(cells)} | "
            f"{stat(bh)} | {stat(vb)} |"
        )
    lines.append("")

    # --- Sysdump cross-check ---------------------------------------------
    lines.append("## Sysdump cross-check")
    lines.append("")
    lines.append("Bounds + sizes from `dumpsys activity top` (deterministic).")
    lines.append("For Barberfish bitmap rendering, baseline = `val_y0 + val_h`.")
    lines.append("For native TextView, baseline ≈ `val_y0 + 0.833 × val_h`.")
    lines.append("")
    lines.append("| page | r | c | side | cell h | box h | val h | val sp | val_y0 | base_y_meas |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|")
    for p in pages:
        for c in p.cells:
            if c.dump_cell_y0 is None:
                continue
            cell_h = (c.dump_cell_y1 or 0) - (c.dump_cell_y0 or 0)
            box_h = c.dump_baseline_box_h
            val_h = c.dump_value_h
            val_sp = c.dump_value_size_sp
            val_y0 = c.dump_value_y0
            base_meas = c.value_baseline
            lines.append(
                f"| `{c.page}` | {c.row} | {c.col} | {c.side} | {cell_h} | "
                f"{box_h or '—'} | {val_h or '—'} | {val_sp or '—'} | "
                f"{val_y0 or '—'} | {base_meas if base_meas is not None else '—'} |"
            )
    lines.append("")

    # --- Per-page detection notes ----------------------------------------
    lines.append("## Per-page detection notes")
    for p in pages:
        n_with_value = sum(1 for c in p.cells if c.value_baseline is not None)
        n_with_header = sum(1 for c in p.cells if c.header_bottom is not None)
        n_with_probe = sum(1 for c in p.cells if c.probe_bottom is not None)
        probe_note = f", {n_with_probe} probes" if n_with_probe else ""
        lines.append(
            f"- `{p.name}` ({p.rows}×{p.cols}): {len(p.cells)} cells, "
            f"{n_with_header} headers, {n_with_value} values{probe_note}"
        )

    path.write_text("\n".join(lines))


def annotate(page: Page, out: Path) -> None:
    img = page.image.copy()
    draw = ImageDraw.Draw(img)
    for c in page.cells:
        x0, y0, x1, y1 = c.bounds
        draw.rectangle([x0, y0, x1 - 1, y1 - 1], outline=(255, 255, 255))
        col_value_bot = (255, 0, 0) if c.side == "native" else (0, 255, 255)
        col_value_top = (255, 128, 0) if c.side == "native" else (0, 200, 255)
        col_header = (255, 255, 0)
        # Top lines first, bottom lines last so they win the overlap.
        if c.header_top is not None:
            draw.line([x0, c.header_top, x1, c.header_top], fill=col_header, width=2)
        if c.value_top is not None:
            draw.line([x0, c.value_top, x1, c.value_top], fill=col_value_top, width=2)
        if c.header_bottom is not None:
            draw.line([x0, c.header_bottom, x1, c.header_bottom], fill=col_header, width=2)
        if c.value_baseline is not None:
            draw.line([x0, c.value_baseline, x1, c.value_baseline], fill=col_value_bot, width=2)
    img.save(out, "JPEG", quality=85)


# --- main -------------------------------------------------------------------


def attach_dump_bounds(page: Page) -> None:
    """For each cell in `page.cells`, populate the `dump_*` fields by parsing
    the matching `<page>.dumpsys.txt` and finding the visible-page cells.

    The rideapp's ConstraintLayout starts BELOW the status bar, so dump y
    coords are content-area-relative; screencap y coords are screen-absolute
    (status bar at y=0..~60). We compute the offset from the first matched
    pair and add it to all dump y values so they're directly comparable to
    screencap measurements.
    """
    dump = parse_dumpsys(SCREENCAPS / f"{page.name}.dumpsys.txt")
    if not dump:
        return
    visible = match_visible_cells(page, dump)
    if len(visible) != len(page.cells):
        # Mismatch — skip rather than blindly pair. The rideapp may have
        # captured a transient state during the swipe.
        return
    # screencap y_screen − dump y_content = status-bar height. Use the
    # first matched cell pair to derive it.
    y_offset = page.cells[0].bounds[1] - visible[0].y0

    def shift(v: int | None) -> int | None:
        return None if v is None else v + y_offset

    # page.cells is row-major (row 0 col 0, row 0 col 1, …); `visible` is
    # sorted by (y0, x0) which is also row-major. Direct zip works.
    for cell, dv in zip(page.cells, visible):
        children = cell_descendants(dv, dump)
        cell.dump_cell_y0 = shift(dv.y0)
        cell.dump_cell_y1 = shift(dv.y1)
        if (h := children.get("headerLayout")) is not None:
            cell.dump_header_y0, cell.dump_header_y1 = shift(h.y0), shift(h.y1)
        if (fh := children.get("field_header")) is not None:
            cell.dump_header_y0, cell.dump_header_y1 = shift(fh.y0), shift(fh.y1)
        if (fr := children.get("field_root")) is not None:
            cell.dump_field_root_y0, cell.dump_field_root_y1 = shift(fr.y0), shift(fr.y1)
        if (bb := children.get("baseline_box")) is not None:
            cell.dump_baseline_box_y0, cell.dump_baseline_box_y1 = shift(bb.y0), shift(bb.y1)
        if (fv := children.get("field_value")) is not None:
            cell.dump_value_y0, cell.dump_value_y1 = shift(fv.y0), shift(fv.y1)
        elif (dt := children.get("dataTextView")) is not None:
            cell.dump_value_y0, cell.dump_value_y1 = shift(dt.y0), shift(dt.y1)


def main(probe: bool = False) -> None:
    pages = load_pages()
    if not pages:
        print(f"no screencaps under {SCREENCAPS}")
        return

    used_uniform = []
    for p in pages:
        bounds, note = detect_cells(p)
        if note != "borders":
            used_uniform.append(p.name)
        col_span, row_span = page_grid_spans(p.rows, p.cols)
        rgb_arg = p.rgb if probe else None
        for i, b in enumerate(bounds):
            row = i // p.cols
            col = i % p.cols
            cell = Cell(
                p.name, row, col, "unknown", b,
                col_span=col_span, row_span=row_span,
                note=note,
            )
            measure_cell(cell, p.gray, rgb=rgb_arg)
            cell.side = label_side(p, cell, probe_mode=probe)
            p.cells.append(cell)
        attach_dump_bounds(p)

    write_csv(pages, SCREENCAPS / "alignment_metrics.csv")
    write_summary(pages, SCREENCAPS / "alignment_summary.md")
    for p in pages:
        annotate(p, SCREENCAPS / f"{p.name}.annotated.jpg")

    print("wrote:")
    print(f"  {SCREENCAPS / 'alignment_metrics.csv'}")
    print(f"  {SCREENCAPS / 'alignment_summary.md'}")
    print(f"  {SCREENCAPS}/<page>.annotated.jpg ({len(pages)} files)")
    if used_uniform:
        print()
        print(f"WARNING: border detection failed for {len(used_uniform)} page(s):")
        print(f"  {', '.join(used_uniform)}")
        print("Re-capture with Karoo OS data boundaries enabled for clean grid detection.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--probe",
        action="store_true",
        help="Detect the magenta header_ref marker (requires Barberfish "
        "running with the layoutProbeMode debug flag enabled).",
    )
    args = parser.parse_args()
    main(probe=args.probe)
