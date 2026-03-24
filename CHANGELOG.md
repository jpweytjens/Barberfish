# Changelog

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
