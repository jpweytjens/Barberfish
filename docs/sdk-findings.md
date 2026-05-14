# SDK findings

Reverse-engineered and empirically discovered behavior of the Karoo SDK and ride app.
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
`FieldState.unavailable()` ("—") is different — `FieldColor.Error` (red) in `field_value`, meaning
the stream is `Streaming` but a specific `DataPoint.values` key is `null`.

---

## Time field semantics: ELAPSED_TIME vs RIDE_TIME

Confirmed from `DataType.kt` source in [karoo-ext on GitHub](https://github.com/hammerheadnav/karoo-ext)
and decompiled SDK in `docs/karoo-ext_decompiled/DataType.kt`.

| Type constant                | SDK description                                                  | Meaning                                                |
| ---------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------ |
| `DataType.Type.ELAPSED_TIME` | "Ride Time — Time spent recording this ride"                     | Moving time, excluding paused time                     |
| `DataType.Type.RIDE_TIME`    | "Total Time — Time since this ride began, including paused time" | Wall-clock time from ride start, including paused time |
| `DataType.Type.PAUSED_TIME`  | "Paused Time — Time spent paused this ride"                      | Cumulative pause duration                              |

The relationship is: `RIDE_TIME = ELAPSED_TIME + PAUSED_TIME`.

"Recording" in the ELAPSED_TIME description means the timer only advances while the ride is
actively recording (not paused). This is confirmed by the observed bug: computing
`movingSeconds = ELAPSED_TIME - PAUSED_TIME` produces a value that shrinks while paused
(ELAPSED stays constant, PAUSED grows), causing avg-speed-moving to grow — the wrong behavior.

Correct formulas:

| Metric                  | Formula                                                                 |
| ----------------------- | ----------------------------------------------------------------------- |
| Moving time             | `ELAPSED_TIME` directly                                                 |
| Total (wall-clock) time | `RIDE_TIME`, or `ELAPSED_TIME + PAUSED_TIME`                            |
| Avg speed (moving)      | `DISTANCE / ELAPSED_TIME`                                               |
| Avg speed (total)       | `DISTANCE / RIDE_TIME` (or native `AVERAGE_SPEED` field if it is total) |



## DataPoint field units

`DataPoint.values` delivers raw `Double` values in these base units.
Always convert before display — never treat raw values as display-ready.

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

Tentative: native Karoo field previews appear to ignore the unit preference — they show
the same (metric-looking) demo values in both metric and imperial mode. Barberfish
previews do convert because `previewFlow()` reads `streamUserProfile()`. To be confirmed
with an actual ride comparing native vs Barberfish fields in imperial mode.

---

## Grade calculation

How the rideapp produces `FIELD_ELEVATION_GRADE` (`TYPE_ELEVATION_GRADE_ID`). The public
SDK only documents "Current Grade % - Steepness of current surface" — nothing about the
algorithm. The notes below are reverse-engineered from the decompiled rideapp.

Inputs. Grade is derived from four data types:

- Barometric elevation (raw pressure-derived, not GPS altitude, and not the
  altitude-corrected variant).
- Distance with paused time excluded.
- Current speed.
- A boolean "is moving" flag.

Elevation deadband. Before reaching the grade calculator, pressure elevation passes
through a 0.75 m deadband: if a new altimeter sample differs from the previous by less
than 0.75 m, the previous value is held and only the timestamp updates. Sub-half-meter
barometric jitter is therefore filtered out upstream. When the deadband does accept a
step, the elevation stream jumps by at least 0.75 m all at once — the input feeding the
grade calc is piecewise constant with infrequent steps, not a continuous noisy signal.

Live grade formula. The exact closed-form is not directly recoverable from the obfuscated
code (the transform is collapsed into a generic switch in shared lambda infrastructure),
but the dependency list and the absence of any filter coefficients on the path between
elevation and grade are enough to characterise it:

- Numerator: change in (deadbanded) barometric elevation.
- Denominator: change in distance-no-pause.
- Gated by the moving flag and/or low speed, so grade is suppressed or frozen when the
  rider is stopped (paused distance does not advance anyway, which would otherwise blow up
  the denominator).
- No EWMA, Kalman, or IIR-filter coefficients applied to grade itself. The only smoothing
  in the chain is the elevation deadband.

Output. Stored as percent in `[0.0, 100.0]` (matches the units table above), formatted to
2 decimal places for display.

Implication for Barberfish's `GradeField` EWMA. The SDK grade is filtered for sub-0.75 m
altimeter noise but is not time-smoothed. Our EWMA is therefore smoothing the
step-function character that the deadband produces when it accepts a step of 0.75 m or
more, not raw altimeter noise. Useful when tuning the half-life: the input is piecewise
constant with infrequent jumps, so a smoother that handles step responses gracefully is
the right shape of tool.

---

## Distance calculation

How the rideapp produces `FIELD_DISTANCE` (`TYPE_DISTANCE_ID`, and the derived
`TYPE_DISTANCE_NO_PAUSE_ID` that feeds grade and other paused-time-aware fields). The
public SDK does not document the algorithm; the notes below are reverse-engineered from
the decompiled rideapp.

Distance is fused from five per-sample diff sources, each emitting `FIELD_DISTANCE`
deltas:

| Source                           | What it is                                               |
| -------------------------------- | -------------------------------------------------------- |
| `TYPE_POWER_DISTANCE_DIFF_ID`    | Power meter that also reports wheel speed                |
| `TYPE_CSC_DISTANCE_DIFF_ID`      | Combined speed+cadence sensor (ANT+/BLE CSC profile)     |
| `TYPE_SPD_DISTANCE_DIFF_ID`      | Dedicated wheel-speed sensor (rotations × circumference) |
| `TYPE_LEV_DISTANCE_DIFF_ID`      | E-bike / Light Electric Vehicle system                   |
| `TYPE_LOCATION_DISTANCE_DIFF_ID` | GPS — per-fix lat/lon delta                              |

The GPS branch's upstream is `TYPE_LOCATION_ID`, which carries
lat/lon/bearing/accuracy/altitude/speed. So the GPS fusion has accuracy information
available to it, even if how that information is used is not visible from this layer.

`TYPE_DISTANCE_NO_PAUSE_ID` is the same distance with paused samples filtered out; its
declaration reuses the base distance field list and applies the no-pause behaviour
elsewhere in the pipeline.

What we could not reconstruct: the selector / combiner that picks among (or sums across)
the five sources and adds the chosen delta to a running total. As with grade, the actual
combiner is collapsed into shared lambda infrastructure and is not pinpoint-identifiable.
Common-sense ordering would prefer wheel-sensor-based sources over GPS, but that is
inference, not observation.

Implication for Barberfish. Distance noise — and therefore the grade-denominator noise
that drives most grade spikes — depends entirely on which source wins:

- With any wheel-speed source paired (CSC, SPD, or a power meter that reports speed),
  distance is essentially exact. Residual grade noise can then only come from the
  elevation deadband-step.
- With GPS only, distance comes from per-fix position deltas. The GPS branch may or may
  not be internally smoothed before producing diffs (`LOC_ACCURACY` is available, but we
  cannot see how it is used). At low speeds this denominator is the more likely source of
  grade spikes.

The Karoo silently switches sources based on what is paired and reporting, so the same
rider can see different grade noise characteristics on different rides depending on their
sensor setup.

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

- `android.view.View` — the base class, even used as a spacer
- `android.widget.Space`
- `androidx.constraintlayout.widget.ConstraintLayout`
- Any custom or third-party view class

---

## SDK container geometry

When `emitter.updateView(rv)` is called, the ride app creates a `FrameLayout` and inserts
it into the root `ConstraintLayout` of `data_element_sdk.xml`:

```
ConstraintLayout.LayoutParams(MATCH_PARENT, 0dp)
topToBottom = R.id.headerLayout
bottomToBottom = PARENT_ID
```

With `showHeader = false`, `headerLayout` has `visibility = GONE`, so the `FrameLayout`
fills exactly the full cell bounds (no offset, no inset). Our `RemoteViews` is then
`apply()`-ed into this `FrameLayout`.

The `sdkViewContainer` element (which has `translationY = -15dp`) is used only for
non-RemoteViews SDK views created via `sdkView.createView()`. It does not affect
RemoteViews-based fields.

Do not add padding or translation to compensate for any assumed offset — there is none.
The field container is exactly `cell_width × cell_height`. Mirror `data_element_single.xml`
directly.

---

## Route polyline simplification

Two separate pipelines; only one applies simplification.

Map display: the ride app uses Visvalingam-Whyatt (`SimplifyVW`) and Douglas-Peucker
(`SimplifyDP`) from `org.oscim.utils.geom` to simplify route geometry for vector tile
rendering. This is why the route line looks simpler at lower zoom levels on the Route
selection screen — it is a purely visual effect on the map geometry.

`routeElevationPolyline` (what the SDK exposes): a separate encoded string that is passed
through to navigation state without simplification. The ride app's own decoder reads it
with the same precision=1 call and no further filtering. Its resolution is fixed at route
creation time (server-side or GPX import) and is unaffected by zoom level.

---

## Native label font sizes

The `DataElementConstraints` factory.
Device: Karoo 3, density = 1.875 (300 dpi / 160).

The ride app uses a hardcoded pixel lookup table keyed on `(colSpan, rowSpan)` from the
60-unit grid, then calls `textView.setTextSize(COMPLEX_UNIT_PX, labelSize)`.

`ViewConfig.textSize` is computed as `(int)(dataSize_px / density)` — the value font
size in dp (≈ sp at Karoo's fixed font scale of 1.0).

| colSpan | rowSpan | labelSize (px) | labelSize (sp) | example layout    | textSize (sp) |
| ------- | ------- | -------------- | -------------- | ----------------- | ------------- |
| 60      | ≥ 15    | 36 px          | 19.2 sp        | 1-col 3- or 4-row | 69 – 96       |
| 60      | ≥ 12    | 33 px          | 17.6 sp        | 1-col 5-row       | 55            |
| 30      | ≥ 15    | 33 px          | 17.6 sp        | 2-col 4-row       | 50            |
| 30      | ≥ 12    | 29 px          | 15.5 sp        | 2-col 5-row       | 47            |

Icon size equals `labelSize` in both dimensions (`width = height = labelSize px`).

For narrow cells (`colSpan = 30`, `rowSpan ≥ 12`), the native label uses two lines with
`lineSpacingMultiplier = 0.6` and `translationY = -3px` to collapse the inter-line gap.

---

## Native ETA estimation (TIME_TO_DESTINATION)

Source: decompiled ride app, `hhp7/m.java` (`TYPE_TIME_TO_DESTINATION_ID`).

The native ETA data type declares four dependencies:

| Dependency         | Obfuscated class | Data type ID                      |
| ------------------ | ---------------- | --------------------------------- |
| Dist to dest       | `hha7.g`         | `TYPE_DISTANCE_TO_DESTINATION_ID` |
| Avg speed (moving) | `hhl7.g`         | `TYPE_AVERAGE_SPEED_ID`           |
| 1hr avg speed      | `hhl7.c`         | `TYPE_1HR_AVERAGE_SPEED_ID`       |
| Ride time (total)  | `hhp7.j`         | `TYPE_RIDE_TIME_ID`               |

`TYPE_AVERAGE_SPEED_ID` is constructed with `TYPE_ELAPSED_TIME_ID` as its time
dependency (`hhp7.b`), meaning it computes distance / moving time (excluding paused
time). `TYPE_RIDE_TIME_ID` is wall-clock time including pauses.

The exact formula that combines these inputs is inside heavily obfuscated processor
code and could not be reconstructed. What we know:

- It uses both overall average speed and a 1-hour rolling window average speed,
  suggesting some kind of blended estimate rather than a simple `distance / avg_speed`.
- Ride time (wall-clock, including paused time) is an input, which may explain the
  reported odd behavior during pauses — if the blend weights depend on elapsed time,
  pausing could shift the weight between the two speed components.
- The processor class (`hhm7.c`, case 1) selects between a "loading" and "no route"
  state but the computation itself is dispatched through further obfuscated layers
  that could not be traced.

---

## Container resize on route toast (GitHub issue #2)

When a rerouting/turn-cue toast appears, the rideapp adjusts the data grid bottom margin
via `hho9.e.hho()`. The grid cells physically shrink, but `startView` is not re-called
with updated `ViewConfig.viewSize` — the extension receives stale dimensions.

### How the rideapp handles it

1. `PersistentNavBarPresenter` (`hhu0/q.java`) detects `NavigationRerouting`,
   `NavigationRerouted`, or `NavigationAlert` instructions
2. Emits `PersistentNavIsShowing` / `PersistentNavIsHidden` events (internal, not in SDK)
3. `DataElementPageFragment` (`hho9/e.java`) sets RecyclerView bottom margin:
   - Nav bar or key buttons visible: `persistent_nav_bottom_padding` = 52dp
   - Neither visible: `no_keys_bottom_padding` = 10dp
4. Cells resize proportionally; the `OnLayoutChangeListener` on itemView fires
5. The `distinctUntilChanged` → `switchMap` chain exists in the code but does NOT
   trigger a new `startView` on firmware 1.628+

### What the SDK does not expose

- `PersistentNavIsShowing` / `PersistentNavIsHidden` events (rideapp-internal)
- `UserProfile.getShowKeyButtons()` (rideapp-internal, not in SDK `UserProfile`)
- Any callback for container resize after `startView`

### RemoteViews constraints on K2 (API 26)

Hammerhead's K2 ROM blocks several `@RemotableViewMethod` calls that work on stock AOSP:

- `setGravity(int)` — CRASH
- `setTextAlignment(int)` — CRASH
- `setTranslationY(float)` — CRASH

Workaround: bake gravity, alignment, and translationY into XML layout files and select
the appropriate variant at render time via `removeAllViews` / `addView` (both work on K2).

### Barberfish layout approach

Value centering uses `baseline_box` (LinearLayout with `weight=1` `TextView` spacers
around `field_value`), which adapts automatically when the rideapp shrinks the cell —
`layout_below=header_ref` + `alignParentBottom` re-sizes the box, and the spacer
weights re-center the bitmap within the new bounds. No `viewSize` or `cellH` dependency.
See `docs/architecture.md` § "Value baseline alignment".
