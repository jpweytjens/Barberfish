# Barberfish

<img src="app/src/main/res/drawable/ic_extension.png" align="left" width="120" alt="Barberfish">

[Barberfishes](https://en.wikipedia.org/wiki/Johnrandallia) keeps Hammerheads sharp, in [the ocean](https://www.instagram.com/reels/DEGADWAPPEy/) and on your bike.
Native-feeling data field enhancements for the [Hammerhead Karoo](https://www.hammerhead.io/).

<br clear="left">

Barberfish replaces a core set of Karoo data fields with reimplementations that match the native look and feel and add features the built-in fields don't offer. A configurable 3- or 4-column HUD groups any combination of speed, heart rate, power, cadence, grade, and other fields side by side with per-slot zone coloring. When a route is loaded, an optional color-coded elevation sparkline below the HUD shows the upcoming terrain. All field settings are configured in the Barberfish app on your Karoo with live-updating previews — changes take effect immediately without restarting your ride.

<p align="center"><img src="docs/hud_sparkline.jpg" width="600" alt="HUD with elevation sparkline"></p>

## Enhancements

### Algorithms

Where Karoo's built-in smoothing and ETA methods are unknown, Barberfish uses explicit, documented algorithms. Grade is smoothed with [ordinary least squares](https://en.wikipedia.org/wiki/Ordinary_least_squares) over a 30 m distance window — consistent regardless of speed, no smearing when stopped. ETA blends a 5-minute fast and 1-hour slow [DEWMA](https://github.com/jpweytjens/godot) component with a configurable speed prior — experimental, see [Godot](https://github.com/jpweytjens/godot) for the ongoing forward-looking replacement.

### Zone & grade coloring

Every Barberfish field uses one of three color modes — Text, Fill, or None — configurable per field. Zone palettes: Karoo, Wahoo, Zwift, Intervals.icu, and HSLuv. Grade palettes: Karoo, Wahoo, Garmin, Zwift, HSLuv, and Turbo. Brand-color palettes ship contrast-tuned for Text mode; Fill mode auto-picks the overlay text color per cell. HSLuv and Turbo work in both modes without correction. See [docs/color-palettes.md](docs/color-palettes.md) for the contrast methodology.

### Formatting

Time formatting is unambiguous across all durations — three formats to pick from:

| Format   | Under an hour | Over an hour |
| -------- | ------------- | ------------ |
| Racing   | `23'45"`      | `1h23'45"`   |
| Clock    | `0:23:45`     | `1:23:45`    |
| Segments | `23m45s`      | `1h23m45s`   |

Average speed comes in two flavors: including paused time (e.g. for [ACP randonneuring](https://www.audax-club-parisien.com/en/welcomepage/) checkpoint speeds) and excluding paused time. Speed, average speed, and cadence support threshold coloring: speed compares against a fixed target or its running average; average speed and cadence add a min/max range with warning bands.

### Layout

A 3- or 4-column HUD groups any combination of fields side by side with per-slot zone coloring. When a route is loaded, an optional [Tufte](https://www.edwardtufte.com/notebook/sparkline-theory-and-practice-edward-tufte/)-inspired elevation sparkline below the HUD shows recent terrain and the upcoming profile with non-linear zoom around your current position — tap to cycle 5/10/20 km lookahead.

## Color palettes

Each palette below is shown in both rendering modes. The top row is **Text mode** — palette color drawn as text on the dark Karoo background, using the contrast-tuned variant where one exists. The bottom row is **Fill mode** — palette color as cell fill with the auto-picked overlay text color.

### Zone palettes

| Palette       | Power preview                             | HR preview                             |
| ------------- | ----------------------------------------- | -------------------------------------- |
| Karoo         | ![](docs/img/palette-power-karoo.svg)     | ![](docs/img/palette-hr-karoo.svg)     |
| Wahoo         | ![](docs/img/palette-power-wahoo.svg)     | ![](docs/img/palette-hr-wahoo.svg)     |
| Zwift         | ![](docs/img/palette-power-zwift.svg)     | ![](docs/img/palette-hr-zwift.svg)     |
| Intervals.icu | ![](docs/img/palette-power-intervals.svg) | ![](docs/img/palette-hr-intervals.svg) |
| HSLuv         | ![](docs/img/palette-power-hsluv.svg)     | ![](docs/img/palette-hr-hsluv.svg)     |

Full RGB references live in [docs/color-palettes.md](docs/color-palettes.md).

### Grade palettes

| Palette | Preview                                |
| ------- | -------------------------------------- |
| Karoo   | ![](docs/img/palette-grade-karoo.svg)  |
| Wahoo   | ![](docs/img/palette-grade-wahoo.svg)  |
| Garmin  | ![](docs/img/palette-grade-garmin.svg) |
| Zwift   | ![](docs/img/palette-grade-zwift.svg)  |
| HSLuv   | ![](docs/img/palette-grade-hsluv.svg)  |
| Turbo   | ![](docs/img/palette-grade-turbo.svg)  |

## Data fields

Complete list of data fields provided by Barberfish, grouped by category. Order matches `extension_info.xml`.

| Field                | Smoothing                                | Zone color / palette | Threshold                          | Format options   |
| -------------------- | ---------------------------------------- | -------------------- | ---------------------------------- | ---------------- |
| **HUD**              |                                          |                      |                                    |                  |
| HUD                  |                                          | per-slot             | per-slot                           |                  |
| **Power**            |                                          |                      |                                    |                  |
| Power                | Instant / 3s / 5s / 10s / 30s / 20m / 1h | ✓                    |                                    |                  |
| Avg Power            |                                          | ✓                    |                                    |                  |
| Lap Avg Power        |                                          | ✓                    |                                    |                  |
| Last Lap Avg Power   |                                          | ✓                    |                                    |                  |
| NP                   |                                          | ✓                    |                                    |                  |
| Power Zone           |                                          | ✓                    |                                    | int/float toggle |
| Max Power            |                                          | ✓                    |                                    |                  |
| **Heart Rate**       |                                          |                      |                                    |                  |
| HR                   |                                          | ✓                    |                                    |                  |
| Avg HR               |                                          | ✓                    |                                    |                  |
| Lap Avg HR           |                                          | ✓                    |                                    |                  |
| Last Lap Avg HR      |                                          | ✓                    |                                    |                  |
| %Max HR              |                                          | ✓                    |                                    |                  |
| Max HR               |                                          | ✓                    |                                    |                  |
| HR Zone              |                                          | ✓                    |                                    | int/float toggle |
| **Speed**            |                                          |                      |                                    |                  |
| Speed                | Instant / 3s / 5s / 10s                  |                      | ✓ (Fixed / Avg total / Avg moving) |                  |
| Avg Speed (Total)    |                                          |                      | ✓ (Fixed / Min-max range)          |                  |
| Avg Speed (Moving)   |                                          |                      | ✓ (Fixed / Min-max range)          |                  |
| **Cadence**          |                                          |                      |                                    |                  |
| Cadence              | Instant / 3s / 5s / 10s                  |                      | ✓ (Fixed / Min-max range)          |                  |
| **Climbing**         |                                          |                      |                                    |                  |
| Grade                | OLS (30 m window)                        | ✓ (grade palette)    |                                    |                  |
| Elevation sparkline  |                                          | ✓ (grade palette)    |                                    |                  |
| **Time**             |                                          |                      |                                    |                  |
| Elapsed              |                                          |                      |                                    | ✓                |
| Moving               |                                          |                      |                                    | ✓                |
| Paused               |                                          |                      |                                    | ✓                |
| Lap                  |                                          |                      |                                    | ✓                |
| Last Lap             |                                          |                      |                                    | ✓                |
| **Navigation & ETA** |                                          |                      |                                    |                  |
| Time to destination  |                                          |                      |                                    | ✓                |
| Remaining ride time  |                                          |                      |                                    | ✓                |
| Time of arrival      |                                          |                      |                                    |                  |
| **Daylight**         |                                          |                      |                                    |                  |
| Time to sunrise      |                                          |                      |                                    | ✓                |
| Time to sunset       |                                          |                      |                                    | ✓                |
| Time to civil dawn   |                                          |                      |                                    | ✓                |
| Time to civil dusk   |                                          |                      |                                    | ✓                |

## Compatibility

| Device  | Oldest tested firmware |
| ------- | ---------------------- |
| Karoo 3 | 1.618.2377.20          |
| Karoo 2 | 1.613.2351.12          |

Barberfish is expected to keep working on newer Karoo firmware unless Hammerhead introduces breaking changes to the extension SDK.

## Installation

1. Find the APK link on the [latest release page](https://github.com/jpweytjens/barberfish/releases/latest).
2. Sideload the APK
   * Karoo 3: via the Karoo app following [Hammerhead's sideloading instructions](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Karoo-Extension-Sideloading).
   * Karoo 2: via your computer following [DC Rainmaker's instructions](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html)

## Roadmap

- Gradient-aware forward-looking ETA — see [Godot](https://github.com/jpweytjens/godot)
- Workout target field — continuous deviation from the target (power, HR, pace) with zone coloring reflecting how far off target you are

## Credits

- [karoo-ext](https://github.com/hammerheadnav/karoo-ext) — the official Hammerhead SDK for building Karoo extensions
- [awesome-karoo](https://github.com/timklge/awesome-karoo) — a curated list of Karoo extensions and resources
- [Hammerhead Visual Data Field System](https://www.figma.com/design/Adr23SlulPNE2RBu1VI28C/%3CH%3E-Visual-Data-Field-System?node-id=1-64&p=f) — the Figma design guide used to match the native Karoo look and feel

## Contributing

Bug reports and pull requests are welcome on [GitHub](https://github.com/jpweytjens/barberfish). Suggestions for new HUD data fields are especially welcomed.

### For extension developers

`BarberfishView` and `BarberfishDataType` are a reimplementation of the native Karoo data field that matches the Hammerhead look and feel, with added support for variable font sizes and control over the fill color behind the label and icon.

See [docs/architecture.md](docs/architecture.md) for the component hierarchy, naming conventions, and the rationale behind using `AndroidRemoteViews` for the label and value rendering. See [docs/sdk-findings.md](docs/sdk-findings.md) for empirically discovered SDK behavior.

## License

Released under the [Apache License 2.0](LICENSE).
