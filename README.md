# Barberfish

<img src="app/src/main/res/drawable/ic_extension.png" align="left" width="120" alt="Barberfish">

[Barberfishes](https://en.wikipedia.org/wiki/Johnrandallia) keeps Hammerheads sharp, in [the ocean](https://www.instagram.com/reels/DEGADWAPPEy/) and on your bike.
Native-feeling data field enhancements for the [Hammerhead Karoo](https://www.hammerhead.io/).

<br clear="left">

## Description

Barberfish reimplements and enhances a core set of Karoo data fields with features the built-in fields don't offer. A configurable 3- or 4-column HUD shows any combination of speed, heart rate, power, cadence, average power, normalized power, or grade side by side with zone coloring.   

Zone coloring supports both background-fill and text-color styles across four palettes. Time fields use a consistent, unambiguous format across all durations. Average speed fields support a single target or a min/max range with threshold coloring. All fields are styled to match the native Karoo look and feel. Settings are configured through a Karoo-native config screen with live-updating field previews.

## Data field enhancements

| Feature             | Default Karoo                                                                                                                                                      | Barberfish                                                                                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| HUD                 | Not available                                                                                                                                                      | Configurable 3- or 4-column layout; each slot independently selectable from Speed, HR, Power, Cadence, Avg Power, NP, or Grade with per-slot zone coloring |
| Zone color palettes | Karoo only                                                                                                                                                         | Karoo, Wahoo, Zwift, and Intervals.icu                                                                                                                     |
| Zone coloring style | Background fill only                                                                                                                                               | Background fill or text color                                                                                                                              |
| Grade coloring      | Not available                                                                                                                                                      | Color-coded by road gradient steepness; Karoo, Wahoo, and Garmin palettes                                                                                  |
| Average speed       | Exclusive paused time only                                                                                                                                         | Both inclusive and exclusive paused time variants                                                                                                          |
| Avg speed threshold | Not available                                                                                                                                                      | Configurable single threshold or min/max range with warning bands                                                                                          |
| Time formatting     | Ambigious `hh:mm` or `mm:ss` depending on duration                                                                                                                 | Unambiguous: `1h 23m 45s`, `1h 23' 45"`, or `01:23:45`                                                                                                     |
| Duration fields     | [Built-in duration fields](https://support.hammerhead.io/hc/en-us/articles/35533240795419-Data-Fields-Legend)  including total time, riding time, paused time, ... | Reimplemented with Barberfish formatting options                                                                                                           |

### Configuration

All field settings are configured in the Barberfish app on your Karoo. Changes update live in the settings and take effect immediately without restarting your ride.

### Zone color palettes

| Palette       | Power zones (Z1 – Z7)           | Heart rate zones (Z1 – Z5)         |
| ------------- | ------------------------------- | ---------------------------------- |
| Karoo         | ![](docs/palette-karoo.svg)     | ![](docs/palette-karoo-hr.svg)     |
| Wahoo         | ![](docs/palette-wahoo.svg)     | ![](docs/palette-wahoo-hr.svg)     |
| Zwift         | ![](docs/palette-zwift.svg)     | ![](docs/palette-zwift-hr.svg)     |
| Intervals.icu | ![](docs/palette-intervals.svg) | ![](docs/palette-intervals-hr.svg) |

### Grade color palettes

| Palette    | Bands (flat → steep)                                                                                            |
| ---------- | --------------------------------------------------------------------------------------------------------------- |
| Karoo  | ![](docs/palette-grade-karoo.svg)<br><sub>0–5% · 5–8% · 8–13% · 13–16% · 16–20% · 20–24% · ≥24%</sub>    |
| Wahoo  | ![](docs/palette-grade-wahoo.svg)<br><sub>0–4% · 4–8% · 8–12% · 12–20% · ≥20%</sub>                      |
| Garmin | ![](docs/palette-grade-garmin.svg)<br><sub>0–3% · 3–6% · 6–9% · 9–12% · ≥12%</sub>                       |

### Time formatting

| Format   | Under an hour | Over an hour |
| -------- | ------------- | ------------ |
| Racing   | `23'45"`      | `1h23'45"`   |
| Clock    | `0:23:45`     | `1:23:45`    |
| Segments | `23m 45s`     | `1h 23m 45s` |

## Data fields

Complete list of data fields provided by Barberfish.

### HUD

- HUD — configurable 3 or 4 columns; each slot selectable from speed, HR, power, cadence, avg power, NP, or grade

### Power & Heart Rate

- Power (Instant, 3s, 5s, 10s, 30s, 20m, 1h smoothing)
- Avg Power
- Normalized Power
- Heart rate

### Speed

- Speed (Instant, 3s, 5s, 10s smoothing)
- Total average speed (including paused time)
- Moving-time average speed (excluding paused time)

### Cadence

- Cadence (Instant, 3s, 5s, 10s smoothing)

### Grade

- Grade

### Time

- Elapsed time
- Moving time
- Paused time

### Navigation

- Time to destination

### Daylight

- Time to sunrise
- Time to sunset
- Time to civil dawn
- Time to civil dusk

## Examples

|                                                   |                                                                                                                                                                                                                       |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| <img width="180" src="docs/hud.jpg">              | HUD using the Wahoo palette for text zone coloring and [RouteGraph](https://github.com/timklge/karoo-routegraph) in the second row.                                                                                   |
| <img width="180" src="docs/hud_fill.jpg">         | HUD with background-fill zone coloring.                                                                                                                                                                               |
| <img width="180" src="docs/comparison.jpg">       | Comparing 4 reimplemented fields: Text-color power vs background-fill power and HR. Moving average speed in red above the configurable 30 km/h threshold. Paused time in clock format showing the difference between. |
| <img width="180" src="docs/config.jpg">           | Karoo-style config screen with collapsible sections.                                                                                                                                                                  |
| <img width="180" src="docs/config_fieldcard.jpg"> | FieldCard with live preview showing the smoothing-aware label and zone color mode selector.                                                                                                                           |
| <img width="180" src="docs/config_threshold.jpg"> | Threshold configuration for average speed: single threshold or min/max range with configurable warning bands.                                                                                                         |
| <img width="180" src="docs/hud_four.jpg">         | 4-column HUD showing speed, HR, power, and cadence.                                                                                                                                                                   |
| <img width="180" src="docs/hud_configurable.jpg"> | Configurable HUD slot picker — tapping a column opens the field selector.                                                                                                                                             |
| <img width="180" src="docs/grade.jpg">            | Grade field with gradient coloring.                                                                                                                                                                                   |
|                                                   |                                                                                                                                                                                                                       |

## Use cases
### Map page HUD

The three-column HUD is designed as the top row of a map data page providing speed, heart rate, and power at a glance. 

A single row on the map page takes up a lot of vertical space. A nice workaround is adding a second row to occupy this excess vertical space with e.g. [RouteGraph](https://github.com/timklge/karoo-routegraph) for an elevation profile.

### Race with a goal pace (single threshold)

Racing an event with a target average? Set a single threshold at your goal pace on the average speed field (excluding paused time). The field colors green above it and red below, so you know at a glance whether you're on track.

### ACP randonneuring (min + max threshold)

[ACP randonneuring](https://www.audax-club-parisien.com/en/welcomepage/) events impose checkpoint cutoff speeds on your total average speed, including any paused time. The rules set a 15 km/h minimum and 30 km/h maximum. Set Min: 15 and Max: 30 on the total average speed field to keep track. The field colors green inside the zone, orange when approaching a boundary, and red when outside.


## Roadmap

Ideas that may or may not be implemented

- Day mode support
  fields are currently only tested in night mode; day mode rendering needs verification and adjustments
- ETA data field  
   estimated time of arrival at destination, gradient- and paused-time aware for more accurate predictions on hilly routes

## Compatibility

Tested on a Karoo 3 running firmware 1.618.2377.20 using metric units and dark mode. Should work on Karoo 2 and other firmware versions, but no guarantees.

## Installation

1. Find the APK link on the [latest release page](https://github.com/jpweytjens/barberfish/releases/latest).
2. Sideload the APK
   * Karoo 3: via the Karoo app following [Hammerhead's sideloading instructions](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Karoo-Extension-Sideloading).
   * Karoo 2: via your computer following [DC Rainmaker's instructions](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html)

## Credits

- [karoo-ext](https://github.com/hammerheadnav/karoo-ext) — the official Hammerhead SDK for building Karoo extensions
- [awesome-karoo](https://github.com/timklge/awesome-karoo) — a curated list of Karoo extensions and resources
- [Hammerhead Visual Data Field System](https://www.figma.com/design/Adr23SlulPNE2RBu1VI28C/%3CH%3E-Visual-Data-Field-System?node-id=1-64&p=f) — the Figma design guide used to match the native Karoo look and feel

## Contributing

Bug reports and pull requests are welcome on [GitHub](https://github.com/jpweytjens/barberfish).

### For extension developers

`BarberfishView` and `BarberfishDataType` are a reimplementation of the native Karoo data field that matches the Hammerhead look and feel, with added support for variable font sizes and control over the fill color behind the label and icon.

See [docs/architecture.md](docs/architecture.md) for the component hierarchy, naming conventions, and the rationale behind using `AndroidRemoteViews` for the label and value rendering.

## License

Released under the [Apache License 2.0](LICENSE).
