# Changelog

## 3.0

### Elevation sparkline

A tiny mountain profile now lives below your HUD. Past on the left, current suffering in the middle, upcoming elevation on the right. Tap to look 5, 10, or 20 km ahead. When a route is loaded, the field picker preview shows your actual route.

### New fields

(Last) Lap Power, (Last) Lap Time, (Last) Lap HR, Average HR. The field list was starting to feel like it only cared about your current moment. Now it also cares about your recent and average moments.

### ETA

Three new fields: remaining ride time, time to destination, and clock-on-the-wall arrival time. Powered by [DEWMA](https://github.com/jpweytjens/godot), because "assume you'll hold this exact watt for the next 80 km" was optimistic at best.

### The elapsed/riding time thing

Elapsed time and riding time were swapped. Somebody named the moving-time field `ELAPSED_TIME` and the total-time field `RIDE_TIME` and I got confused.

### Zone colors

Zone colors are now legible on the Karoo screen. Previously they were colorful. There is a difference. Each palette is tuned using [APCA](https://apcacontrast.com/) contrast checking and [HSLuv](https://www.hsluv.org/) lightness correction. A new HSLuv palette was designed from scratch to not need correction in the first place. Zwift grade palette added for your indoor climb sessions.

### Config screen

Tap-to-expand cards, section icons, grouped dropdowns, live previews. It now matches the Karoo UI even better.

### Under the hood

Unified rendering pipeline, glyph-measured font sizing, shared base types. The kind of work that should make the next features take at least half as long.

### Karoo 2 support

Barberfish now runs on the Karoo 2. The old dog gets the new tricks.

### Imperial units

Data fields and the config UI now respect your Karoo's unit preference. Barberfish speaks both imperial and metric.

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
