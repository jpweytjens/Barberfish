# Shared Karoo adb helpers, sourced by the scripts in this directory (`source scripts/_karoo.sh`
# after `cd` to the repo root). They drive one connected Karoo: keep it awake, survive the USB
# drop when it dozes, tap by element text through uiautomator, start and end rides, and switch
# the theme.
#
# The caller sets, before calling the helpers that need them:
#   STAGE   a scratch directory, for dump, tap and ride_end (dumps and probe screencaps land here)
#   W, H    the screen size in px (K3: 480 x 800), or calls screen_size to read it
# UI defaults to $STAGE/ui.xml. Fixed tap coordinates below are K3 positions.
UI="${UI:-${STAGE:-}/ui.xml}"

screen_size() { # set W and H from the connected device, so swipes work on K2 (landscape) and K3
    local size
    size=$(A shell wm size | awk -F'[ x]' '/Physical size/ {print $(NF-1), $NF}')
    read -r W H <<<"$size"
    [[ -n "${W:-}" && -n "${H:-}" ]] || { echo "  ! could not read the screen size" >&2; return 1; }
}

# The ride pager's next page (finger moves left). Forward is the only reliable direction:
# backward swipes drop on dense pages.
swipe_next() { A shell input swipe $((W * 8 / 10)) $((H / 2)) $((W * 2 / 10)) $((H / 2)) 200; }

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
    # Clear the last dump first: on a screen that never idles (an animated preview) the dump fails,
    # and the stale file would answer for a screen no longer shown.
    rm -f "$UI"; A shell rm -f /sdcard/ui.xml >/dev/null 2>&1
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

# ---- rides -------------------------------------------------------------------
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
