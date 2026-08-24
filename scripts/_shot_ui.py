#!/usr/bin/env python3
"""Locate a tap target or element bounds in a uiautomator XML dump.

Used by capture_shots.sh to anchor taps and crops on element *text* rather than
fixed screen coordinates, so the capture flow survives config-screen layout
drift between Barberfish/Karoo versions.

Usage:
    _shot_ui.py XML tap  text   VALUE      # center 'x y' of node whose text == VALUE
    _shot_ui.py XML tap  text*  PREFIX     # ... whose text starts with PREFIX
    _shot_ui.py XML tap  desc   VALUE      # ... whose content-desc == VALUE
    _shot_ui.py XML tap  res    VALUE      # ... whose resource-id == VALUE
    _shot_ui.py XML tap  chevron CARDTEXT  # the Expand/Collapse control on CARDTEXT's row
    _shot_ui.py XML tap  hud-col N         # center of the Nth HUD preview column (1-based)
    _shot_ui.py XML has  text*  PREFIX     # exit 0 if present, 3 if not (for scroll loops)
    _shot_ui.py XML has  res    VALUE
    _shot_ui.py XML box  text   VALUE      # bounds 'x1 y1 x2 y2' of the matched node
    _shot_ui.py XML box  text*  PREFIX
    _shot_ui.py XML box  res    VALUE

Exit 3 when the target is not found.
"""

import re
import sys


def nodes(path):
    xml = open(path, encoding="utf-8").read()
    out = []
    for m in re.finditer(r"<node\b[^>]*>", xml):
        s = m.group(0)

        def attr(k):
            mm = re.search(k + r'="([^"]*)"', s)
            return mm.group(1) if mm else ""

        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        out.append(
            {
                "text": attr("text"),
                "desc": attr("content-desc"),
                "res": attr("resource-id"),
                "clickable": attr("clickable") == "true",
                "x1": x1,
                "y1": y1,
                "x2": x2,
                "y2": y2,
            }
        )
    return out


def match(ns, kind, value):
    """First node matching a text/text*/desc/res selector, in document order."""
    if kind == "text":
        return next((n for n in ns if n["text"] == value), None)
    if kind == "text*":
        return next((n for n in ns if n["text"].startswith(value)), None)
    if kind == "desc":
        return next((n for n in ns if n["desc"] == value), None)
    if kind == "res":
        return next((n for n in ns if n["res"] == value), None)
    return None


def chevron(ns, card_text):
    """The Expand/Collapse control sharing a row with the card whose text == card_text."""
    anchor = next((n for n in ns if n["text"] == card_text), None)
    if not anchor:
        return None
    for n in ns:
        if (
            n["desc"] in ("Expand", "Collapse")
            and n["y1"] <= anchor["y2"]
            and n["y2"] >= anchor["y1"]
        ):
            return n
    return None


def hud_column(ns, idx):
    """Nth cell of the HUD preview strip: the first row of >=3 equal-width cells
    inside the bf:hud:preview container."""
    strip = next((n for n in ns if n["res"] == "bf:hud:preview"), None)
    if not strip:
        return None
    inside = [n for n in ns if n["y1"] >= strip["y1"] and n["y2"] <= strip["y2"]]
    rows = {}
    for n in inside:
        w = n["x2"] - n["x1"]
        if 80 <= w <= 200:
            rows.setdefault(n["y1"], []).append(n)
    for y1 in sorted(rows):
        cells = sorted(rows[y1], key=lambda n: n["x1"])
        if len(cells) >= 3:
            if idx <= len(cells):
                return cells[idx - 1]
            return None
    return None


def center(n):
    print((n["x1"] + n["x2"]) // 2, (n["y1"] + n["y2"]) // 2)


def main():
    path, verb, kind = sys.argv[1], sys.argv[2], sys.argv[3]
    ns = nodes(path)

    if verb == "tap":
        if kind == "chevron":
            n = chevron(ns, sys.argv[4])
        elif kind == "hud-col":
            n = hud_column(ns, int(sys.argv[4]))
        else:
            n = match(ns, kind, sys.argv[4])
        if n:
            center(n)
            return
    elif verb == "has":
        n = match(ns, kind, sys.argv[4])
        if n:
            return
    elif verb == "box":
        n = match(ns, kind, sys.argv[4])
        if n:
            print(n["x1"], n["y1"], n["x2"], n["y2"])
            return
    sys.exit(3)


main()
