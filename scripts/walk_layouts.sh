#!/usr/bin/env bash
# Capture every data-field layout of the "Barberfish sweep" profile on a fresh ride, with
# scripts/capture_layout.sh, for scripts/measure_alignment.py.
#
# From the K3 at rest (no ride running), the walk keeps the device awake, switches it to the dark
# theme the measurement is tuned for, starts a ride on the sweep profile, finds its map page,
# steps through the data pages capturing each, then ends and deletes the ride and restores the
# theme.
# The ride is ended and the theme restored even when a step fails.
#
# Usage: OUTDIR=screencaps/<run> scripts/walk_layouts.sh [page1 page2 ...]
#   OUTDIR  where captures land (default: screencaps/)
#   pages   names for the data pages in order (default: the ten below)
#
# Assumes: K3 only (the ride start and end tap K3 positions); the debug APK is installed (the ride
# end presses its buttons through a debug receiver); the "Barberfish sweep" profile has the map as
# page 1, then the data pages below; and Data Boundaries is on in the Karoo's data field settings,
# which the cell detection needs. Set the Label Size and Barberfish design for the run first.

set -Eeuo pipefail

cd "$(dirname "$0")/.."
STAGE="$(mktemp -d)"
source scripts/_karoo.sh

PROFILE="Barberfish sweep"

# Page order matches the sweep profile's data pages. 1x1 is split into two pages because a single
# cell can't show native and Barberfish side by side:
#   1x1n  = 1-cell page with a native field
#   1x1b  = 1-cell page with a Barberfish field
DEFAULT_PAGES=(1x1n 1x1b 2x1 3x1 4x1 5x1 2x2 3x2 4x2 5x2)
pages=("$@")
if [[ ${#pages[@]} -eq 0 ]]; then
    pages=("${DEFAULT_PAGES[@]}")
fi

RIDE_UP=0
on_exit() { # end our ride and put the device back, whether the walk finished or failed
    local rc=$?
    set +e
    if (( RIDE_UP )); then
        (( rc )) && echo "  ! walk failed; ending the ride" >&2
        ride_end || echo "  ! could not end the ride: end it on the device" >&2
    fi
    restore_theme
    A shell dumpsys deviceidle enable >/dev/null 2>&1
    rm -rf "$STAGE"
}
trap on_exit EXIT

if ride_running; then
    echo "  ! a ride is already running; end it on the device first" >&2
    exit 1
fi

screen_size
echo "screen: ${W}x${H}"
keep_awake
save_theme
A shell cmd uimode night yes >/dev/null 2>&1
# Barberfish reads the theme when it renders; restart it so the ride's fields come up dark.
A shell am force-stop com.jpweytjens.barberfish >/dev/null 2>&1

echo "start a ride on $PROFILE"
start_ride "$PROFILE"
RIDE_UP=1
# Clear the log so each page's capture reads only the cell sizes logged on this ride.
A logcat -c

# A new ride opens on whichever page was last shown, and the pager wraps around, so step forward
# until the map is in front. next_page retries the swipes the pager drops.
for (( i = 0; i <= ${#pages[@]} + 1; i++ )); do
    wake
    map_page_like && break
    next_page
done
map_page_like || { echo "  ! map page not found" >&2; exit 1; }

for page in "${pages[@]}"; do
    echo "-> $page"
    wake
    next_page
    wake
    scripts/capture_layout.sh "$page"
done

echo
echo "done. ${#pages[@]} pages captured under ${OUTDIR:-screencaps}/"
echo "next: uv run scripts/measure_alignment.py --dir ${OUTDIR:-screencaps}"
