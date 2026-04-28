"""Measure baseline alignment between native Karoo and Barberfish data fields.

Reads ``screencaps/<page>.jpg`` for the layouts listed in
``scripts/walk_layouts.sh`` (1x1n, 1x1b, 2x1, 3x1, 4x1, 5x1, 2x2, 3x2, 4x2,
5x2 — `<rows>x<cols>` with optional `n`/`b` suffix for the single-cell
native/Barberfish reference pages), detects each cell, finds the header and
value text bands, pairs native vs Barberfish, and reports per-cell deltas.

Outputs (under ``screencaps/``):

- ``alignment_metrics.csv``         one row per measured cell
- ``alignment_summary.md``          per-page deltas + auto-tune snippet
- ``<page>.annotated.jpg``          overlay of cell rectangles + baseline lines

Usage
-----
``uv run scripts/measure_alignment.py``

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

# Linear gain seeded for v1 auto-tune. sp adjustment ≈ K × Δpx.
AUTO_TUNE_K = 2.0

PAGE_RE = re.compile(r"^(\d+)x(\d+)([nb]?)$")


@dataclass
class Cell:
    page: str
    row: int  # 0-indexed top to bottom
    col: int  # 0-indexed left to right
    side: str  # "native" or "barberfish"
    bounds: tuple[int, int, int, int]  # x0, y0, x1, y1 (screen px)
    header_top: int | None = None
    header_bottom: int | None = None
    value_top: int | None = None
    value_baseline: int | None = None
    value_band_x0: int | None = None
    value_band_x1: int | None = None
    note: str = ""


@dataclass
class Page:
    name: str
    rows: int
    cols: int
    suffix: str  # "" | "n" | "b"
    image: Image.Image
    gray: np.ndarray
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
        pages.append(Page(jpg.stem, rows, cols, suffix, img, gray))
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


def detect_borders(
    gray: np.ndarray, content_y0: int, content_y1: int
) -> tuple[list[int], list[int]] | None:
    """Find row/col cell boundaries from the dark-cell-on-gray-bg layout.

    The Karoo page builder renders each cell as a near-black rounded rectangle
    on a mid-grey background (~72 luminance). We classify each row as
    "cell-content" (mostly dark) or "gap" (mostly grey/bg), then return the
    boundaries between consecutive cell-content runs. Same idea per column.

    Status-bar rows have grey-bg dominance so they classify as "gap" and
    drop out naturally — no content_y0 slicing needed.

    Returns clustered (row_breaks, col_breaks) where each list has length
    rows+1 / cols+1 (top + bottom edges included). Returns None if no clear
    cell-content runs found.
    """
    cell_dark = gray < BORDER_LUMINANCE_LO  # mostly black inside cells
    bg_grey = (gray >= BORDER_LUMINANCE_LO) & (gray <= BORDER_LUMINANCE_HI)

    row_dark_pct = cell_dark.mean(axis=1)
    row_grey_pct = bg_grey.mean(axis=1)
    is_cell_row = row_dark_pct > row_grey_pct

    def runs(mask: np.ndarray, offset: int = 0) -> list[tuple[int, int]]:
        out = []
        in_run = False
        start = 0
        for i, v in enumerate(mask):
            if v and not in_run:
                start = i
                in_run = True
            elif not v and in_run:
                out.append((start + offset, i + offset))
                in_run = False
        if in_run:
            out.append((start + offset, len(mask) + offset))
        return out

    row_runs = [r for r in runs(is_cell_row) if r[1] - r[0] >= 30]
    if not row_runs:
        return None

    # Column classification across the rows we just identified — restrict to
    # the y-range of detected cells so the column gap is unambiguous.
    y_lo = row_runs[0][0]
    y_hi = row_runs[-1][1]
    col_dark_pct = cell_dark[y_lo:y_hi, :].mean(axis=0)
    col_grey_pct = bg_grey[y_lo:y_hi, :].mean(axis=0)
    is_cell_col = col_dark_pct > col_grey_pct
    col_runs = [r for r in runs(is_cell_col) if r[1] - r[0] >= 30]
    if not col_runs:
        return None

    # Convert runs into break-points. Cell ri spans row_breaks[ri]..row_breaks[ri+1].
    row_breaks = [row_runs[0][0]]
    for a, b in zip(row_runs[:-1], row_runs[1:]):
        row_breaks.append((a[1] + b[0]) // 2)
    row_breaks.append(row_runs[-1][1])

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


def measure_cell(cell: Cell, gray: np.ndarray) -> None:
    x0, y0, x1, y1 = cell.bounds
    inset = 3  # skip border pixels
    sub = gray[y0 + inset : y1 - inset, x0 + inset : x1 - inset]
    if sub.size == 0:
        cell.note = "empty crop"
        return

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
    else:
        cell.note = (cell.note + "; " if cell.note else "") + "no value band"


# --- side labelling ---------------------------------------------------------


def label_side(page: Page, row: int, col: int) -> str:
    """User convention: 1x1n=native, 1x1b=Barberfish, 2-col left=native right=Barberfish.

    Single-col multi-row pages contain Barberfish-only test cells.
    """
    if page.suffix == "n":
        return "native"
    if page.suffix == "b":
        return "barberfish"
    if page.cols >= 2:
        return "native" if col == 0 else "barberfish"
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
                "cell_x0",
                "cell_y0",
                "cell_x1",
                "cell_y1",
                "header_top",
                "header_bottom",
                "value_top",
                "value_baseline",
                "value_band_width_pct",
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
                        *c.bounds,
                        c.header_top,
                        c.header_bottom,
                        c.value_top,
                        c.value_baseline,
                        round(band_w_pct, 3),
                        c.note,
                    ]
                )


def pair_native_barberfish(pages: list[Page]):
    """Yield (label, native_cell, bf_cell) pairs.

    For 2-col layouts: pair native (col 0) and Barberfish (col 1) per row.
    For 1x1n + 1x1b: pair the two single-cell pages directly.
    """
    pages_by_name = {p.name: p for p in pages}

    if "1x1n" in pages_by_name and "1x1b" in pages_by_name:
        n_cells = pages_by_name["1x1n"].cells
        b_cells = pages_by_name["1x1b"].cells
        if n_cells and b_cells:
            yield "1x1 (n vs b)", n_cells[0], b_cells[0]

    for p in pages:
        if p.cols < 2 or p.suffix:
            continue
        rows = defaultdict(dict)
        for c in p.cells:
            rows[c.row][c.col] = c
        for r in sorted(rows):
            row = rows[r]
            if 0 in row and 1 in row:
                yield f"{p.name} row {r}", row[0], row[1]


def fmt_delta(a: int | None, b: int | None) -> tuple[str, int | None]:
    """Native - Barberfish, with PASS/FAIL marker against TOLERANCE_PX."""
    if a is None or b is None:
        return "—", None
    d = a - b
    mark = "PASS" if abs(d) <= TOLERANCE_PX else "FAIL"
    return f"{d:+d} {mark}", d


def write_summary(pages: list[Page], path: Path) -> None:
    lines = ["# Baseline alignment summary", ""]
    lines.append(f"Tolerance: ±{TOLERANCE_PX} px")
    lines.append("")
    lines.append("Δ = native − Barberfish (in pixels). Negative Δvalue_baseline")
    lines.append("means Barberfish baseline sits BELOW native.")
    lines.append("")

    deltas_by_layout: dict[tuple[int, int], list[int]] = defaultdict(list)
    pair_rows = []
    for label, n, b in pair_native_barberfish(pages):
        ht_str, _ = fmt_delta(n.header_top, b.header_top)
        hb_str, _ = fmt_delta(n.header_bottom, b.header_bottom)
        vt_str, _ = fmt_delta(n.value_top, b.value_top)
        vb_str, vb_d = fmt_delta(n.value_baseline, b.value_baseline)
        pair_rows.append((label, ht_str, hb_str, vt_str, vb_str))
        if vb_d is not None:
            # Map page name to (colSpan, rowSpan) using `60 / cols`, `60 / rows`
            page_name = label.split()[0]
            m = PAGE_RE.match(page_name)
            if m:
                rows, cols = int(m.group(1)), int(m.group(2))
                key = (60 // cols, 60 // rows)
                deltas_by_layout[key].append(vb_d)

    lines.append("| Pair | Δheader_top | Δheader_bot | Δvalue_top | Δvalue_baseline |")
    lines.append("|---|---|---|---|---|")
    for label, ht, hb, vt, vb in pair_rows:
        lines.append(f"| {label} | {ht} | {hb} | {vt} | {vb} |")
    lines.append("")

    lines.append("## Auto-suggested baselineRefSp adjustments")
    lines.append("")
    lines.append(f"K = {AUTO_TUNE_K} sp/px (heuristic; refine after first iteration).")
    lines.append("")
    lines.append("```kotlin")
    lines.append("// Paste into ViewSizeConfig.kt baselineRefSp lookup. Adjust signs if")
    lines.append("// the visual result moves the wrong way and rerun.")
    for (col_span, row_span), ds in sorted(deltas_by_layout.items()):
        avg = sum(ds) / len(ds)
        delta_sp = AUTO_TUNE_K * avg
        sign = "+" if delta_sp >= 0 else ""
        lines.append(
            f"// colSpan={col_span} rowSpan={row_span}: Δavg={avg:+.1f}px ({len(ds)} pair) "
            f"-> valueFontBase {sign}{delta_sp:.1f}f"
        )
    lines.append("```")
    lines.append("")

    lines.append("## Per-page detection notes")
    for p in pages:
        n_with_value = sum(1 for c in p.cells if c.value_baseline is not None)
        n_with_header = sum(1 for c in p.cells if c.header_bottom is not None)
        lines.append(
            f"- `{p.name}` ({p.rows}×{p.cols}): {len(p.cells)} cells, "
            f"{n_with_header} headers, {n_with_value} values"
        )

    path.write_text("\n".join(lines))


def annotate(page: Page, out: Path) -> None:
    img = page.image.copy()
    draw = ImageDraw.Draw(img)
    for c in page.cells:
        x0, y0, x1, y1 = c.bounds
        draw.rectangle([x0, y0, x1 - 1, y1 - 1], outline=(255, 255, 255))
        col_baseline = (255, 0, 0) if c.side == "native" else (0, 255, 255)
        col_header = (255, 255, 0)
        if c.value_baseline is not None:
            draw.line([x0, c.value_baseline, x1, c.value_baseline], fill=col_baseline, width=2)
        if c.header_bottom is not None:
            draw.line([x0, c.header_bottom, x1, c.header_bottom], fill=col_header, width=2)
    img.save(out, "JPEG", quality=85)


# --- main -------------------------------------------------------------------


def main() -> None:
    pages = load_pages()
    if not pages:
        print(f"no screencaps under {SCREENCAPS}")
        return

    used_uniform = []
    for p in pages:
        bounds, note = detect_cells(p)
        if note != "borders":
            used_uniform.append(p.name)
        for i, b in enumerate(bounds):
            row = i // p.cols
            col = i % p.cols
            side = label_side(p, row, col)
            cell = Cell(p.name, row, col, side, b, note=note)
            measure_cell(cell, p.gray)
            p.cells.append(cell)

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
    main()
