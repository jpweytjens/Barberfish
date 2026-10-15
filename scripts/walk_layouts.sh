#!/usr/bin/env bash
# Walk the 9 Barberfish data pages on an active ride, capturing each with
# scripts/capture_layout.sh.
#
# Defaults assume the profile pages are ordered as set up:
#   1x1, 2x1, 3x1, 4x1, 5x1, 2x2, 3x2, 4x2, 5x2
# (rows x cols, matching how the user labeled them on-device).
#
# Usage: scripts/walk_layouts.sh [page1 page2 ...]
#
# Before running:
#   - Start a ride on the Karoo with the Barberfish profile active.
#   - Navigate to the FIRST page in the list (default: 1x1).
#   - Then run this script. It swipes right-to-left between captures.

set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/_karoo.sh

# Page order matches the user's Karoo profile (10 pages). 1x1 is split into two
# pages because a single cell can't show native + Barberfish side-by-side:
#   1x1n  = 1-cell page with native field
#   1x1b  = 1-cell page with Barberfish field
DEFAULT_PAGES=(1x1n 1x1b 2x1 3x1 4x1 5x1 2x2 3x2 4x2 5x2)
pages=("$@")
if [[ ${#pages[@]} -eq 0 ]]; then
    pages=("${DEFAULT_PAGES[@]}")
fi

# Swipe coordinates follow the screen, so the walk works on both K2 (landscape) and K3 (portrait).
screen_size
echo "screen: ${W}x${H}"

for i in "${!pages[@]}"; do
    page="${pages[$i]}"
    wake
    if [[ $i -gt 0 ]]; then
        echo "-> swipe to next ($page)"
        swipe_next
        settle 0.8
    else
        echo "-> assuming you're already on $page"
        settle 0.8
    fi
    wake
    scripts/capture_layout.sh "$page"
done

echo
echo "done. ${#pages[@]} pages captured under screencaps/"
echo "next: review dumpsys/logcat per page, then we tabulate."
