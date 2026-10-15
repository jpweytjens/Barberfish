#!/usr/bin/env python3
"""Name the data-page layout in a Karoo ride screencap, as rows x cols.

Used by walk_layouts.sh to check that a swipe landed on the page it expects: the ride pager drops
swipes, and a dropped swipe can't be told from ticking digits by comparing screens.

Needs Data Boundaries on: data cells are then black boxes on a neutral grey background. Counting
the boxes along a line just inside the screen's left edge gives the rows, and along a line just
inside the bottom edge of the first row gives the columns. Neither line crosses text, whose
antialiased edges are the same grey, and a gap must be neutral, so a magenta probe header (the
same luminance as the grey) does not count as one.

The two single-cell pages of the sweep profile share a grid. Its native cell ends at mid-screen
while the Barberfish cell fills the page, so a single cell taller than half the screen is "1x1b".

Usage:
    _page_grid.py SCREENCAP     # prints e.g. "3x2", "1x1n" or "1x1b"; "?" if no grid is found
"""

import subprocess
import sys

GAP_LO, GAP_HI = (
    55,
    95,
)  # the neutral grey of the gaps and the status bar (72 and 79 on K3)
MIN_CELL = 30  # px
EDGE = 6  # px in from the screen's left edge: inside the cells, left of every icon and digit
BOTTOM = (
    3  # px above the first row's bottom edge: values end 13 px or more above it, while
)
# 5-row native labels reach to within 4 px of the top edge


def rgb(path):
    w, h = map(
        int,
        subprocess.run(
            ["magick", "identify", "-format", "%w %h", path],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.split(),
    )
    raw = subprocess.run(
        ["magick", path, "-depth", "8", "rgb:-"], capture_output=True, check=True
    ).stdout
    return w, h, raw


def cells(pixels):
    """(start, end) of each run of non-gap pixels at least MIN_CELL long."""
    runs, start = [], None
    for i, (r, g, b) in enumerate(pixels + [(GAP_LO,) * 3]):
        gap = GAP_LO <= r <= GAP_HI and abs(r - g) <= 6 and abs(r - b) <= 6
        if not gap and start is None:
            start = i
        elif gap and start is not None:
            if i - start >= MIN_CELL:
                runs.append((start, i))
            start = None
    return runs


def main():
    w, h, raw = rgb(sys.argv[1])

    def px(x, y):
        i = 3 * (y * w + x)
        return tuple(raw[i : i + 3])

    rows = cells([px(EDGE, y) for y in range(h)])
    if not rows:
        print("?")
        return
    y0, y1 = rows[0]
    cols = cells([px(x, y1 - BOTTOM) for x in range(w)])
    name = f"{len(rows)}x{len(cols)}"
    if name == "1x1":
        name += "b" if y1 - y0 > h // 2 else "n"
    print(name)


main()
