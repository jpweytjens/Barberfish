# Changelog

## 3.1 — Sparkline update

### Accuracy

The sparkline looked right in most cases but could drift or flatten in a few real-world ones. This release fixes those.

- Position now tracks distance-to-destination instead of distance-ridden, so the "you are here" marker stays correct after reroutes and restarts
- Elevation range is derived from the visible window only, so short, sharp climbs no longer get squashed against a full-route Y scale
- Lowered the minimum elevation range to 50 m so small rollers actually look like rollers instead of a flat line
- Hardened the polyline decoder against truncated input so a partial route string can no longer crash the field

### Config preview

- Animated preview sweeps along the profile so you can see how the current-position marker and gradient coloring behave while riding

## 3.0

### Elevation sparkline

A tiny mountain profile now lives below your HUD.  

Only when a route is loaded, you see the past on the left, current gradient in the middle, upcoming elevation on the right. Tap to look 5, 10, or 20 km ahead. 

### New data fields

The field list was starting to feel like it only cared about your current moment. Now it also cares about your recent and average moments.

- (Last) Lap Power
- (Last) Lap Time
- (Last) Lap HR
- Average HR
- Cadence threshold coloring — set a target RPM or min/max zone, same system as average speed

### ETA

Three new ETA data fields powered by [DEWMA](https://github.com/jpweytjens/godot).
- Remaining ride time (excluding paused time)
- Time to destination (including paused time)
- Clock-on-the-wall arrival time.

### Elapsed/paused time fix

Somebody at Karoo named the moving-time field `ELAPSED_TIME` and the total-time field `RIDE_TIME` and I got confused.

- Fixed elapsed time showing no values
- Fixed paused time showing large values

### Zone colors

Tweaked color palettes for increased readability in dark mode.

- [APCA](https://apcacontrast.com/) contrast tuning and [HSLuv](https://www.hsluv.org/) lightness correction per palette
- New HSLuv palette designed for readability in dark mode
- Zwift grade palette for indoor climb sessions

### Compatibility

- Light mode support
- Karoo 2 support

### Config screen

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
