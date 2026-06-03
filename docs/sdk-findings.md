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

## Native label font sizes

Device: Karoo 3, density = 1.875 (300 dpi / 160).

The native field header label uses a different pixel size for each `(colSpan, rowSpan)`
in the SDK's 60-unit grid. Measured from screencaps + `adb shell dumpsys activity
top` view-bounds for every layout 1×1 through 5×2:

| colSpan | rowSpan | labelSize (px) | labelSize (sp) | example layout    | textSize (sp) |
| ------- | ------- | -------------- | -------------- | ----------------- | ------------- |
| 60      | ≥ 15    | 36 px          | 19.2 sp        | 1-col 3- or 4-row | 69 – 96       |
| 60      | ≥ 12    | 33 px          | 17.6 sp        | 1-col 5-row       | 55            |
| 30      | ≥ 15    | 33 px          | 17.6 sp        | 2-col 4-row       | 50            |
| 30      | ≥ 12    | 29 px          | 15.5 sp        | 2-col 5-row       | 47            |

The right-most column is the SDK-supplied `ViewConfig.textSize` (sp) for that layout.
It appears to be `(int)(dataSize_px / density)` and corresponds to the recommended
value font size.

Icon size equals `labelSize` in both dimensions (`width = height = labelSize px`).

For narrow cells (`colSpan = 30`, `rowSpan ≥ 12`), the native label wraps to two
lines with a compressed inter-line gap (line-spacing multiplier ≈ 0.6) and a small
upward translation (~-3 px) to keep the value baseline stable.

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
