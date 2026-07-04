# SDK findings

Empirically discovered behavior of the Karoo SDK and ride app, observed through
on-device testing with `karoo-ext`, ADB instrumentation, and screencap analysis.
These are not documented in the official SDK AFAIK.

---

## StreamState semantics

`OnStreamState` delivers one of four `StreamState` variants. Barberfish maps each to a `FieldState`:

| Variant        | Barberfish factory          | Display text    | Meaning                                                     |
| -------------- | --------------------------- | --------------- | ----------------------------------------------------------- |
| `Streaming`    | happy path                  | live value      | Sensor actively emitting data; `DataPoint` is valid         |
| `Searching`    | `FieldState.searching()`    | "Searching"     | Sensor is paired but offline / reconnecting                 |
| `NotAvailable` | `FieldState.notAvailable()` | "Not available" | Feature not supported on this device (permanent)            |
| `Idle`         | `FieldState.idle()`         | "No data"       | Sensor connected but silent (ride paused, movement stopped) |

All three use `FieldColor.StreamState` → rendered white in `stream_state_tv` (ibm-plex-sans-condensed).
`FieldState.unavailable()` ("—") is different. It uses `FieldColor.Error` (red) in `field_value`, meaning
the stream is `Streaming` but a specific `DataPoint.values` key is `null`.

---

## Time field semantics: ELAPSED_TIME vs RIDE_TIME

Confirmed from `DataType.kt` source in [karoo-ext on GitHub](https://github.com/hammerheadnav/karoo-ext).

| Type constant                | SDK description                                                  | Meaning                                                |
| ---------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------ |
| `DataType.Type.ELAPSED_TIME` | "Ride Time — Time spent recording this ride"                     | Moving time, excluding paused time                     |
| `DataType.Type.RIDE_TIME`    | "Total Time — Time since this ride began, including paused time" | Wall-clock time from ride start, including paused time |
| `DataType.Type.PAUSED_TIME`  | "Paused Time — Time spent paused this ride"                      | Cumulative pause duration                              |

The relationship is: `RIDE_TIME = ELAPSED_TIME + PAUSED_TIME`.

"Recording" in the ELAPSED_TIME description means the timer only advances while the ride is
actively recording (not paused). This is confirmed by the observed bug: computing
`movingSeconds = ELAPSED_TIME - PAUSED_TIME` produces a value that shrinks while paused
(ELAPSED stays constant, PAUSED grows), causing avg-speed-moving to grow, which is the wrong behavior.

Correct formulas:

| Metric                  | Formula                                                                 |
| ----------------------- | ----------------------------------------------------------------------- |
| Moving time             | `ELAPSED_TIME` directly                                                 |
| Total (wall-clock) time | `RIDE_TIME`, or `ELAPSED_TIME + PAUSED_TIME`                            |
| Avg speed (moving)      | `DISTANCE / ELAPSED_TIME`                                               |
| Avg speed (total)       | `DISTANCE / RIDE_TIME` (or native `AVERAGE_SPEED` field if it is total) |



## DataPoint field units

`DataPoint.values` delivers raw `Double` values in these base units.
Always convert before display. Never treat raw values as display-ready.

| Category  | Unit                | Conversion                                       |
| --------- | ------------------- | ------------------------------------------------ |
| Time      | milliseconds        | divide by 1000 → seconds; use `ConvertType.TIME` |
| Distance  | meters              | `ConvertType.DISTANCE` → km or mi                |
| Speed     | m/s                 | `ConvertType.SPEED` → km/h or mph                |
| Elevation | meters              | `ConvertType.ELEVATION` → m or ft                |
| Power     | watts               | no conversion needed                             |
| HR        | bpm                 | no conversion needed                             |
| Cadence   | rpm                 | no conversion needed                             |
| Grade     | percent (0.0–100.0) | no conversion needed                             |

Special case: `TIME_OF_ARRIVAL` delivers milliseconds since midnight (not epoch).
Divide by 1000, then take modulo 86400 to get seconds since midnight.

Units are always base SI regardless of the user's preferred unit setting. The extension
is responsible for converting to km/h or mph, km or mi, etc. based on
`UserProfile.preferredUnit`.

Tentative: native Karoo field previews appear to ignore the unit preference; they show
the same (metric-looking) demo values in both metric and imperial mode. Barberfish
previews do convert because `previewFlow()` reads `streamUserProfile()`. To be confirmed
with an actual ride comparing native vs Barberfish fields in imperial mode.

---

## Preview update rate floor

The ride app silently cancels preview flows that emit faster than approximately 900 ms.
No error is raised, the preview just stops updating.

Use `Delay.PREVIEW = 1000L` (defined in `shared/Delay.kt`) as the emit interval
for all `previewFlow()` implementations. Do not go below 1000 ms.

For live flows, `sampleMs = 400L` (the default in `BarberfishDataType`) is safe.
`TimeField` overrides this to `1000L` since seconds-resolution data needs no faster sampling.

---

## RemoteViews class whitelist

Extension fields are rendered cross-process via `RemoteViews.apply()` in the ride app.
The ride app's `LayoutInflater` enforces a strict class allowlist. Anything not on this
list causes `InflateException: Class not allowed` at runtime, with no compile-time warning.

Allowed containers:

- `FrameLayout`
- `LinearLayout`
- `RelativeLayout`
- `GridLayout`, `GridView`, `ListView`, `StackView`, `ViewFlipper`, `AdapterViewFlipper`

Allowed leaves:

- `TextView`, `ImageView`, `ImageButton`, `Button`
- `ProgressBar`, `Chronometer`, `TextClock`, `AnalogClock`

Not allowed (even though they compile):

- `android.view.View`: the base class, even used as a spacer
- `android.widget.Space`
- `androidx.constraintlayout.widget.ConstraintLayout`
- Any custom or third-party view class

---

## SDK container geometry

When `emitter.updateView(rv)` is called with `showHeader = false`, the ride app
gives the `RemoteViews` a container that fills the full cell bounds exactly. No
offset, no inset. Observed by inspecting `field_root`'s on-screen bounds via
`adb shell dumpsys activity top` and comparing them to the cell rectangle in
screencaps; the two match to the pixel.

Do not add padding or translation to compensate for any assumed offset; there is
none. The field container is exactly `cell_width × cell_height`.

Note: the SDK exposes a separate path (`sdkView.createView()`) for non-`RemoteViews`
SDK views; that path does not apply to extension data fields rendered via
`emitter.updateView(rv)`.

---

## Route polyline simplification

Two separate pipelines; only one applies simplification.

Map display: the route line drawn on the map looks visibly simpler at lower zoom
levels on the Route selection screen, observable by zooming in and out and
counting kinks on the polyline. This is a purely visual effect of map-tile
rendering and does not affect the underlying route data.

`routeElevationPolyline` (what the SDK exposes): a separate encoded polyline that
the SDK passes through to navigation state without simplification. Its
resolution is fixed at route creation time (server-side or GPX import) and is
unaffected by zoom level. Decode with the standard Google Encoded Polyline
algorithm at precision = 1 (verified by decoding and overlaying onto the map).

---

## Native header and value sizing

Device: Karoo 3, density = 1.875 (300 dpi / 160). Established by on-device
screencap sweeps (`scripts/walk_layouts.sh` + `scripts/measure_alignment.py`)
across both Label Size settings, plus behavioral analysis of the rideapp
(notes local-only in `docs/native-header-internals.md`).

The rideapp sizes each data cell per `(colSpan, rowSpan)` in the 60-unit grid
and per the rider's Label Size setting (Small or Large). All sizes below are
raw px on the 1.875-density screen; translations are raw px too.

Karoo 3, first matching row wins:

| condition                       | example     | value px | valueTransY | label px | line spacing | labelTransY |
| ------------------------------- | ----------- | -------- | ----------- | -------- | ------------ | ----------- |
| rowSpan > 20, colSpan 60        | 1×1, 2×1    | 180      | −14         | 36       | 0.7          | 0           |
| rowSpan = 20, colSpan 60        | 3×1         | 170      | −14         | 36       | 0.7          | 0           |
| rowSpan ≥ 18, colSpan 60        |             | 145      | −12         | 36       | 0.7          | 0           |
| rowSpan ≥ 15, colSpan 60        | 4×1         | 130      | −12         | 36       | 0.7          | 0           |
| rowSpan ≥ 15, colSpan 30        | 2×2,3×2,4×2 | 94       | −10         | 33       | 0.7          | 0           |
| rowSpan ≥ 12, colSpan 60        | 5×1         | 104      | −10         | 33       | 0.7          | 0           |
| rowSpan ≥ 12, colSpan 30, Large | 5×2 Large   | 78       | −9          | 33       | 0.6          | −3          |
| else (5×2 Small)                | 5×2 Small   | 88       | −9          | 29       | 0.6          | 0           |

The Label Size setting affects ONLY 2-col 5-row cells (rowSpan 12–14,
colSpan 30). Every other layout, including all 1-col layouts and 2-col
2/3/4-row, renders the same label size at both settings. This resolves a
long-standing ambiguity in this file (rowSpan-driven vs setting-driven
29/33 px): the old table here was measured at Small; a later sweep at Large
saw 33 px in 5-row cells and mistook it for a global setting effect.

`ViewConfig.textSize` for extension cells is `(int)(value px / density)`, so
it tracks the Label Size setting: 5×2 delivers 46–47 sp at Small and 41 sp at
Large; all other layouts are setting-independent.

### Header band geometry

- The header container sits at the cell top with no inset; min height 22 dp,
  otherwise wrap_content, horizontal padding 1 dp.
- The label renders in `ibm-plex-sans-condensed`, allCaps, font padding off,
  ellipsize end, simple line-break strategy, gravity END|CENTER_VERTICAL,
  3 dp start/end margins.
- The label TextView reserves 2 lines in 2-col cells and 1 line in 1-col
  cells. The line-spacing multiplier is 0.6 only in 5-row 2-col cells and 0.7
  everywhere else (see table).
- The vertical position of a 1-line label emerges from TextView centering
  inside the 2-line reservation: reservation height
  `H = lineH + round(lineH × mult)` (`lineH` = `Paint.getFontMetricsInt`
  descent − ascent), 1-line text centered in `H`, then shifted by labelTransY
  (−3 px at 5×2 Large). This reproduces the observed header tops (~22 px below
  cell top at 5×2 Large, ~30 px in 2/3/4-row 2-col cells) without a separate
  per-layout inset constant.

### Icon geometry

- Icon size equals the label size: `width = height = label px`.
- Icons have 3 dp top/bottom margins and are centered in the header band; the
  icon-to-label gap is 3 dp.
- The key-icon toggle removes the icon views entirely (GONE), so the label
  regains the full width when icons are off.

### Verification status

Measured on-device:

- Label px at Small for the four 2-col/1-col rows of the original table.
- Label px at Large: 33 px bands in 2-col cells of every rowSpan, including
  5×2; native 5×2 value font ≈ 42 sp (= 78 px) and 2/3/4-row ≈ 50 sp (= 94 px)
  in the same sweep, independently confirming the Large column.
- 2-line pitch 0.6 at 5×2; `ViewConfig.textSize` per layout at one setting.

Inferred, not yet re-measured: the 0.7 multiplier actually rendering in 2-col
2/3/4-row 2-line headers, the −3 px label translation at 5×2 Large in
isolation, and icon px and gap with icons enabled.

---

## Stream-state icon tint and placeholders

Verified on-device 2026-07-04. Implemented in Barberfish in fa91ca7..6ab5f69.

### Icon tint

- The connected icon green is #32E09A in dark mode and #129A5E in light mode.
  The idle color is the theme foreground.
- The tint applies to the icon only; the label keeps the normal header color.
- Ride-clock fields (Ride Time) gate the green on ride state: theme foreground
  before the ride starts, green while Recording and while Paused. Stream-backed
  fields (grade, distance, avg speed) turn green whenever their stream
  delivers, even pre-ride.

### Placeholders

- Native fields draw placeholder messages from one shared vocabulary: No
  Sensor, No Route, Off Route, No GPS Signal, Needs 30s Power Data, Press Lap,
  No Laps Yet, No Data, Searching… (single ellipsis glyph), Loading…. Each
  message carries its own text and icon color.
- The strings are localized on the device (132 locales).

### Placeholder geometry

- The placeholder text anchors below the reserved max-lines label band, 19 sp,
  maxLines 2, line-spacing multiplier 0.6, 6 dp bottom margin.
- Barberfish's flat 1.7 `headerHeightPx` factor matches native everywhere
  except 5x2, where native uses the 0.6 spacing; there the 1.7 empirically
  cancels other unmodeled height, measured delta 0 px. Do not "fix" it to 1.6.

---

## Container resize on route toast (GitHub issue #2)

When a rerouting/turn-cue toast appears, the data-grid cells physically shrink, but
`startView` is not re-called with updated `ViewConfig.viewSize`; the extension
receives stale dimensions. Confirmed by logging the cell size delivered to
`startView` across a reroute event and comparing it to `dumpsys`-reported cell
bounds before vs after; the SDK-reported size stays put while the visible cells
shrink.

The SDK exposes no callback for container resize after `startView` and no event
for the nav-toast show/hide that triggers it.

### RemoteViews constraints on K2 (API 26)

Hammerhead's K2 ROM blocks several `@RemotableViewMethod` calls that work on stock AOSP:

- `setGravity(int)`: CRASH
- `setTextAlignment(int)`: CRASH
- `setTranslationY(float)`: CRASH

Workaround: bake gravity, alignment, and translationY into XML layout files and select
the appropriate variant at render time via `removeAllViews` / `addView` (both work on K2).

### Barberfish layout approach

Value centering uses `baseline_box` (LinearLayout with `weight=1` `TextView` spacers
around `field_value`), which adapts automatically when the cell shrinks.
`layout_below=header_ref` + `alignParentBottom` re-sizes the box, and the spacer
weights re-center the bitmap within the new bounds. No `viewSize` or `cellH` dependency.
See `docs/architecture.md` § "Value baseline alignment".

---

## NavigatingRoute.routePolyline is the un-snapped saved polyline

`OnNavigationState.NavigationState.NavigatingRoute.routePolyline` (precision-5 Google
encoded polyline) is the same geometry that was saved with the route. It is *not*
road-snapped to the map geometry the rideapp actually draws as the yellow
`ROUTE_LINE`. An extension that emits a `ShowPolyline` with `route.routePolyline`
verbatim will cut chords across hairpins where the native route line hugs the road.

Confirmed empirically: a diagnostic `ShowPolyline(encodedPolyline = route.routePolyline)`
drawn in a contrasting colour under the native route shows exactly the same
chord-cutting as a re-encoded subset built by our own extraction pipeline. The
divergence is visible on tight switchbacks; over straight roads the two lines
coincide.

### Implications for extensions that draw map overlays

Any extension that needs to trace the *actual road geometry* (not just the distance
axis along the route) currently cannot do so. The workaround is to accept the
chord-cutting, which is visually acceptable on most routes but noticeable on
switchbacks.

### Feature request

The ideal SDK fix is one of:

1. Guarantee `NavigatingRoute.routePolyline` is the road-snapped, detailed polyline
   the rideapp draws as `ROUTE_LINE`, and document the precision.
2. If the existing field is intentionally the saved geometry, add a separate
   `snappedRoutePolyline: String?` field on `NavigatingRoute` populated from the same
   source the rideapp uses for its `ROUTE_LINE` draw call.
3. Alternatively, accept `MapEffect` extensions that reference a built-in layer id
   (e.g. "colour the segments of `ROUTE_LINE` between distances `[d0, d1]` with
   colour X") so extensions never handle geometry at all and the rideapp always
   owns the polyline.

This was investigated during the climb overlay work. See
`app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ClimbPolylines.kt`
for the extraction pipeline that hits this limitation.
