#!/usr/bin/env bash
# Capture per-page measurement data for the baseline-alignment investigation.
#
# Usage: scripts/capture_layout.sh <page_name>
#   page_name : label used for output filenames (e.g. 1x1n, 5x2)
#
# Writes flat under screencaps/:
#   screencaps/<page_name>.jpg
#   screencaps/<page_name>.dumpsys.txt
#   screencaps/<page_name>.logcat.txt
#
# Assumes adb is on PATH and a single Karoo is connected on an active ride
# with the target page visible.

set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "usage: $0 <page_name>" >&2
    exit 2
fi

page="$1"
mkdir -p screencaps

# 1) View bounds — both native + ours come back together.
adb shell "dumpsys activity top" \
    | grep -E "field_root|header_spacer|field_header|baseline_box|baseline_ref|field_value|dataElementRoot|headerLayout|dataTextView" \
    > "screencaps/${page}.dumpsys.txt" || true

# 2) Recent extension log. The always-on line in BarberfishDataType has shape:
#    "density=... cellH=...dp cellW=...px textSize=...sp gridSize=(...) → headerSp=... typeId=..."
adb logcat -d -s "Barberfish" \
    | grep -E "textSize=|gridSize=" \
    | tail -40 \
    > "screencaps/${page}.logcat.txt" || true

# 3) Screencap. Use raw adb so we control the filename — swim ss parses any
#    digit-leading arg (like "1x1") as a duration via its [0-9]* glob and
#    falls back to a default timestamp.
adb exec-out screencap -p > "screencaps/${page}.jpg"

echo "captured $page"
ls -la "screencaps/${page}".* 2>/dev/null || true
