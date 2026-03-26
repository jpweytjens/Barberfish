# SDK findings

Reverse-engineered and empirically discovered behavior of the Karoo SDK and ride app.
These are not documented in the official SDK AFAIK.

---

## StreamState semantics

`OnStreamState` delivers one of four `StreamState` variants. Barberfish maps each to a `FieldState`:

| Variant        | Barberfish factory       | Display text    | Meaning                                                      |
| -------------- | ------------------------ | --------------- | ------------------------------------------------------------ |
| `Streaming`    | happy path               | live value      | Sensor actively emitting data; `DataPoint` is valid          |
| `Searching`    | `FieldState.searching()`    | "Searching"     | Sensor is paired but offline / reconnecting                  |
| `NotAvailable` | `FieldState.notAvailable()` | "Not available" | Feature not supported on this device (permanent)            |
| `Idle`         | `FieldState.idle()`         | "No data"       | Sensor connected but silent (ride paused, movement stopped)  |

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

## Value-centering in RemoteViews

The native field centers the value in the space below the header using a ConstraintLayout
(`top_toBottomOf=header`, `bottom_toBottomOf=parent`, `wrap_content`). ConstraintLayout is
not allowed in RemoteViews, so the equivalent pattern using allowed view types is:

```xml
<LinearLayout android:orientation="vertical">
    <LinearLayout android:id="@+id/field_header"
                  android:layout_height="wrap_content"> ... </LinearLayout>
    <RelativeLayout android:layout_height="0dp"
                    android:layout_weight="1">
        <TextView android:id="@+id/field_value"
                  android:layout_height="wrap_content"
                  android:layout_centerVertical="true" />
    </RelativeLayout>
</LinearLayout>
```

`RelativeLayout` with `layout_centerVertical="true"` centers the view itself in the
available space. The alternative (`FrameLayout` + `match_parent` + `gravity=center_vertical`)
only centers the text within the view, not the view in the space — it produces incorrect
vertical alignment when `includeFontPadding=false`.

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
