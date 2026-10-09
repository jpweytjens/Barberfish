# SDK findings

Behaviour of the Karoo SDK and rideapp that the SDK documentation does not
state, observed on-device with `karoo-ext`, ADB instrumentation, and screencap
analysis. Each section says how the finding was measured and, where Barberfish
works around it, points at the code that does.

---

## StreamState semantics

`OnStreamState` delivers one of four `StreamState` variants. Barberfish maps each to a `FieldState`:

| Variant        | Barberfish factory          | Display text    | Meaning                                                     |
| -------------- | --------------------------- | --------------- | ----------------------------------------------------------- |
| `Streaming`    | happy path                  | live value      | Sensor actively emitting data; `DataPoint` is valid         |
| `Searching`    | `FieldState.searching()`    | "Searching"     | Sensor is paired but offline / reconnecting                 |
| `NotAvailable` | `FieldState.notAvailable()` | "Not available" | Feature not supported on this device (permanent)            |
| `Idle`         | `FieldState.idle()`         | "No data"       | Sensor connected but silent (ride paused, movement stopped) |

The three non-streaming states use `FieldColor.StreamState` and render white in
`stream_state_tv` (ibm-plex-sans-condensed). `FieldState.unavailable()` ("—") is a
different case: the stream is `Streaming` but a specific `DataPoint.values` key is
`null`, and it renders red (`FieldColor.Error`) in `field_value`.

---

## Time field semantics: ELAPSED_TIME vs RIDE_TIME

Confirmed from `DataType.kt` in [karoo-ext on GitHub](https://github.com/hammerheadnav/karoo-ext).

| Type constant                | SDK description                                                  | Meaning                                                |
| ---------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------ |
| `DataType.Type.ELAPSED_TIME` | "Ride Time — Time spent recording this ride"                     | Moving time, excluding paused time                     |
| `DataType.Type.RIDE_TIME`    | "Total Time — Time since this ride began, including paused time" | Wall-clock time from ride start, including paused time |
| `DataType.Type.PAUSED_TIME`  | "Paused Time — Time spent paused this ride"                      | Cumulative pause duration                              |

The relationship is `RIDE_TIME = ELAPSED_TIME + PAUSED_TIME`.

"Recording" in the ELAPSED_TIME description means the timer advances only while the
ride is recording, not while paused. The bug that confirmed it: computing
`movingSeconds = ELAPSED_TIME - PAUSED_TIME` gives a value that shrinks while paused
(ELAPSED holds, PAUSED grows), so moving average speed climbs during a stop.

Correct formulas:

| Metric                  | Formula                                                                 |
| ----------------------- | ----------------------------------------------------------------------- |
| Moving time             | `ELAPSED_TIME` directly                                                 |
| Total (wall-clock) time | `RIDE_TIME`, or `ELAPSED_TIME + PAUSED_TIME`                            |
| Avg speed (moving)      | `DISTANCE / ELAPSED_TIME`                                               |
| Avg speed (total)       | `DISTANCE / RIDE_TIME` (or native `AVERAGE_SPEED` field if it is total) |

---

## DataPoint field units

`DataPoint.values` delivers raw `Double` values in these base units. Nothing in it
is display-ready.

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

`TIME_OF_ARRIVAL` is the special case: milliseconds since midnight, not since the
epoch. Divide by 1000, then take modulo 86400 for seconds since midnight.

Units are base SI whatever the rider's unit setting. Converting to km/h or mph, km
or mi, is the extension's job, from `UserProfile.preferredUnit`.

Unverified: native field previews appear to ignore the unit preference and show the
same metric-looking demo values in both modes. Barberfish previews do convert,
because `previewFlow()` reads `streamUserProfile()`. A ride comparing native and
Barberfish fields in imperial mode would settle it.

---

## Preview update rate floor

The rideapp silently cancels a preview flow that emits faster than about 900 ms.
No error is raised; the preview just stops updating.

Barberfish emits every preview at [`Delay.PREVIEW`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/Delay.kt),
1000 ms, and never below. Live flows are unaffected: the `sampleMs = 400L` default
in `BarberfishDataType` is safe, and `TimeField` samples at 1000 ms only because
seconds-resolution data needs nothing faster.

---

## RemoteViews class allowlist

Extension fields are rendered cross-process by `RemoteViews.apply()` in the rideapp,
whose `LayoutInflater` enforces a class allowlist. Anything not on it fails at
runtime with `InflateException: Class not allowed`, with no compile-time warning.

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

## RemoteViews methods that crash on Karoo 2

Karoo 2 runs Android 8 and Karoo 3 runs Android 12 (API 32). The APK installs on
both (minSdk 23), but three setter calls that apply cleanly on Karoo 3 throw at
`RemoteViews.apply` on Karoo 2, where the method is not `@RemotableViewMethod`:

- `rv.setInt(id, "setGravity", ...)`
- `rv.setInt(id, "setTextAlignment", ...)`
- `rv.setFloat(id, "setTranslationY", ...)`

The rideapp catches the exception and the cell renders blank. Logcat shows:

```
android.widget.RemoteViews$ActionException:
  view: android.widget.TextView can't use method with RemoteViews: setGravity(int)
  at android.widget.RemoteViews.getMethod(RemoteViews.java:974)
  at android.widget.RemoteViews$ReflectionAction.apply(RemoteViews.java:1521)
```

The workaround is to bake the attribute into the layout XML (`android:gravity`,
`android:textAlignment`, `android:translationY`), one layout variant per value, and
pick the variant at render time. `removeAllViews` and `addView` work on both devices,
and an attribute set in XML is applied by the inflater, never through the remotable
method check.

---

## SDK container geometry

When `emitter.updateView(rv)` is called with `showHeader = false`, the rideapp gives
the `RemoteViews` a container that fills the cell bounds exactly: no offset, no
inset. Measured by reading `field_root`'s on-screen bounds from
`adb shell dumpsys activity top` against the cell rectangle in a screencap; the two
match to the pixel.

So there is no offset to compensate for. Padding or translation added for one only
moves the field off the cell.

The SDK's separate path for non-`RemoteViews` views, `sdkView.createView()`, does not
apply to extension data fields rendered through `emitter.updateView(rv)`.

---

## Route polyline simplification

Two separate pipelines; only one simplifies.

On the map, the route line looks visibly simpler at lower zoom levels on the Route
selection screen, which zooming in and out and counting kinks on the line confirms.
That is map-tile rendering and does not touch the route data.

`routeElevationPolyline`, the one the SDK exposes, is a separate encoded polyline the
SDK passes through to navigation state without simplification. Its resolution is
fixed at route creation (server-side or GPX import) and independent of zoom. Decode
it with the standard Google Encoded Polyline algorithm at precision 1, verified by
decoding and overlaying it on the map.

---

## Native header and value sizing

Device: Karoo 3, density = 1.875 (300 dpi / 160). Established by on-device screencap
sweeps ([walk_layouts.sh](../scripts/walk_layouts.sh) and
[measure_alignment.py](../scripts/measure_alignment.py)) across both Label Size
settings. All sizes are raw px on the 1.875-density screen.

The rideapp sizes each data cell by its column and row span in the 60-unit grid and
by the rider's Label Size setting (Small or Large). Measured per layout:

| layout (cols×rows) | value px | label px |
| ------------------ | -------- | -------- |
| 1×1, 2×1           | 180      | 36       |
| 3×1                | 170      | 36       |
| 4×1                | 130      | 36       |
| 2×2, 3×2, 4×2      | 94       | 33       |
| 5×1                | 104      | 33       |
| 5×2, Large         | 78       | 33       |
| 5×2, Small         | 88       | 29       |

The Label Size setting affects only 5×2 cells. Every other layout, including all
1-col layouts and 2-col 2/3/4-row, renders the same label size at both settings. An
earlier table here was measured at Small; a later sweep at Large saw 33 px in 5-row
cells and mistook it for a global setting effect.

`ViewConfig.textSize` for extension cells is `(int)(value px / density)`, so it
tracks the Label Size setting: 5×2 delivers 46–47 sp at Small and 41 sp at Large;
every other layout is setting-independent.

The value sits slightly above the centre of the band between the header and the
cell bottom. Barberfish reproduces that with a small upward translation per layout
(see [Per-layout vertical translation](architecture.md#per-layout-vertical-translation)).

### Header band geometry

- The header sits at the cell top with no inset and is about 22 dp tall for a
  one-line label, with the label right-aligned in a condensed all-caps font and
  about 3 dp of side margin.
- A one-line label sits lower than the cell top (about 30 px in 2/3/4-row
  two-column cells, about 22 px at 5×2 Large), consistent with the label being
  centred in a two-line reservation. Two-column cells reserve two lines,
  one-column cells one. Barberfish reproduces the observed tops by reserving
  the lines and centring, not with a per-layout inset.
- Two-line labels are set tight: at 5×2 the line pitch measured 0.6 of the
  line height. Other two-column layouts look the same but were not
  re-measured.

### Icon geometry

- Icons appear the same height as the label text, centred in the header band,
  with about a 3 dp gap to the label. Not re-measured with icons on.
- The key-icon toggle removes the icon entirely, so the label regains the full
  width when icons are off.

---

## Stream-state icon tint and placeholders

Verified on-device 2026-07-04.

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
- The strings are localized on the device.
- The placeholder text sits below the reserved label band, smaller than a
  value and at most two lines.
- Barberfish's flat 1.7 `headerHeightPx` factor matches native everywhere
  except 5×2, where native uses the 0.6 spacing; there the 1.7 cancels other
  unmodeled height, measured delta 0 px. Do not "fix" it to 1.6.

---

## Container resize on route toast (GitHub issue #2)

When a rerouting or turn-cue toast appears, the data-grid cells shrink, but
`startView` is not called again with a new `ViewConfig.viewSize`, so the extension
keeps stale dimensions. Confirmed by logging the cell size delivered to `startView`
across a reroute and comparing it with the `dumpsys` cell bounds before and after:
the SDK-reported size holds while the visible cells shrink.

The SDK exposes no callback for a container resize after `startView` and no event
for the toast that triggers it.

Barberfish centres the value with weighted spacers that re-centre as the cell
shrinks, with no dependency on the delivered size; see
[Value baseline alignment](architecture.md#value-baseline-alignment).

---

## NavigatingRoute.routePolyline is the un-snapped saved polyline

`OnNavigationState.NavigationState.NavigatingRoute.routePolyline` (precision-5
Google encoded polyline) is the geometry saved with the route. It is not snapped to
the road geometry the rideapp draws as the yellow `ROUTE_LINE`, so a `ShowPolyline`
of `route.routePolyline` verbatim cuts chords across hairpins where the native line
hugs the road.

Confirmed by drawing `ShowPolyline(encodedPolyline = route.routePolyline)` in a
contrasting colour under the native route: it cuts exactly the same chords as a
re-encoded subset of the same points. The divergence shows on tight switchbacks;
over straight roads the two lines coincide.

No SDK field carries the road-snapped line, so an overlay drawn along the route has
to accept the chords.

---

## Route polyline and distance fields disagree on direction when reversed

`NavigatingRoute.routePolyline` arrives in saved order whichever way the rider is
travelling. `routeElevationPolyline` arrives in ride order, so its distance axis
starts at the rider's actual start point, and `climbs[].startDistance` and
`DISTANCE_TO_DESTINATION` are in ride order too. `NavigatingRoute.reversed` is the
only signal linking the two orderings, so an extension that combines the GPS
polyline with a ride-order distance has to reverse the decoded GPS points itself
when `reversed` is true.

Confirmed by loading the same route once forward and once reversed: the first and
last GPS coordinates in `routePolyline` were identical both times, while the
elevation profile's start and end elevations swapped. Route length 115810 m.

Still unverified: whether `rejoinDistance` and `Symbol.POI.distancesAlongRoute`
follow saved order or ride order.

---

## Map symbol lifecycle: what survives what

Confirmed on a Karoo 3 (rideapp 4.x, 2026-08-22) by force-stopping the extension
mid-ride and comparing painted symbols against emissions:

- Drawn extension symbols and polylines survive extension process death and
  `startMap` cancel/restart cycles. The rideapp keeps them painted; a fresh
  `startMap` that does not re-mint an id leaves that symbol on the map until
  ride end with nothing able to hide it.
- Ride end clears all extension symbols. Ghosting across rides does not occur.
- `startMap` is not cancelled by paging between ride pages. It is cancelled
  when the rideapp loses the foreground (and a new `startMap` arrives on
  return) and at ride end.
- `ShowPolyline` with an existing id replaces that polyline in place.
- `HideSymbols` for an id emitted just before `ShowSymbols` for the same id
  can be processed after it, permanently blanking the symbol. Large paired
  hide/show batches (about 160 ids and up) lost every chevron reproducibly.
  Never hide an id in the same emission that shows it; only hide ids nothing
  is about to show.

An extension that mints ids per generation therefore has to hide the previous
generation's range itself on a fresh `startMap`, from a record it kept; nothing
tells it what is still painted.

---

## Map layer order: extension drawings cover the native chevrons

Observed on a Karoo 3 (2026-09-17) with a route loaded and a route-width extension
polyline drawn along it, by enlarging a screencap of the map page four times:

- Extension polylines paint above the native route line and above its direction
  chevrons. A polyline at route width hides the line's colour and the centre of
  every chevron it crosses.
- The native chevrons are wider than the route line, so their outer ends still
  show either side of a route-width polyline. That overhang is what makes them
  look as if they were drawn on top; they are not.
- Extension symbols paint above extension polylines.
- The native chevron is a yellow chevron with a black outline. On the yellow
  route line only the outline reads, so it looks black until something covers
  the line beneath it.

There is no API to draw beneath the route line or to change any of this ordering.
A route-width overlay therefore always costs the direction cue where it is drawn;
an overlay that wants to keep the chevrons legible has to be narrower than the
line, sparse along it, or offset beside it.

---

## Extension polyline width is in dp, and casings need a second batch

Measured on a Karoo 3 (2026-09-17) by requesting widths of 30 and 36 and reading the
drawn band off a screencap: about 55 px and 67 px, the request times the 1.875
screen density. `ShowPolyline.width` is therefore dp, not pixels. A width of 8 draws
a 15 px band, a little wider than the 13 px route line, and the native direction
chevrons span about 27 px (31 px with their outline), so hiding them takes 17 dp or
more.

Extension polylines have no outline of their own. A casing is a second, wider black
polyline under the fill, and its order matters: a new layer goes on top of
everything already in the extension group, while a `ShowPolyline` for an id that
already exists updates that layer in place and keeps its position. Casing and fill
sent in one batch came out with the casing on top, repeatedly, even after hiding
every id first, so a single batch is not processed in emission order. Sending the
casings, waiting about half a second, then sending the fills put the fills on top,
and later in-place updates kept that order.

---

## A Strava route arrives as its own geometry

Captured on a Karoo 3 (2026-09-19) by logging `NavigatingRoute.routePolyline` for a
route imported from a Strava export and comparing it with the export's track points
rounded to five decimals: 748 points against the export's 746, the two extra being
exact duplicates of their predecessor, and the same length to the metre. The rideapp
does not re-route or resample an imported route; the vertices are the export's,
quantised by the polyline encoding. Two passes over the same road in a planned route
therefore share their vertices.

---

## `routeDistance` runs longer than the polyline

On the same route `NavigatingRoute.routeDistance` reported 34,151 m while the
polyline's cumulative equirectangular length is 34,113.5 m, a ratio of 1.0011. The
distance field and `DISTANCE_TO_DESTINATION` are on the rideapp's axis; anything
measured on the polyline is on the other. Rescale by the ratio of the two lengths
before comparing a distance from one axis with a position on the other;
thirty-eight metres is more than the tolerance of anything matching a point near
the end of the route.

---

## One map effect is one Binder transaction, capped near 1 MB

Observed on a Karoo 3 (2026-09-20) with a 295 km route at zoom 16. The chevron layer
sent every symbol of the route in a single `ShowSymbols`, over 3,000 of them, and
the extension process died with `TransactionTooLargeException: data parcel size
1053588 bytes`. It restarted, redrew the same effect and died again. Each
`Emitter.onNext` is a synchronous Binder call, and Android caps one transaction near
1 MB, so an effect that grows with route length has to be split. Barberfish sends
symbols in messages of at most 500. Polylines are never at risk: each piece is its
own effect, and even the whole 295 km route encodes to about 40 KB.

Each effect also costs 1.2 to 1.7 ms in the Binder call itself, measured over
several thousand effects on the same route, so a band of a few thousand pieces takes
about ten seconds to send. The rideapp writes nothing to the log while it draws, so
the time it takes to place the layers after they arrive can only be read off the
screen.
