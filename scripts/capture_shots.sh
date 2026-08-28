#!/usr/bin/env bash
# Reproduce the on-device README/gallery screenshots so they don't go stale on
# version bumps. Each "shot" navigates a pre-staged Karoo to a known screen and
# captures it. This is the LIVE-DEVICE set only; the fixture-rendered set
# (all_fields, grade/profile states, palettes) stays with render_previews.sh.
# The Barberfish config-screen shots (config, hud_config, design_barberfish)
# are rendered from Compose instead, by scripts/render_config_shots.sh, which
# also adds a palette_config shot. threshold_config is retired entirely.
#
# Pre-staged device assumption: the Barberfish profile, data pages, and per-field
# config are already set up as you want them shown. This script navigates and
# captures; it does not mutate your config, with one exception: the ride shots
# briefly set the HUD config to drive the on-device HUD, then restore the
# snapshot taken at session start.
#
# Ride shots additionally assume: the Barberfish profile is selected in
# ride-replay with its four data pages; replay sensors are paired to that
# profile; the Tranquilo ride is starred in ride-replay and its recording is
# at /sdcard/FitFiles/tranquilo.fit; pages 3-4 field colorMode is pre-staged
# per shot; the debug APK (with HardwareActionReceiver / ConfigReceiver) is
# installed. K3 only (single device).
#
# Taps and crops are anchored on element *text* via _shot_ui.py + uiautomator,
# not fixed coordinates, so a reordered or restructured settings screen still
# resolves, except the in-ride rideapp screens (ride_start/ride_load_route/
# ride_end/goto_page), which don't dump reliably and use fixed coordinates
# instead.
#
# Usage:
#   scripts/capture_shots.sh                 # all shots below
#   OUTDIR=docs/screenshots scripts/capture_shots.sh   # write straight to the committed dir
#
# Shots (increment 1, no ride needed):
#   design_karoo
# Shots (increment 2, share one discardable ride session):
#   hud_sparkline climbs_counter climbs_profile barberfish_fields light_mode
#   karoo_vs_barberfish
set -euo pipefail
cd "$(dirname "$0")/.."

OUTDIR="${OUTDIR:-screencaps/shots}"
STAGE="$(mktemp -d)"
UI="$STAGE/ui.xml"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$OUTDIR"

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

nudge_to_top() { # bring the matched element near the top of the viewport
    # small upward scroll so the anchor sits below the app bar, not at the very edge
    scroll_up; settle
}

# ---- debug broadcast helpers (debug APK only) -------------------------------
PKG=com.jpweytjens.barberfish
bcast() { # bcast <ReceiverClass> <ACTION> [extra args...]
    A shell am broadcast -n "$PKG/.extension.$1" -a "$PKG.$2" -f 0x01000000 "${@:3}" \
        >/dev/null 2>&1
}
press_button() { bcast HardwareActionReceiver PRESS_BUTTON --es button "$1"; settle 1; }

CFG_PUSH=/sdcard/Android/data/$PKG/files/bf_config.json   # app external files dir: readable
                                                          # by the app under scoped storage;
                                                          # an arbitrary /sdcard path is not
                                                          # (EACCES on Android 12 / targetSdk 34)
config_set() { # config_set <name> <local json file> — push and apply, wait for re-render
    [[ -f "$2" ]] || { echo "  ! no such fixture: $2" >&2; return 1; }
    A push "$2" "$CFG_PUSH" >/dev/null 2>&1
    bcast ConfigReceiver SET_CONFIG --es name "$1" --es file "$CFG_PUSH"
    settle 2
}
config_get() { # config_get <name> <local out file> — dump live config and pull
    bcast ConfigReceiver GET_CONFIG --es name "$1"; settle 1
    A pull /sdcard/Android/data/$PKG/files/"$1"_config.json "$2" >/dev/null 2>&1
}
set_hud() { config_set hud "$1"; }
get_hud() { config_get hud "$1"; }

# ---- ride-replay control (it.gangitano.karooridereplay) ----------------------
RR=it.gangitano.karooridereplay/.MainActivity
replay_open() { A shell am start -n "$RR" >/dev/null 2>&1; settle 2; wake; }
replay_load() { # open replay, pick tranquilo, start playback
    replay_open
    if ui has text "SELECT RIDE" >/dev/null 2>&1; then
        scroll_to text* "tranquilo" || { echo "  ! tranquilo not in replay list" >&2; return 1; }
        tap text* "tranquilo"; settle 2
    fi
    ui has text "Play" >/dev/null 2>&1 && { tap text "Play"; settle 2; }
}
replay_pause() { replay_open; ui has text "Pause" >/dev/null 2>&1 && { tap text "Pause"; settle 1; }; }
replay_seek() { # replay_seek <fraction 0..1> — tap the scrubber track at that fraction
    replay_open
    local x; x=$(awk -v f="$1" 'BEGIN{printf "%d", 55 + f*(455-55)}')
    tap_xy "$x" 312; settle 1   # track y verified on-device; adjust if the thumb does not move
}

# ---- rideapp ride lifecycle (fixed coords where the ride screen won't dump) --
ride_start() { # from ride-replay's replay screen: To ride -> start the ride
    ui has text "To ride" >/dev/null 2>&1 && { tap text "To ride"; settle 3; }
    tap_xy 429 732; settle 6   # green play FAB on the profile carousel
}
ride_load_route() { # ride_load_route <route name> — control center -> ADD Route -> Follow
    press_button control_center; settle 1
    tap_xy 239 184; settle 3            # ADD Route tile
    scroll_to text* "$1" || { echo "  ! route not found: $1" >&2; return 1; }
    local xy; xy=$(ui tap text* "$1"); tap_xy $xy; settle 3   # open the route detail
    ui has text "Follow route" >/dev/null 2>&1 && { tap text "Follow route"; settle 5; }
}
ride_end() { # finish flag -> confirm -> Delete -> confirm (discard the throwaway recording)
    tap_xy 40 732; settle 2             # finish flag (bottom-left of the map overlay)
    tap_xy 429 732; settle 4            # confirm end
    scroll_to text "Delete" || { echo "  ! Delete not found on summary" >&2; return 1; }
    tap text "Delete"; settle 2
    tap_xy 429 732; settle 3            # confirm delete
}

# ---- data-page navigation ----------------------------------------------------
goto_page() { # goto_page <n> — swipe left to reach data page n (1-based), from page 1
    tap_xy 240 400; settle 1            # tap map to make sure the ride view has focus
    local i
    for (( i=1; i<$1; i++ )); do A shell input swipe 400 400 80 400 250; settle 1; done
}
settle_drawer() { settle "${1:-6}"; }   # the bottom pill auto-hides after a few idle seconds

# ---- capture / crop ----------------------------------------------------------
cap() { wake; A exec-out screencap -p > "$STAGE/$1.png"; }

crop_band() { # crop_band <src.png> <y1> <y2> <out> — full width minus scrollbar
    magick "$1" -crop "$((W-12))x$(( $3 - $2 ))+0+$2" +repage -quality 92 "$4"
}

box_y1() { ui box "$@" | awk '{print $2}'; }   # top edge of a matched node

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

# ============================= ride session ==================================
# The ride shots share one discardable ride: snapshot the user's HUD, start the
# replay + ride, load the route once (RIDE REMAINING / OVERVIEW / PROFILE / climbs
# all need it), capture, then end+discard and restore the HUD.
RIDE_SHOTS=(hud_sparkline climbs_counter climbs_profile barberfish_fields light_mode karoo_vs_barberfish)
SESSION_UP=0
session_start() {
    (( SESSION_UP )) && return 0
    get_hud "$STAGE/hud_saved.json"
    replay_load
    ride_start
    ride_load_route Tranquilo
    SESSION_UP=1
}
session_end() {
    (( SESSION_UP )) || return 0
    ride_end
    [[ -f "$STAGE/hud_saved.json" ]] && set_hud "$STAGE/hud_saved.json"
    SESSION_UP=0
}
needs_session() { # true if any requested target is a ride shot
    local t s
    for t in "$@"; do for s in "${RIDE_SHOTS[@]}"; do [[ "$t" == "$s" ]] && return 0; done; done
    return 1
}

# ============================= shots =========================================

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
ALL=(design_karoo hud_sparkline climbs_counter climbs_profile barberfish_fields light_mode karoo_vs_barberfish)
targets=("$@"); [[ ${#targets[@]} -eq 0 ]] && targets=("${ALL[@]}")

require_device || { echo "no device" >&2; exit 1; }
keep_awake
echo "capturing to $OUTDIR"

needs_session "${targets[@]}" && session_start
for s in "${targets[@]}"; do
    if declare -F "shot_$s" >/dev/null; then
        "shot_$s"
    else
        echo "unknown shot: $s (known: ${ALL[*]})" >&2
    fi
done
session_end

A shell dumpsys deviceidle enable >/dev/null 2>&1 || true
echo "done."
