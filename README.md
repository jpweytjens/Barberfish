# Barberfish

[![Release](https://img.shields.io/github/v/release/jpweytjens/barberfish)](https://github.com/jpweytjens/barberfish/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/jpweytjens/barberfish/ci.yml?branch=master)](https://github.com/jpweytjens/barberfish/actions/workflows/ci.yml)
[![Downloads](https://img.shields.io/github/downloads/jpweytjens/barberfish/barberfish.apk)](https://github.com/jpweytjens/barberfish/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

<img src="app/src/main/res/drawable/ic_extension.png" align="left" width="120" alt="Barberfish">

[Barberfishes](https://en.wikipedia.org/wiki/Johnrandallia) keep Hammerheads sharp, in [the ocean](https://www.instagram.com/reels/DEGADWAPPEy/) and on your bike.
Native-feeling data field enhancements for the [Hammerhead Karoo](https://www.hammerhead.io/).

Barberfish is a collection of data fields for the Hammerhead Karoo. They sit alongside the native ones, match their look, and quietly add a bit more: a 3- or 4-column HUD, an elevation profile drawn as a [Tufte](https://www.edwardtufte.com/notebook/sparkline-theory-and-practice-edward-tufte/)-inspired sparkline, and configurable smoothing, color modes, color palettes, and thresholds per field. Everything is set up in the Barberfish app on your Karoo with live previews; changes apply mid-ride.

<table>
  <tr>
    <td align="center">Elevation profile below a 3-column HUD on the map view</td>
    <td align="center">Karoo native fields beside their Barberfish counterparts</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/hud_sparkline.jpg" alt="3-column HUD with elevation profile over the map view"></td>
    <td align="center"><img src="docs/karoo_vs_barberfish.jpg" alt="Karoo native fields next to Barberfish equivalents on a 5-row data page"></td>
  </tr>
</table>

## Enhancements

### Algorithms

Most GPS bike computer manufacturers, Hammerhead included, don't publish the algorithms behind their built-in smoothing and ETA fields. Barberfish uses explicit, documented ones so the field's behaviour is something you can predict.

Grade is smoothed over distance rather than time, fitting an [ordinary least squares](https://en.wikipedia.org/wiki/Ordinary_least_squares) line through the last 30 m of elevation. A fixed-window time average has to pick between jittering with every cadence stroke (short window) and smearing the start and end of a climb (long window). The OLS-over-distance variant sidesteps the trade by following the road instead of the clock: it holds steady at any speed and stops moving when you do. At the start of a ride, before it has 30 m of road to fit, it shows "Searching…"; when you stop, it holds the last reading in grey rather than going blank.

ETA blends a 5-minute fast and 1-hour slow [DEWMA](https://github.com/jpweytjens/godot) of recent speed with a configurable prior, so the estimate sharpens as the ride goes on rather than starting from a generic guess. It is not yet gradient-aware, so the climb you can see coming will still pull the arrival time inward. The forward-looking replacement lives in [Godot](https://github.com/jpweytjens/godot).

### Zone & grade coloring

Most fields have a color mode that controls how the value sits on the background. None is the default and applies no zone coloring. Text colors the value with the zone color. Fill paints the background with the zone color and picks black or white for the value so it stays readable on top.

The Karoo background is white in light mode and black in dark mode. Text mode puts the colored value straight on that background, and palettes designed for one theme can read poorly on the other. Barberfish contrast-tunes each brand palette into a light and a dark variant so the colors stay legible against either background.

The tuning uses [APCA](https://apcacontrast.com/): any color below the threshold for legible large text has its [HSLuv](https://www.hsluv.org/) lightness shifted until it passes, hue and saturation kept intact. Fill mode needs no tuning: the value drawn on top adapts to whichever zone color fills the background. The HSLuv palette is built around perceptually uniform lightness from the start and reads the same on both themes without correction. See [docs/color-palettes.md](docs/color-palettes.md) for the full derivation.

### Layout

A 3- or 4-column HUD groups any combination of fields side by side with per-slot zone coloring. When a route is loaded, an optional elevation profile, drawn as a [Tufte](https://www.edwardtufte.com/notebook/sparkline-theory-and-practice-edward-tufte/)-inspired sparkline, sits below the HUD in one of two modes. On shows the whole route's upcoming terrain with non-linear zoom around your current position; tap to cycle 5/10/20 km lookahead. Climbs is a Barberfish take on Hammerhead's Climber: it stays hidden until a climb nears, shows a `Climb 2/5` heads-up, then frames the climb foot to summit as you ride up and clears at the top, revealing earlier for harder climbs.

## Examples

<table>
  <tr>
    <td align="center">4-column HUD config with fill-mode zone coloring</td>
    <td align="center">Light mode with zone-colored HUD and field comparison</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/hud_config.jpg" alt="HUD config screen with 4-column layout and fill-mode zones"></td>
    <td align="center"><img src="docs/light_mode.jpg" alt="Light mode data page with zone-colored HUD and Karoo vs Barberfish comparison"></td>
  </tr>
  <tr>
    <td align="center">Climbs mode flags the next climb before it arrives</td>
    <td align="center">Climbs mode frames the climb foot to summit</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/climbs_counter.jpg" alt="Climbs mode heads-up showing the next climb on the route"></td>
    <td align="center"><img src="docs/climbs_profile.jpg" alt="Climbs mode profile framing a climb foot to summit with the position dot partway up"></td>
  </tr>
  <tr>
    <td align="center">Average speed with target-mode threshold, text coloring above target</td>
    <td align="center">Data field configuration grouped by category</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/threshold.jpg" alt="Avg Speed threshold config with text-mode green above-target coloring"></td>
    <td align="center"><img src="docs/config.jpg" alt="Main Barberfish config screen with HUD and Data Fields sections"></td>
  </tr>
  <tr>
    <td align="center">Grade fill colored by the gradient palette</td>
    <td align="center">Grade greys out when the estimate is not reliable</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/grade_color.jpg" alt="Grade data field in fill mode, orange cell at 13 percent"></td>
    <td align="center"><img src="docs/grade_stale.jpg" alt="Grade data field in fill mode showing the grey stale state when no reliable estimate is available"></td>
  </tr>
</table>

## Color palettes

Zone palettes from Karoo, Wahoo, Zwift, Intervals.icu, and HSLuv; grade palettes add Garmin and Turbo. Each is kept legible in light and dark mode. Every palette in both modes, and the contrast tuning behind them: [docs/color-palettes.md](docs/color-palettes.md).

![Karoo power palette in both themes and fill mode](docs/img/palette-power-karoo.svg)

## Data fields

39 fields across ten categories: power, heart rate, speed, cadence, climbing, navigation, time, ETA, daylight, and the HUD. The full list, with each field's palette, threshold, format, and smoothing options, is in [docs/data-fields.md](docs/data-fields.md).

## Compatibility

| Device  | Oldest tested firmware |
| ------- | ---------------------- |
| Karoo 3 | 1.618.2377.20          |
| Karoo 2 | 1.613.2351.12          |

Barberfish is expected to keep working on newer Karoo firmware unless Hammerhead introduces breaking changes to the extension SDK.

Light mode and dark mode are both supported, as are metric and imperial units.

Karoo's Data Field Design settings (Data Icons and Label Size) are not visible to extensions, so Barberfish cannot follow them automatically. Mirror your choices in the Data Field Design section of the Barberfish config and its fields will render like the native fields beside them.

## Installation

1. Find the APK link on the [latest release page](https://github.com/jpweytjens/barberfish/releases/latest).
2. Sideload the APK
   * Karoo 3: via the Karoo app following [Hammerhead's sideloading instructions](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Karoo-Extension-Sideloading).
   * Karoo 2: via your computer following [DC Rainmaker's instructions](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html)

## Roadmap

- Gradient-aware forward-looking ETA: see [Godot](https://github.com/jpweytjens/godot)
- Workout target field: continuous deviation from the target (power, HR, pace) with zone coloring reflecting how far off target you are

## Credits

- [karoo-ext](https://github.com/hammerheadnav/karoo-ext): the official Hammerhead SDK for building Karoo extensions
- [awesome-karoo](https://github.com/timklge/awesome-karoo): a curated list of Karoo extensions and resources
- [Hammerhead Visual Data Field System](https://www.figma.com/design/Adr23SlulPNE2RBu1VI28C/%3CH%3E-Visual-Data-Field-System?node-id=1-64&p=f): the Figma design guide used to match the native Karoo look and feel

## Contributing

Bug reports and pull requests are welcome on [GitHub](https://github.com/jpweytjens/barberfish). Suggestions for new HUD data fields are especially welcomed.

### For extension developers

`BarberfishView` and `BarberfishDataType` are a reimplementation of the native Karoo data field that matches the Hammerhead look and feel, with added support for variable font sizes and control over the fill color behind the label and icon.

See [docs/architecture.md](docs/architecture.md) for the component hierarchy, naming conventions, and the rationale behind using `AndroidRemoteViews` for the label and value rendering. See [docs/sdk-findings.md](docs/sdk-findings.md) for empirically discovered SDK behavior.

## License

Released under the [Apache License 2.0](LICENSE).
