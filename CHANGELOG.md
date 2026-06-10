# Changelog

## 4.0

New data fields:
- Distance, the ride odometer
- Distance Remaining to the destination
- Ascent Remaining, the climbing left to the destination
- Descent Remaining to the destination
- Ride Remaining, the distance and climbing left stacked in one field
- Overview, a plain elevation profile of the whole route with a dot for where you are
- Distance and the remaining fields also selectable as HUD slots
- The standalone elevation sparkline is now called Profile

Navigation fields take their names and icons from the Karoo, so they sit naturally beside the native ones: a finish flag for Distance Remaining, up and down arrows for Ascent and Descent Remaining, the route line for Distance.

- Barberfish fields now match Karoo's Data Icons and Label Size settings. Karoo doesn't share those choices with extensions, so mirror them once in the new Data Field Design config section.

Fixes:
- Grade keeps its fill while holding a stale value in fill mode, instead of dropping to grey text
- The held-grey grade reading is now readable in light mode, not only dark

## 3.3.1

Fixes:
- The HUD and grade no longer freeze on a slow climb, and one stuck field can no longer freeze the rest of the HUD. Thanks to NevBailey for the report.
- Grade and other decimal values are no longer cut off on devices set to Spanish, German, and similar languages; they now show a dot, like the native fields. Thanks to Sasker for the report.

## 3.3

New data fields:
- Max Power
- Power Zone, integer or with one decimal
- Max HR
- % Max HR
- HR Zone, integer or with one decimal. Thanks to Holdthedoor440 for the request.
- All five also selectable as HUD slots

Grade:
- Steadier reading, follows the road instead of lagging or jittering
- Shows "Searching…" while it warms up, then holds the last value in grey across stops, instead of going blank
- No more drift after pauses
- Grade bands aligned with Karoo build 1.634.2440

Coloring:
- New light-mode color palettes for zone and grade fields, tuned for daylight readability
- Palettes switch automatically with the system theme on both ride pages and config previews
- Value text in fill mode picks black or white per zone, whichever reads better. Thanks to Sasker for the suggestion.
- Speed can be colored against a fixed target or its own running average, like Avg Speed. Thanks to max-t-d for the request.
- Threshold coloring on Speed, Avg Speed, and Cadence respects text vs fill mode like zoned fields do

Sparkline:
- New Climbs mode for the HUD sparkline, a third setting next to Off and On: it stays hidden on flat ground and appears only as you come up on a climb, framed from the foot to the top
- Harder climbs reveal sooner, so a long ascent gives more notice than a short ramp
- Works while navigating a route, since that's where the climbs come from

Config screen:
- All configurable fields gathered into one Data fields section; threshold controls live with the fields they belong to
- Climbing and Sparkline use the same expand-and-collapse layout as the other fields
- Numeric inputs commit when you tap away, not only when you press Done
- Brand colors on active controls, selection borders, and the sparkline current-position dot
- Smaller touch-ups: card animation plays again, HUD preview width matches the sparkline, grade upper-bound shows ≥ instead of +

Fixes:
- Cadence shows "Searching…" while waiting for a sensor, instead of "Not available"
- Sparkline no longer stalls when settings load slowly

## 3.2

Sparkline:
- Smoother rendering near the current position dot
- Default lookahead lowered to 5 km
- Detected uphill climbs are tinted blue along the outline (Climber-style highlight)
- Route POIs now appear as outline markers; past POIs fade to grey
- Symmetric flat-band skipping on descents for cleaner gradient bands near zero grade
- New config toggles: show climbs, show POIs, and an independent descent-skip strength

Palettes:
- New Turbo grade palette, with full negative-grade coloring across 10 distinct descent shades

General:
- Fix value text clipping when the rerouting navigation toast appears mid-ride with key icons off. Thanks to LucasHuber for the report. 
- Improved data field rendering: values are now bitmap-rendered for pixel-accurate baseline alignment with native Karoo fields, on both Karoo 2 and Karoo 3
- More Karoo-like streaming state: header icon stays visible during "Searching…", stream-state text is sized to match, and is correctly centered.
- "No Laps Yet" placeholder on last-lap fields during the first lap

Config UI:
- Renamed the sparkline emphasis section and corrected its readout
- HUD preview uses on-device font sizing so it more closely matches the rendered field
- Grade band bar shows zone boundary labels for clearer reading


## 3.1 — Sparkline update

Accuracy:

The sparkline looked right in most cases but could drift or flatten in a few real-world ones. This release fixes those.

- Position now tracks distance-to-destination instead of distance-ridden, so the "you are here" marker stays correct after reroutes and restarts
- Lowered the default minimum elevation range to 50 m so small rollers actually look like rollers instead of a flat line (now configurable via Y-zoom)

Sparkline config:

Make the sparkline more configurable

- Profile detail  
  Control how much similar gradient segments are combined into one for a better a glance view
- X-axis warp  
  control how much the view stretches toward what's immediately ahead versus showing the full lookahead evenly
- Y-zoom  
  Close amplifies minor bumps, Wide smooths them out

Sparkline rendering:

- Smoother edges: segments near the window boundaries are now partially drawn instead of popping in, and the outline connects cleanly through the current position dot

Standalone sparkline field:

- New standalone elevation sparkline data field available outside the HUD

Visual:

- Desaturated past-section gradient fills in light mode
- Enlarged current-position dot for easier tracking

Config UI:

- Reorganized data fields: Grade moved to a "Climbing" subsection, NP moved to end of power fields
- Renamed "Climber" section to "Climbing"
- Clearer sparkline labels: Off / Mild / Medium / Max
- Default color palettes now use the original Karoo colors instead of the readability-corrected variants
- Clarified that the "Gradient colors" palette drives both the Grade data field and the sparkline gradient overlay

## 3.0

Elevation sparkline:

A tiny mountain profile now lives below your HUD.  

Only when a route is loaded, you see the past on the left, current gradient in the middle, upcoming elevation on the right. Tap to look 5, 10, or 20 km ahead. 

New data fields:

The field list was starting to feel like it only cared about your current moment. Now it also cares about your recent and average moments.

- (Last) Lap Power
- (Last) Lap Time
- (Last) Lap HR
- Average HR
- Cadence threshold coloring — set a target RPM or min/max zone, same system as average speed

ETA:

Three new ETA data fields powered by DEWMA.
- Remaining ride time (excluding paused time)
- Time to destination (including paused time)
- Clock-on-the-wall arrival time.

Elapsed/paused time fix:

Somebody at Karoo named the moving-time field `ELAPSED_TIME` and the total-time field `RIDE_TIME` and I got confused.

- Fixed elapsed time showing no values
- Fixed paused time showing large values

Zone colors:

Tweaked color palettes for increased readability in dark mode.

- APCA contrast tuning and HSLuv lightness correction per palette
- New HSLuv palette designed for readability in dark mode
- Zwift grade palette for indoor climb sessions

Compatibility:

- Light mode support
- Karoo 2 support

Config screen:

 - Config UI has more organisation and new UI to follow Karoo UI more closely.


## 2.0
- Configurable HUD slots  
each column independently selectable from Speed, HR, Power, Cadence, Avg Power, Normalized Power, or Grade; per-slot zone color mode
- Four-column HUD  
optional 4th column for an extra metric at a glance
- New fields: Cadence (instant, 3 s, 5 s, 10 s), Average Power, Normalized Power, Grade (EWMA smoothing, α=0.15, ~6 s time constant)
- Grade coloring  
gradient palette with Wahoo, Garmin, and Hammerhead styles
- Rendering overhaul  
Glance removed; pure RemoteViews with IBM Plex Sans Condensed headers, native font-size lookup, and correct value centering

## 1.0
Initial release with 15 data fields for power, heart rate, speed, time, navigation, and daylight — all with zone coloring and native Karoo styling.
