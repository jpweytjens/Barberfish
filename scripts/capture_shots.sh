#!/usr/bin/env bash
# Reproduce the on-device README/gallery screenshots so they don't go stale on
# version bumps. Each "shot" navigates a pre-staged Karoo to a known screen and
# captures it. This is the LIVE-DEVICE set only; the fixture-rendered set
# (all_fields, grade/profile states, palettes) stays with render_previews.sh.
#
# Pre-staged device assumption: the Barberfish profile, data pages, and per-field
# config are already set up as you want them shown. This script navigates and
# captures; it does not mutate your config (the one exception, restored in-shot,
# is noted where it happens).
#
# Taps and crops are anchored on element *text* via _shot_ui.py + uiautomator,
# not fixed coordinates, so a reordered or restructured settings screen still
# resolves. K3 only (single device).
#
# Usage:
#   scripts/capture_shots.sh                 # all shots below
#   scripts/capture_shots.sh config hud_config   # just these
#   OUTDIR=docs/screenshots scripts/capture_shots.sh   # write straight to the committed dir
#
# Shots (increment 1, no ride needed):
#   config  hud_config  threshold_config  design_barberfish  design_karoo
set -euo pipefail
cd "$(dirname "$0")/.."

OUTDIR="${OUTDIR:-screencaps/shots}"
STAGE="$(mktemp -d)"
UI="$STAGE/ui.xml"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$OUTDIR"

BF=com.jpweytjens.barberfish
MAIN="$BF/.screens.MainActivity"
KAROO_DFD=io.hammerhead.settingsapp/.dataFieldSettings.DataFieldSettingsActivity
FONT=/System/Library/Fonts/Supplemental/Arial.ttf

W=480; H=800   # K3 physical size (verified via `wm size`)

# ---- adb / ui helpers --------------------------------------------------------
# The Karoo suspends its USB controller when it dozes, re-enumerating on the bus
# and tearing down the adb transport (the cable stays plugged; `transport_id`
# changes). `keep_awake` prevents it; `A` survives it if it still happens.
require_device() {
    [[ "$(adb get-state 2>/dev/null)" == device ]] && return 0
    echo "  ... adb lost the device (USB re-enumerated); waiting" >&2
    timeout 60 adb wait-for-device || { echo "  ! device did not return" >&2; return 1; }
    sleep 2
}
A() { # resilient adb: on failure, wait for a re-enumeration and retry once
    adb "$@" && return 0
    require_device || return 1
    adb "$@"
}

keep_awake() { # hold the device awake and out of Doze for the run
    A shell svc power stayon true                     >/dev/null 2>&1 || true
    A shell dumpsys deviceidle disable                >/dev/null 2>&1 || true
    A shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true
}

wake()  { A shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true; }
settle(){ sleep "${1:-1}"; }

dump() { # refresh $UI with the current view hierarchy
    A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    A pull /sdcard/ui.xml "$UI" >/dev/null 2>&1
}

ui() { python3 scripts/_shot_ui.py "$UI" "$@"; }

tap_xy() { A shell input tap "$1" "$2"; }

tap() { # tap <selector...>  e.g. tap chevron HUD | tap hud-col 1 | tap text* "AVG SPEED"
    wake; dump
    local xy; xy=$(ui tap "$@") || { echo "  ! not found: $*" >&2; return 1; }
    tap_xy $xy
}

# swipe content up (finger drags up; the reliable direction).
# Big page nudge for explicit reveals; gentle step for search (short + slow =
# little fling, so a short settings section can't be skipped between dumps).
scroll_up()   { A shell input swipe $((W/2)) $((H*8/10)) $((W/2)) $((H*2/10)) 400; }
scroll_step() { A shell input swipe $((W/2)) $((H*7/10)) $((W/2)) $((H*4/10)) 600; }

scroll_to() { # scroll_to <selector...> — step down until the target text is on screen
    local tries=0
    while (( tries < 14 )); do
        wake; dump
        if ui has "$@" >/dev/null 2>&1; then return 0; fi
        scroll_step; settle
        (( tries++ ))
    done
    echo "  ! never found while scrolling: $*" >&2; return 1
}

wait_text() { # block until a selector appears (content rendered), or time out
    local tries=0
    while (( tries < 12 )); do
        wake; dump
        if ui has "$@" >/dev/null 2>&1; then return 0; fi
        settle 1; (( tries++ ))
    done
    echo "  ! content never appeared: $*" >&2; return 1
}

nudge_to_top() { # bring the matched element near the top of the viewport
    # small upward scroll so the anchor sits below the app bar, not at the very edge
    scroll_up; settle
}

# ---- capture / crop ----------------------------------------------------------
cap() { wake; A exec-out screencap -p > "$STAGE/$1.png"; }

publish_full() { # publish_full <name>  — full screen, saved as jpg
    magick "$STAGE/$1.png" -quality 92 "$OUTDIR/$1.jpg"
    echo "  -> $OUTDIR/$1.jpg"
}

crop_band() { # crop_band <src.png> <y1> <y2> <out> — full width minus scrollbar
    magick "$1" -crop "$((W-12))x$(( $3 - $2 ))+0+$2" +repage -quality 92 "$4"
}

box_y1() { ui box "$@" | awk '{print $2}'; }   # top edge of a matched node
box_y2() { ui box "$@" | awk '{print $4}'; }   # bottom edge

frame_heading() { # scroll TEXT heading into the upper viewport; echo its y1.
    # Puts the heading high enough that its ~150px panel (help + toggle) sits
    # fully on screen and clear of the bottom Back FAB.
    local t=0 y
    while (( t < 6 )); do
        dump; y=$(box_y1 text "$1")
        if [[ -n "$y" ]] && (( y > 60 && y < H*45/100 )); then echo "$y"; return 0; fi
        scroll_step; settle; (( t++ ))
    done
    dump; box_y1 text "$1"   # best effort
}

# ---- Barberfish app navigation ----------------------------------------------
bf_fresh() {
    wake
    A shell am force-stop "$BF"
    settle 1
    A shell am start -n "$MAIN" >/dev/null 2>&1
    # Compose cold start renders blank for a beat; wait for real content, not a
    # fixed sleep, or the capture catches an empty white frame.
    wait_text res "bf:section:palettes"
    wake
}

# ---- theme -------------------------------------------------------------------
SAVED_NIGHT=""
save_theme() { SAVED_NIGHT=$(A shell cmd uimode night | tr -d '\r'); }
set_day()    { A shell cmd uimode night no  >/dev/null 2>&1; settle 1; }
restore_theme() {
    case "$SAVED_NIGHT" in
        *yes) A shell cmd uimode night yes >/dev/null 2>&1 ;;
        *no)  A shell cmd uimode night no  >/dev/null 2>&1 ;;
    esac
}

# ============================= shots =========================================

shot_config() {
    echo "config: main screen overview (all sections collapsed)"
    bf_fresh
    cap config
    publish_full config
}

shot_hud_config() {
    echo "hud_config: HUD expanded, Speed column selected"
    bf_fresh
    tap res "bf:section:hud"; settle
    scroll_to res "bf:hud:preview"
    scroll_up; settle              # reveal the full preview strip below the instruction
    tap hud-col 1; settle          # select the first (Speed) column
    # frame the preview + the selected column's Data field panel (proves per-slot config)
    scroll_to res "bf:hud:data-field"
    cap hud_config
    publish_full hud_config
}

shot_threshold_config() {
    echo "threshold_config: Avg Speed Total field expanded (zone color + threshold)"
    bf_fresh
    tap res "bf:section:data-fields"; settle
    scroll_to res "bf:field:avg-speed-total"
    tap res "bf:field:avg-speed-total"; settle   # expand the Avg Speed Total card
    # frame the colored preview + ZONE COLOR + start of THRESHOLD (the shot's story)
    scroll_to res "bf:field:zone-color"
    cap threshold_config
    publish_full threshold_config
}

shot_design_barberfish() {
    echo "design_barberfish: Data Field Design section (Data Icons + Label Size)"
    bf_fresh
    scroll_to res "bf:section:data-field-design"
    tap res "bf:section:data-field-design"; settle   # it's a collapsible card
    scroll_to res "bf:dfd:label-size"      # brings the full Data Icons + Label Size pair into view
    dump
    local top bot
    top=$(box_y1 res "bf:dfd:data-icons")
    bot=$(box_y2 res "bf:dfd:label-size")
    cap design_barberfish
    crop_band "$STAGE/design_barberfish.png" $((top-12)) $((bot+16)) "$OUTDIR/design_barberfish.jpg"
    echo "  -> $OUTDIR/design_barberfish.jpg"
}

shot_design_karoo() {
    echo "design_karoo: Karoo native Data Icons + Label Size (day mode, cropped pair)"
    save_theme; set_day
    A shell am force-stop io.hammerhead.settingsapp >/dev/null 2>&1 || true
    A shell am start -n "$KAROO_DFD" >/dev/null 2>&1
    settle 2; wake

    # Screen order is Data Boundaries -> Label Size -> Data Icons, and scroll_to
    # only goes down, so capture Label Size first, then Data Icons.
    # Order is Data Boundaries -> Label Size -> Data Icons; scroll_to only goes
    # down, so capture Label Size first. frame_heading puts each heading high
    # enough that its panel is fully visible and clear of the Back FAB.
    local ly iy
    ly=$(frame_heading "Label Size")
    cap dk_label
    crop_band "$STAGE/dk_label.png" $((ly-10)) $((ly+150)) "$STAGE/ls.png"

    scroll_to text "Data Icons"
    iy=$(frame_heading "Data Icons")
    cap dk_icons
    crop_band "$STAGE/dk_icons.png" $((iy-10)) $((iy+150)) "$STAGE/di.png"

    # stack Data Icons over Label Size to match the reference composition
    magick "$STAGE/di.png" "$STAGE/ls.png" -append -background white \
        -quality 92 "$OUTDIR/design_karoo.jpg"
    echo "  -> $OUTDIR/design_karoo.jpg"
    restore_theme
}

# ============================= main ==========================================
ALL=(config hud_config threshold_config design_barberfish design_karoo)
targets=("$@"); [[ ${#targets[@]} -eq 0 ]] && targets=("${ALL[@]}")

require_device || { echo "no device" >&2; exit 1; }
keep_awake
echo "capturing to $OUTDIR"
for s in "${targets[@]}"; do
    if declare -F "shot_$s" >/dev/null; then
        "shot_$s"
    else
        echo "unknown shot: $s (known: ${ALL[*]})" >&2
    fi
done
A shell dumpsys deviceidle enable >/dev/null 2>&1 || true   # restore normal Doze
echo "done."
