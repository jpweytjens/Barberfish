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

# Left-margin fling: scrolls from x=14, clear of the pill/preview hitboxes that
# span the card width. A center swipe that starts on a pill toggles it instead of
# scrolling; this doesn't.
scroll_lm() { A shell input swipe 14 $((H*62/100)) 14 $((H*47/100)) 450; }
# ... and the reverse (finger down = content down), for two-sided convergence.
scroll_lm_down() { A shell input swipe 14 $((H*47/100)) 14 $((H*62/100)) 450; }

# Scroll to the bottom of the page: keep flinging until the target selector's
# bounds stop changing (max scroll). Lifts a target out of the bottom-left Back
# FAB's zone. Uses the aggressive scroll_up fling, not scroll_step — near the
# content edge the gentle step doesn't overcome Compose's scroll threshold and
# moves nothing. The target must already be on screen — call scroll_to first.
scroll_to_bottom() { # scroll_to_bottom <selector...>
    local prev="" cur tries=0
    while (( tries < 8 )); do
        wake; dump; cur=$(ui box "$@" 2>/dev/null)
        [[ -n "$cur" && "$cur" == "$prev" ]] && return 0
        prev=$cur; scroll_up; settle
        (( tries++ ))
    done
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

crop_full() { # crop_full <src.png> <y1> <y2> <out> — full device width (keeps a card's right edge)
    magick "$1" -crop "${W}x$(( $3 - $2 ))+0+$2" +repage -quality 92 "$4"
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
    # screenshot mode freezes the HUD preview animation (fixed values + a fixed sweep
    # position between the two climbs) so hud_config is reproducible.
    A shell am start -n "$MAIN" --ez screenshot true >/dev/null 2>&1
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
    # reveal the full preview strip: scroll_to lands it at the bottom edge (only a
    # sliver on screen). Step down until the whole strip and its cells are visible.
    # scroll_step starts above the ELEVATION PROFILE pills, so it scrolls the page
    # instead of tapping a pill (scroll_up starts on the pill row and gets eaten).
    local pt pb r=0
    while (( r < 5 )); do
        dump; pt=$(box_y1 res "bf:hud:preview"); pb=$(box_y2 res "bf:hud:preview")
        { [[ -n "$pt" && -n "$pb" ]] && (( pb - pt >= 120 )) && (( pb <= H*88/100 )); } && break
        scroll_step; settle; (( r++ ))
    done
    tap hud-col 1; settle          # select the first (Speed) column
    # The preview is frozen in capture mode, so the ELEVATION-PROFILE..Data-field
    # block is a fixed-height slab. Converge the profile row into a band where that
    # whole slab is on-screen and clear of the fixed header, then crop a FIXED height
    # anchored to it — so the frame is identical every run regardless of the exact
    # scroll offset. Left-margin scrolls clear the pill hitboxes.
    local pt c=0
    while (( c < 12 )); do
        dump; pt=$(box_y1 res "bf:hud:profile")
        if [[ -z "$pt" ]]; then scroll_lm; settle; (( c++ )); continue; fi
        (( pt >= 240 && pt <= 300 )) && break
        if (( pt > 300 )); then scroll_lm; else scroll_lm_down; fi
        settle; (( c++ ))
    done
    dump
    local ptop
    ptop=$(box_y1 res "bf:hud:profile")
    cap hud_config
    # ptop is the ELEVATION PROFILE pill row; its label sits 53px above it. Crop from
    # 62px above (the heading) down a fixed 550px, through the Data field panel.
    crop_full "$STAGE/hud_config.png" $((ptop-62)) $((ptop+488)) "$OUTDIR/hud_config.jpg"
    echo "  -> $OUTDIR/hud_config.jpg"
}

shot_threshold_config() {
    echo "threshold_config: Avg Speed Total THRESHOLD block"
    bf_fresh
    tap res "bf:section:data-fields"; settle
    scroll_to res "bf:field:avg-speed-total"
    tap res "bf:field:avg-speed-total"; settle   # expand the Avg Speed Total card
    scroll_to res "bf:field:threshold"
    # bring the THRESHOLD label to the top, then crop a fixed height from it: the
    # frame is just the threshold config — the card title, preview and zone-color
    # scroll off above. Left-margin scrolls clear the pill/input hitboxes; the hidden
    # Back FAB (screenshot mode) keeps the bottom clean.
    local tt c=0
    while (( c < 12 )); do
        dump; tt=$(box_y1 res "bf:field:threshold")
        if [[ -z "$tt" ]]; then scroll_lm; settle; (( c++ )); continue; fi
        (( tt >= 150 && tt <= 185 )) && break
        if (( tt > 185 )); then scroll_lm; else scroll_lm_down; fi
        settle; (( c++ ))
    done
    dump
    local ttop
    ttop=$(box_y1 res "bf:field:threshold")
    cap threshold_config
    crop_full "$STAGE/threshold_config.png" $((ttop-8)) $((ttop+528)) "$OUTDIR/threshold_config.jpg"
    echo "  -> $OUTDIR/threshold_config.jpg"
}

shot_design_barberfish() {
    echo "design_barberfish: Data Field Design section (Data Icons + Label Size)"
    bf_fresh
    scroll_to res "bf:section:data-field-design"
    tap res "bf:section:data-field-design"; settle   # it's a collapsible card
    scroll_to res "bf:dfd:label-size"      # brings the full Data Icons + Label Size pair into view
    scroll_to_bottom res "bf:dfd:label-size"  # lift the pills clear of the bottom-left Back FAB
    dump
    local top bot
    top=$(box_y1 res "bf:dfd:data-icons")
    bot=$(box_y2 res "bf:dfd:label-size")
    cap design_barberfish
    crop_full "$STAGE/design_barberfish.png" $((top-12)) $((bot+16)) "$OUTDIR/design_barberfish.jpg"
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
