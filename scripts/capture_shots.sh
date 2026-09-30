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
# briefly set the HUD and palette config to drive the on-device fields (the
# Barberfish palettes unless a shot shows others), then restore the snapshot
# taken at session start.
#
# Ride shots additionally assume: the Barberfish profile is selected in
# ride-replay with its four data pages; replay sensors are paired to that
# profile; the Tranquilo ride is starred in ride-replay and its recording is
# at /sdcard/FitFiles/tranquilo.fit; pages 3-4 field colorMode is pre-staged
# per shot; the debug APK (with HardwareActionReceiver / ConfigReceiver) is
# installed. K3 only (single device). hud_hr_missing also needs a ride-replay build with
# per-sensor states ("Separate sensors" on, its four devices paired to the profile).
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
#   hud_sparkline palettes climbs_counter climbs_profile barberfish_fields light_mode
#   karoo_vs_barberfish grade_map hud_hr_missing
set -Eeuo pipefail
# Most helpers discard adb's output, so an unhandled failure would end the run without a word.
# Name the line and command instead. Quiet inside $(...), where the caller handles the failure.
trap 'rc=$? cmd=$BASH_COMMAND; if [[ $BASH_SUBSHELL -eq 0 ]]; then echo "  ! line $LINENO: $cmd (exit $rc)" >&2; fi' ERR
cd "$(dirname "$0")/.."

OUTDIR="${OUTDIR:-screencaps/shots}"
STAGE="$(mktemp -d)"
UI="$STAGE/ui.xml"
on_exit() { # a failed shot still ends the ride and restores the config, then drops the snapshot
    local rc=$?
    set +e
    if (( SESSION_UP )); then
        (( rc )) && echo "  ! run failed; ending the ride and restoring the config" >&2
        session_end || echo "  ! cleanup incomplete: check the ride and config on the device" >&2
    fi
    A shell dumpsys deviceidle enable >/dev/null 2>&1
    rm -rf "$STAGE"
}
trap on_exit EXIT
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
set_zone() { config_set zone "$1"; }   # power, HR and grade palettes
get_zone() { config_get zone "$1"; }
# The replay drives GPS, not the barometer, so a live Grade reads 0.0% all ride; pin it for
# shots that show the field, and clear it again (session_end clears it too).
# Written twice: the first pin after a live reading was seen not to reach a running Grade view,
# while a pin replacing another pin did, every time (K3, 2026-09-21). The first write is a
# throwaway value, which has to differ or the second write changes nothing.
pin_grade() { # pin_grade <fixture json> — the first write is a throwaway (see above)
    echo '{"percent": 0.3}' > "$STAGE/grade_pin_first.json"
    config_set grade_pin "$STAGE/grade_pin_first.json"
    config_set grade_pin "$1"
}
unpin_grade() { config_set grade_pin scripts/fixtures/grade_pin_off.json; }

# ---- ride-replay control (it.gangitano.karooridereplay) ----------------------
RR=it.gangitano.karooridereplay/.MainActivity
replay_open() { A shell am start -n "$RR" >/dev/null 2>&1; settle 2; wake; }
replay_load() { # open replay, pick tranquilo, start playback
    replay_open
    dump
    if ui has text "SELECT RIDE" >/dev/null 2>&1; then
        # Tranquilo is starred, so it sits at the top, but scroll_to only goes down and the
        # list keeps its last scroll position. Fling back to the top first.
        local i; for i in $(seq 1 15); do A shell input swipe $((W/2)) 200 $((W/2)) 780 150; done
        settle 1; dump
        scroll_to text* "tranquilo" || { echo "  ! tranquilo not in replay list" >&2; return 1; }
        tap text* "tranquilo"; settle 2
    fi
    dump
    # `if`, not `&&`: a false check as a function's last command is its exit status, and under
    # `set -e` that ends the run (seen when the replay was already playing).
    if ui has text "Play" >/dev/null 2>&1; then tap text "Play"; settle 2; fi
}
replay_pause() {
    replay_open; dump
    if ui has text "Pause" >/dev/null 2>&1; then tap text "Pause"; settle 1; fi
}
replay_resume() { # resume playback and go back to the ride; otherwise the next capture
    # shows the replay screen with the HUD reading "No data"
    dump; ui has text "Play" >/dev/null 2>&1 && { tap text "Play"; settle 1; }
    dump
    if ui has text "To ride" >/dev/null 2>&1; then tap text "To ride"; settle 3; fi
    ride_front
}
replay_seek() { # replay_seek <fraction 0..1> — tap the scrubber track at that fraction
    replay_open
    # The track spans x 34 to 445 (thumb centre at 0 and at the ride's end; K3, 2026-09-25).
    local x; x=$(awk -v f="$1" 'BEGIN{printf "%d", 34 + f*(445-34) + 0.5}')
    tap_xy "$x" 312; settle 1   # track y verified on-device; adjust if the thumb does not move
    # Scrubbing pauses playback and leaves the replay app in front.
    replay_resume
}

# ---- rideapp ride lifecycle (fixed coords where the ride screen won't dump) --
ride_start() { # from ride-replay's replay screen: To ride -> start the ride
    dump
    ui has text "To ride" >/dev/null 2>&1 && { tap text "To ride"; settle 3; }
    profile_front Barberfish
    tap_xy 429 732             # green play FAB on the profile carousel
    # The ride screen can take well over the old fixed 6 s to come up (K3, 2026-09-25); a page
    # swipe before it does moves the profile carousel instead. Wait for it to be in front.
    local i
    for i in $(seq 1 45); do
        settle 1
        if ride_in_front; then settle 3; return 0; fi
    done
    echo "  ! ride screen never came up" >&2; return 1
}
profile_front() { # profile_front <name> — home screen, with that profile's card in the middle
    # "To ride" uncovers whatever the launcher was left on (menu grid, app info, ...). Home lands on
    # the launcher's carousel or its menu grid, whichever it last showed, and Back on the grid flips
    # to the carousel (K3, 2026-09-30). Card titles end in a no-break space, and matching the whole
    # title keeps "Barberfish" from also taking "Barberfish sweep". Fling to the first card, then
    # step right until the named card spans the middle of the screen.
    local title="$1"$'\xc2\xa0' i box x1 x2
    A shell input keyevent KEYCODE_HOME; settle 2
    dump
    if ui has text "Rides" >/dev/null 2>&1; then A shell input keyevent KEYCODE_BACK; settle 1.5; fi
    for i in $(seq 1 8); do A shell input swipe 120 480 400 480 150; done
    settle 1
    for i in $(seq 1 12); do
        dump
        if box=$(ui box text "$title" 2>/dev/null); then
            read -r x1 _ x2 _ <<<"$box"
            if (( x1 < W/2 && x2 > W/2 )); then return 0; fi
        fi
        A shell input swipe 400 480 120 480 300; settle 1.2
    done
    echo "  ! profile $1 not on the home screen" >&2; return 1
}
ride_running() { # a ride screen exists in any task, in front or not
    A shell dumpsys activity activities 2>/dev/null | grep -q -F "rideapp/.views.ride.RideActivity"
}
ride_in_front() {
    A shell dumpsys activity activities 2>/dev/null \
        | grep -m1 -F "ResumedActivity=" | grep -q -F "rideapp/.views.ride.RideActivity"
}
ride_front() { # "To ride" only sends the replay to the back, so the screen beneath it comes up:
    # the home screen, or the route list the route was loaded from. Starting the ride screen's
    # component brings the running ride's task forward from any of them.
    (( RIDE_UP )) || return 0
    local i
    for i in $(seq 1 10); do
        if ride_in_front; then return 0; fi
        if (( i == 2 )); then
            A shell am start -n io.hammerhead.rideapp/.views.ride.RideActivity \
                -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >/dev/null 2>&1
        fi
        settle 1
    done
    echo "  ! ride screen not in front" >&2; return 1
}
cc_ride_panel() { # open the control center on its Ride panel
    # It opens on whichever panel was last shown and auto-closes after ~10 s. In-ride the
    # panels run System, Display, Ride, Devices; only Ride carries a yellow tab icon at
    # (403,53), so go to the left end and step right until that pixel is yellow.
    local i px
    press_button control_center; settle 1.2
    for i in 1 2 3; do A shell input swipe 80 300 400 300 200; settle 0.7; done
    for i in 1 2 3 4; do
        A exec-out screencap -p > "$STAGE/cc.png"
        px=$(magick "$STAGE/cc.png" -format '%[fx:int(255*p{403,53}.r)] %[fx:int(255*p{403,53}.g)] %[fx:int(255*p{403,53}.b)]' info:)
        read -r r g b <<< "$px"
        if (( r > 180 && g > 160 && b < 120 )); then return 0; fi
        A shell input swipe 400 300 80 300 300; settle 1.2
    done
    echo "  ! control center Ride panel not found" >&2; return 1
}
ride_load_route() { # ride_load_route <route name> — control center -> ADD Route -> search -> Follow
    cc_ride_panel || return 1
    tap_xy 239 184; settle 3            # ADD Route tile
    tap_xy 37 89; settle 2              # search (the full list is too long to scroll through)
    A shell input text "$1"; settle 2
    tap_xy 443 747; settle 3            # keyboard search key
    # Open the first result by position: in a dump the route name matches the search box first,
    # and tapping that only reopens the keyboard. The detail screen confirms it is the right route.
    tap_xy 240 600; settle 3
    dump
    if ! { ui has text "$1" && ui has text "Follow route"; } >/dev/null 2>&1; then
        echo "  ! route not found: $1" >&2; return 1
    fi
    tap text "Follow route"; settle 5
}
ride_end() { # pause -> finish flag -> confirm -> Delete -> confirm (discard the recording)
    # Pause, which shows the finish flag on any data page. A press sent while the ride screen
    # rebuilds (after a theme flip) is lost, and the taps below then land on the map; so check
    # that the top bar turned yellow, as it does paused (#FFE714; black or white riding).
    local i px r g b
    for i in 1 2 3; do
        press_button bottom_right; settle 2
        A exec-out screencap -p > "$STAGE/pause.png"
        px=$(magick "$STAGE/pause.png" -format '%[fx:int(255*p{20,85}.r)] %[fx:int(255*p{20,85}.g)] %[fx:int(255*p{20,85}.b)]' info:)
        read -r r g b <<< "$px"
        if (( r > 180 && g > 160 && b < 120 )); then break; fi
        (( i == 3 )) && { echo "  ! ride did not pause" >&2; return 1; }
        settle 2
    done
    tap_xy 40 732; settle 2             # finish flag (bottom-left of the pause overlay)
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
goto_page_like() { # goto_page_like <reference jpg> <crop WxH+X+Y> <max RMSE> — swipe left until
    # a crop of the screen matches the same crop of a committed shot.
    # "To ride" returns to whichever data page the ride was last on, so counting swipes from
    # page 1 lands anywhere. Recognize the page instead by a region that is fixed on it: the
    # Grade / Elapsed time label row on the fields page (RMSE 0.007 on a match, 0.27 or more
    # elsewhere), the column of map buttons on the map page (0.25 to 0.33 on a match, 0.85
    # elsewhere). `magick compare` exits 1 whenever the images differ, so its status is ignored
    # and only the printed distance is read.
    local i d
    magick "$1" -crop "$2" +repage "$STAGE/page_ref.png"
    tap_xy 240 400; settle 1
    for i in 1 2 3 4 5 6; do
        A exec-out screencap -p > "$STAGE/page_probe.png"
        magick "$STAGE/page_probe.png" -crop "$2" +repage "$STAGE/page_band.png"
        d=$(magick compare -metric RMSE "$STAGE/page_band.png" "$STAGE/page_ref.png" null: 2>&1 \
            | sed 's/.*(\(.*\))/\1/' || true)
        if awk -v d="$d" -v m="$3" 'BEGIN{exit !(d < m)}'; then return 0; fi
        A shell input swipe 400 400 80 400 250; settle 1.5
    done
    echo "  ! page like $1 not found" >&2; return 1
}
goto_map_page()    { goto_page_like docs/screenshots/hud_sparkline.jpg    90x280+18+405 0.5; }
goto_fields_page() { goto_page_like docs/screenshots/barberfish_fields.jpg 480x40+0+362  0.2; }
settle_drawer() { settle "${1:-6}"; }   # the bottom pill auto-hides after a few idle seconds
map_extensions_toggle() { # flip the puzzle toggle (extension map effects) in the map layers menu
    tap_xy 63 452; settle 1             # layers button, left edge of the map
    tap_xy 160 548; settle 1            # puzzle icon, second row of the menu
    tap_xy 63 452; settle 1             # close the menu
}

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
RIDE_SHOTS=(hud_sparkline palettes climbs_counter climbs_profile barberfish_fields light_mode karoo_vs_barberfish grade_map hud_hr_missing)
SESSION_UP=0   # the config snapshot is taken, so session_end has something to restore
RIDE_UP=0      # our ride is running, so session_end has a ride to end
session_start() {
    (( SESSION_UP )) && return 0
    if ride_running; then
        echo "  ! a ride is already running; end it on the device first" >&2; return 1
    fi
    get_hud "$STAGE/hud_saved.json"
    get_zone "$STAGE/zone_saved.json"
    SESSION_UP=1
    set_zone scripts/fixtures/zone/barberfish.json   # house palettes unless a shot sets others
    replay_load
    ride_start
    RIDE_UP=1
    ride_load_route Tranquilo
    check_sensors
}
session_end() { # restore the config first: it is the part a half-finished run must not lose
    (( SESSION_UP )) || return 0
    SESSION_UP=0
    unpin_grade
    [[ -f "$STAGE/hud_saved.json" ]] && set_hud "$STAGE/hud_saved.json"
    [[ -f "$STAGE/zone_saved.json" ]] && set_zone "$STAGE/zone_saved.json"
    [[ -f "$STAGE/time_saved.json" ]] && config_set time "$STAGE/time_saved.json"
    (( RIDE_UP )) || return 0
    RIDE_UP=0
    ride_front
    ride_end
}
check_sensors() { # stop before shooting if the replay sensors are not reaching the ride
    # Shown on the hero's HUD (Speed, HR, 3s Power), as the ride screen does not dump: the label
    # row must match the hero's, which it does not when a column is dropped (RMSE 0.035 or less
    # on a match, 0.40 or more with HR dropped), and each value must be digit-tall, not a
    # "Searching…" line (57 px against 25 px; K3, 2026-09-30).
    local i c h d ok
    set_hud scripts/fixtures/hud/hud_sparkline.json
    goto_map_page
    magick docs/screenshots/hud_sparkline.jpg -crop 480x32+0+72 +repage "$STAGE/labels_hero.png"
    for i in $(seq 1 12); do
        cap sensors
        magick "$STAGE/sensors.png" -crop 480x32+0+72 +repage "$STAGE/labels_live.png"
        d=$(magick compare -metric RMSE "$STAGE/labels_live.png" "$STAGE/labels_hero.png" null: 2>&1 \
            | sed 's/.*(\(.*\))/\1/' || true)
        ok=0
        if awk -v d="$d" 'BEGIN{exit !(d < 0.15)}'; then
            ok=1
            for c in 0 1 2; do
                h=$(magick "$STAGE/sensors.png" -crop 160x100+$((c*160))+105 +repage \
                    -colorspace gray -threshold 40% -trim -format "%h" info: 2>/dev/null || echo 0)
                (( h >= 45 )) || ok=0
            done
        fi
        (( ok )) && return 0
        settle 5
    done
    echo "  ! HUD shows no live HR or Power: are the replay sensors paired to the profile?" >&2
    return 1
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

shot_hud_sparkline() { # page 1: map + 3-col HUD + elevation profile strip (README/gallery hero)
    echo "hud_sparkline: map page, HUD 3-col Speed/HR/Power, sparkline on"
    session_start
    set_hud scripts/fixtures/hud/hud_sparkline.json
    # Minute 31 of the replay: a hairpin on the climb, so the band and the profile both show it.
    replay_seek 0.236; settle 20
    goto_map_page; settle_drawer
    cap hud_sparkline
    magick "$STAGE/hud_sparkline.png" -quality 92 "$OUTDIR/hud_sparkline.jpg"
    echo "  -> $OUTDIR/hud_sparkline.jpg"
}

shot_palettes() { # page 1: the hud_sparkline layout under two non-house palette pairs
    echo "palettes: hud_sparkline layout on the switchbacks, Turbo + Wahoo (Text), Garmin + Intervals.icu (Fill)"
    session_start
    local pair hud zone name
    for pair in "hud_sparkline turbo_wahoo" "hud_sparkline_fill garmin_intervals"; do
        read -r hud zone <<<"$pair"
        name="palette_$zone"
        set_hud "scripts/fixtures/hud/$hud.json"
        set_zone "scripts/fixtures/zone/$zone.json"
        # Switchbacks on the climb, so the band fills the map ahead. Repeated per capture so
        # both land on the same spot.
        replay_seek 0.20; settle 20
        goto_map_page; settle_drawer
        cap "$name"
        magick "$STAGE/$name.png" -quality 92 "$OUTDIR/$name.jpg"
        echo "  -> $OUTDIR/$name.jpg"
    done
    set_zone scripts/fixtures/zone/barberfish.json
}

shot_climbs_counter() { # page 1: HUD in CLIMBS mode showing the climb counter, on a climb
    echo "climbs_counter: map page, HUD climb counter"
    session_start
    set_hud scripts/fixtures/hud/climbs_counter.json
    # Must park on one of Tranquilo's categorized climbs so the native climber engages and the
    # HUD counter reads "Climb N/M"; tune this fraction against the reference. On an uncategorized
    # pitch the counter does not show.
    replay_seek 0.324
    press_button drawer_action  # collapse the native climber panel to its closed (down-chevron)
                                # state; drawer_action cycles closed/half/full, so may need tuning
    goto_page 1; settle_drawer
    cap climbs_counter
    magick "$STAGE/climbs_counter.png" -quality 92 "$OUTDIR/climbs_counter.jpg"
    echo "  -> $OUTDIR/climbs_counter.jpg"
}

shot_climbs_profile() { # page 1: HUD 3-col Speed/HR/Grade + profile strip, on a climb
    echo "climbs_profile: map page, HUD grade + profile"
    session_start
    set_hud scripts/fixtures/hud/climbs_profile.json
    # Park on a categorized climb so GRADE reads a settled positive value (it shows "Searching…"
    # on flats/descents); tune against the reference.
    replay_seek 0.324
    press_button drawer_action  # collapse the native climber panel (see shot_climbs_counter)
    goto_page 1; settle_drawer
    cap climbs_profile
    magick "$STAGE/climbs_profile.png" -quality 92 "$OUTDIR/climbs_profile.jpg"
    echo "  -> $OUTDIR/climbs_profile.jpg"
}

shot_barberfish_fields() { # page 2: full data page (HUD row + profile + fields) — README hero
    echo "barberfish_fields: data page 2"
    session_start
    set_hud scripts/fixtures/hud/barberfish_fields.json
    config_get time "$STAGE/time_saved.json"
    config_set time scripts/fixtures/time_racing.json
    pin_grade scripts/fixtures/grade_pin_descent.json
    # Scrub to the long descent after minute 45 so the profile ahead matches the pinned
    # grade, and ride it for a few minutes so the elapsed time is not seconds.
    replay_seek 0.343; settle 240
    # Pause the ride for a minute so ride time falls behind elapsed time and the two
    # average speeds separate; with no stop they read the same to one decimal. Pausing
    # the replay app instead does not pause the ride (paused time stayed at 6 s, K3
    # 2026-09-22).
    press_button bottom_right; settle 60; press_button bottom_right; settle 3
    goto_fields_page; settle_drawer
    cap barberfish_fields
    unpin_grade
    [[ -f "$STAGE/time_saved.json" ]] && config_set time "$STAGE/time_saved.json"
    magick "$STAGE/barberfish_fields.png" -quality 92 "$OUTDIR/barberfish_fields.jpg"
    echo "  -> $OUTDIR/barberfish_fields.jpg"
}

shot_light_mode() { # page 3 in day mode: 4-col zone HUD + fill-mode field pairs
    echo "light_mode: data page 3, day mode"
    session_start
    set_hud scripts/fixtures/hud/light_mode.json
    save_theme; set_day
    goto_page 3; settle_drawer
    cap light_mode
    magick "$STAGE/light_mode.png" -quality 92 "$OUTDIR/light_mode.jpg"
    echo "  -> $OUTDIR/light_mode.jpg"
    restore_theme
}

shot_karoo_vs_barberfish() { # page 4: native vs Barberfish paired single fields (no HUD row)
    echo "karoo_vs_barberfish: data page 4"
    session_start
    goto_page 4; settle_drawer
    cap karoo_vs_barberfish
    magick "$STAGE/karoo_vs_barberfish.png" -quality 92 "$OUTDIR/karoo_vs_barberfish.jpg"
    echo "  -> $OUTDIR/karoo_vs_barberfish.jpg"
}

shot_grade_map() { # page 1: the same map view with the grade map on, then off (grade map page hero pair)
    echo "grade_map: map page, grade map on then off"
    session_start
    set_hud scripts/fixtures/hud/hud_sparkline.json
    # Park around the 10 km mark, where the route climbs; tune against the reference. Assumes the
    # map's extension effects are on at entry (the puzzle toggle is a flip, its state cannot be read).
    replay_seek 0.246
    goto_page 1; settle_drawer
    cap grade_map_on
    magick "$STAGE/grade_map_on.png" -quality 92 "$OUTDIR/grade_map_on.jpg"
    echo "  -> $OUTDIR/grade_map_on.jpg"
    map_extensions_toggle; settle 3
    cap grade_map_off
    magick "$STAGE/grade_map_off.png" -quality 92 "$OUTDIR/grade_map_off.jpg"
    echo "  -> $OUTDIR/grade_map_off.jpg"
    map_extensions_toggle; settle 3    # leave the map as found
}

# The replay's HR sensor, stepped through its tap cycle (streaming, searching, missing) on the
# replay screen until its readout shows <state>: "···" searching, "--" missing, a number streaming.
# Needs the forked replay with "Separate sensors" on and its four devices paired to the profile;
# with one combined device the readouts do not respond to taps.
replay_hr() { # replay_hr streaming|searching|missing
    local i now
    replay_open
    for i in 1 2 3; do
        dump
        if ui has text "--" >/dev/null 2>&1; then now=missing
        elif ui has text "···" >/dev/null 2>&1; then now=searching
        else now=streaming; fi
        [[ "$now" == "$1" ]] && return 0
        tap text "HR"; settle 1
    done
    echo "  ! replay HR never reached $1 (Separate sensors off?)" >&2; return 1
}
to_ride() { dump; if ui has text "To ride" >/dev/null 2>&1; then tap text "To ride"; settle 3; fi; ride_front; }

shot_hud_hr_missing() { # page 1: the hero's HUD while the Karoo searches for HR, then without it
    echo "hud_hr_missing: map page, HR searching in its own column, then the 2-column HUD"
    session_start
    set_hud scripts/fixtures/hud/hud_sparkline.json
    replay_seek 0.236; settle 20
    # A sensor that goes missing is searched for first, keeping its column; the Karoo gives up
    # after a while, reports it not available, and the column drops. It retries a few minutes
    # later and the column returns as searching (K3, 2026-09-25).
    replay_hr missing; to_ride
    goto_map_page; settle_drawer
    cap hud_hr_searching
    magick "$STAGE/hud_hr_searching.png" -quality 92 "$OUTDIR/hud_hr_searching.jpg"
    echo "  -> $OUTDIR/hud_hr_searching.jpg"
    # Watch the HUD label row, which holds still while values tick and moves only with the
    # columns. No seek back to the hero's spot: seeking pauses the replay, and the live fields
    # read No data for a while after.
    local band="480x32+0+72" t d
    magick "$STAGE/hud_hr_searching.png" -crop "$band" +repage "$STAGE/labels_ref.png"
    for (( t=0; t<600; t+=5 )); do
        settle 5
        A exec-out screencap -p > "$STAGE/labels_probe.png"
        magick "$STAGE/labels_probe.png" -crop "$band" +repage "$STAGE/labels_now.png"
        d=$(magick compare -metric RMSE "$STAGE/labels_now.png" "$STAGE/labels_ref.png" null: 2>&1 \
            | sed 's/.*(\(.*\))/\1/' || true)
        if awk -v d="$d" 'BEGIN{exit !(d > 0.05)}'; then break; fi
    done
    echo "  ... label row moved after about ${t}s of searching"
    settle_drawer
    cap hud_hr_hidden
    magick "$STAGE/hud_hr_hidden.png" -quality 92 "$OUTDIR/hud_hr_hidden.jpg"
    echo "  -> $OUTDIR/hud_hr_hidden.jpg"
    replay_hr streaming; to_ride
}

# ============================= main ==========================================
ALL=(design_karoo hud_sparkline palettes climbs_counter climbs_profile barberfish_fields light_mode karoo_vs_barberfish grade_map hud_hr_missing)
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
echo "done."
