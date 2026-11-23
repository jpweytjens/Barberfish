# Barberfish architecture

How a Barberfish data field is drawn, and why it is drawn that way. The reader is an extension author who wants a field that looks native inside the rideapp's RemoteViews constraints; the SDK behaviour those constraints come from is in [SDK findings](sdk-findings.md).

## The native Karoo field anatomy

Every native Hammerhead data field follows the same visual structure:

```
┌─────────────────────────────┐
│ 🔥 ← Icon   POWER  ← Header │
│                             │
│          239       ← Value  │
└─────────────────────────────┘
```

The Header sits at the top of the cell and contains:
- an optional Icon (tinted teal, or black when the cell has a colored background)
- a Label: short, all-caps, usually one word (POWER, HR, SPEED) but two lines for compound names (AVG SPEED  MOVING)

The Value is the large number below the header. Its font shrinks to fit longer strings, so `00:18` renders smaller than `239`.

Barberfish reimplements this anatomy as `RemoteViews`, in three alignment-specific layouts (`barberfish_field.xml` for right, `barberfish_field_left.xml` for left, `barberfish_field_center.xml` for center), and adds zone coloring, per-string font sizing, and a three- or four-column HUD.

---

## Rendering entry point

All field rendering goes through one function, [`barberfishFieldRemoteViews`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/BarberfishView.kt):

```
barberfishFieldRemoteViews(field, alignment, colorMode, sizeConfig, preview, context)
  → RemoteViews (barberfish_field*.xml, selected by alignment + valueTranslationDp)
      ├── stream_state_tv   TextView      (GONE by default; shown for non-Streaming SDK states)
      ├── header_ref        TextView      (invisible 1-line probe; anchors baseline_box)
      ├── field_header      LinearLayout  (icon + label, alignParentTop, may wrap to 2 lines)
      │   ├── field_icon            ImageView
      │   ├── field_icon_secondary  ImageView  (GONE by default)
      │   └── field_label           TextView
      └── baseline_box      LinearLayout  (layout_below=header_ref, alignParentBottom)
          ├── TextView (weight=1)         (top spacer)
          ├── field_value   ImageView     (Bitmap, baseline pinned to bottom edge)
          └── TextView (weight=1)         (bottom spacer)
```

Alignment selects the layout file: `barberfish_field.xml` (right), `barberfish_field_left.xml` (left), `barberfish_field_center.xml` (center). Vertical translation is baked into `*_neg3.xml` variants through `android:translationY` on `field_value`, selected when `valueTranslationDp == -3`. Nothing calls `setGravity()` or `setTranslationY()` at runtime, because both [crash on Karoo 2](sdk-findings.md#remoteviews-methods-that-crash-on-karoo-2).

The function receives a `FieldState` and a `ViewSizeConfig` and nothing else: no streams, no DataStore, no configuration. Every sizing decision is made by the caller before the call.

Config-screen previews go through the same function. [`remoteViewsToBitmap`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/RemoteViewsBitmap.kt) applies, measures, lays out and draws the `RemoteViews` into a `Bitmap`, which Compose shows with `Image(bitmap.asImageBitmap())`. A preview caller passes the exact cell width in pixels through `ViewSizeConfig.cellWidthPxOverride`, in place of the `dm.widthPixels * colSpan / 60` formula on-device rendering uses.

### Decimal separator

Native Karoo fields render a dot as the decimal separator in every locale, and a comma would clip against the digit-tuned value bitmap (issue #6). So fields format their values with `Locale.ROOT`, which always gives a dot. `makeFieldRemoteViews` still replaces any comma with a dot in a local `valueText` before sizing and drawing, as a guard for a formatter that leaves out the locale.

The guard is safe only because value strings carry no grouping separator (Karoo and Barberfish render `1234 W`, never `1,234`), so the only comma a locale can introduce is the decimal one.

---

## HUD layout

The HUD field uses `barberfish_hud.xml` (3-col) or `barberfish_hud_four.xml` (4-col), a horizontal `LinearLayout` of equal-weight `FrameLayout` slots. `HUDDataType` fills each slot with a call to `barberfishFieldRemoteViews()`, passing a `colSpanOverride` and `textSizeOverride` for the column count (see *Value font sizing* below) in place of the SDK's full-cell `textSize`.

```
barberfish_hud.xml (LinearLayout horizontal)
├── hud_slot_left   FrameLayout  (weight=1)
│   └── barberfishFieldRemoteViews(...)
├── hud_slot_middle FrameLayout  (weight=1)
│   └── barberfishFieldRemoteViews(...)
└── hud_slot_right  FrameLayout  (weight=1)
    └── barberfishFieldRemoteViews(...)
```

The 4-col variant `barberfish_hud_four.xml` has the same shape plus a fourth `hud_slot_fourth` `FrameLayout`.

---

## Data flow

```
DataTypeImpl subclass
  │  emits FieldState every update tick
  ▼
BarberfishDataType.startView()
  │  calls config.toViewSizeConfig() → ViewSizeConfig
  │  calls
  ▼
barberfishFieldRemoteViews(field, alignment, colorMode, sizeConfig, preview, context)
  │  derives ColorConfig from field.color + colorMode
  └─► RemoteViews update emitted to Karoo rideapp
```

For the HUD, `HUDDataType.startView()` computes one `ViewSizeConfig` (with `colSpanOverride=20`) and calls `barberfishFieldRemoteViews()` once per slot.

`FieldState` is the runtime snapshot of one field: primary value string, header label, icon resource, zone color, and color rendering mode. `DataTypeImpl` subclasses emit `FieldState` values; views are pure functions of `FieldState` and configuration.

---

## Following the route

A route reaches Barberfish in one of two navigation states: a loaded route, which also covers a reroute back onto it, or navigation to a dropped pin or POI. The two carry different fields, so every route-dependent surface (the HUD profile, Overview and the grade map) reads the state through one conversion, [`followedRoute()`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/FollowedRoute.kt), which returns a `FollowedRoute`, or null when nothing is navigated. A pin route has no length, direction or rejoin path of its own, so `FollowedRoute` takes its length from the end of the elevation profile and marks it with a non-null `destination`. New route-dependent code reads `FollowedRoute` instead of matching navigation states itself, so a new kind of route reaches every surface at once.

---

## Moving time and distance

Avg Speed (moving) is distance over moving time, and the Karoo's recording time (`ELAPSED_TIME`) is not moving time: it runs while the ride records, with or without a speed signal, and after a power-off it can restart while the distance is kept. Take a ride at 10.0 mi after 3360 s. The Karoo is switched off and comes back with 10.0 mi and 1 s. Twelve minutes later, at 12.1 mi, its recording time is 721 s and distance over it gives 60 mph. Barberfish keeps its own pair, [`MovingTimeAndDistance`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/MovingTimeAndDistance.kt), which at that moment says 4080 s and 10.7 mph.

The pair grows in steps. Each step takes the change in the Karoo's recording time and distance since the last step. When either went backwards the Karoo restarted, and the pair keeps its value while it takes the new readings as the base for the next step; that is how the 3360 s survive. A forward change is added in full when the Karoo has a speed reading (a speed of zero counts) or the distance moved. With neither, nothing is added, so time spent standing without a speed signal stays out of moving time. A restore that rolls both values back a few seconds keeps those seconds in the pair, which then runs a little ahead of the Karoo's recording time. After a restart or a stretch without signal, the Moving Time field therefore differs from the Karoo's own recording time, by design.

The steps run in the extension service, not in a field, so a field on a page that is not shown misses none of them. [`MovingTimeAndDistanceFlow.kt`](../app/src/main/kotlin/com/jpweytjens/barberfish/extension/MovingTimeAndDistanceFlow.kt) takes a step once a second while a ride is recording or paused and a field reads the pair, and one at every change of ride state; between rides nothing ticks. It exposes the pair as one `StateFlow`, so time and distance from the same step arrive together. The pair has a value only during a ride whose active profile holds a field that reads it, on any page or in a HUD slot. Otherwise the Karoo's time, distance and speed streams are not read for it, the pair has no value, and each field reads the Karoo's own values. ETA restarts its running speed estimate whenever it switches between the two sources.

The stored state is four numbers in a DataStore file of its own, `barberfish_ride`, so that saving it does not wake the readers of the settings file: the pair, and the Karoo's recording time and distance at the same step. Those last two are the base for the first step after the extension or the Karoo restarts. A save follows a step only when the state differs from the last one stored, which comes to once a second while the pair has a value and the ride records, and none while paused; a failed save is tried again at the next step. At Android's shutdown notice one more step is taken with the latest readings, then the state is saved.

Ride boundaries rest on the ride state alone. A change from idle to recording starts a new ride, and idle after a ride was seen active ends it and clears the stored state. When the extension first sees the ride in any other way, such as paused after a boot (the Karoo restores a ride as paused) or recording after the extension restarted, the stored ride continues. After the shutdown notice the state is never cleared. A ride with no stored state starts the pair from the Karoo's values at its first step: near zero for a new ride, and for a field added mid-ride the Karoo's values at that moment. For about 20 s after boot the streams carry no values, so steps begin once time and distance both have a reading.

These fields read the pair:

- Avg Speed (moving), as a field and as a HUD slot
- Moving Time, as a field and as a HUD slot
- Remaining Ride Time, Time to Destination and ETA, as fields and as HUD slots, which take the distance ridden and the time it took from the pair
- the Speed field's color against the moving average, and its 30 s warm-up; the HUD's Speed slot has no average-based color

Distance, Elapsed Time, Paused Time, the lap times and Avg Speed (total) keep reading the Karoo's streams, so Distance stays equal to the Karoo's and to the FIT file.

Two limits are accepted. Time is not counted while the Karoo has neither a speed reading nor moving distance, even if the rider was in fact riding. And since the streams carry no ride identifier, a ride that ends and a new one that starts while the extension is not running look like one ride.

---

## Naming conventions

| Term           | Meaning                                                                    |
| -------------- | -------------------------------------------------------------------------- |
| Field          | The full cell (header + value together)                                    |
| Header         | The top strip inside a field: icon and label                               |
| Icon           | The small glyph at the start of the header                                 |
| Label          | The short all-caps text in the header (1–2 lines)                          |
| Value          | The large primary number displayed below the header                        |
| FieldState     | Runtime data snapshot emitted by a `DataTypeImpl` each tick                |
| ColorConfig    | Derived per-render colors (value text, header text, icon tint, background) |
| ViewSizeConfig | Per-layout sizing constants (see below)                                    |

### ViewSizeConfig

All spacing and sizing constants for one rendering context live in a single [`ViewSizeConfig`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ViewSizeConfig.kt). `ViewConfig.toViewSizeConfig()` produces it, taking an optional `colSpanOverride` and `textSizeOverride` for callers such as HUD slots that need to replace the SDK-provided grid values.

```
Cell level   paddingH
Header       headerIconSize, headerIconLabelGap, headerFontSize, labelMaxLines, headerMinHeightDp
Value        valueFontSizeBase, wrapThresholdSp, valueBitmapHeightDp, valueTranslationDp
```

Presets:

| Preset                    | Use                                                           |
| ------------------------- | ------------------------------------------------------------- |
| `ViewSizeConfig.STANDARD` | Default values; overridden by `toViewSizeConfig()` at runtime |
| `ViewSizeConfig.HUD_THREE`| HUD 3-column slots (colSpan=20), live and config preview      |
| `ViewSizeConfig.HUD_FOUR` | HUD 4-column slots (colSpan=15), live and config preview      |

---

## Value font sizing

The Karoo SDK provides `ViewConfig.textSize`, the recommended value font size in sp, already adjusted for the cell's column and row span. Barberfish passes it straight to `ViewSizeConfig.valueFontSizeBase`, clamped to a minimum of 20 sp:

```kotlin
valueFontSizeBase = textSizeEff.coerceAtLeast(20)
```

The rideapp calibrates this value for the number of characters that fill the cell at full size. In practice:

- 2-column cells (`colSpan = 30`): `textSize` fits roughly 4 wide characters; `"239"` or `"1234"` sit comfortably, a fifth character would start to clip.
- 1-column cells (`colSpan = 60`): `textSize` is proportionally larger and fits roughly 6–7 characters; time values like `"1:23:45"` or `"23m 45s"` fit at or near full size.

For HUD slots the SDK `textSize` does not apply, since each slot fills only a third or a quarter of the cell. `HUDDataType` selects the preset directly:

| HUD columns | Preset                     | `valueFontSizeBase` |
| ----------- | -------------------------- | ------------------- |
| 3-col       | `ViewSizeConfig.HUD_THREE` | 49 sp               |
| 4-col       | `ViewSizeConfig.HUD_FOUR`  | 36 sp               |

### Dynamic shrinking: `fontSizeForCell`

`valueFontSizeBase` is the ceiling, the size a short value gets. For longer strings, [`fontSizeForCell`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/DynamicFontSp.kt) shrinks the font from exact glyph measurements:

```
fontSizeForCell(text, fontSizeBaseSp, cellWidthPx, density, wrapThresholdSp)
```

1. Measure the text at `fontSizeBaseSp` with the paint it is drawn in: `valuePaint` for values, `headerPaint` in capitals for HUD slot labels.
2. If it fits in `cellWidthPx` → return `fontSizeBaseSp` unchanged.
3. Otherwise → scale proportionally: `floor(fontSizeBaseSp × cellWidthPx / measuredWidth)`.
4. If the scaled result drops below `wrapThresholdSp` → attempt 2-line split at the word boundary
   nearest the midpoint; size so the wider half fits.

### `wrapThresholdSp` per layout

| `colSpan` | Layout context | `wrapThresholdSp` |
| --------- | -------------- | ----------------- |
| 60        | 1-column field | 22                |
| 30        | 2-column field | 18                |
| 20        | HUD 3-col slot | 14                |
| 15        | HUD 4-col slot | 12                |

HUD slots take theirs from the `HUD_THREE` and `HUD_FOUR` presets. Both sit at or above the slot's header size, so a slot label that does not fit wraps before it shrinks.

### End-to-end flow

```
ViewConfig.textSize  (SDK, sp, layout-aware)
  │  HUD 3/4-col slots: the HUD_THREE / HUD_FOUR presets instead (49 / 36 sp)
  ▼
ViewSizeConfig.valueFontSizeBase          ← toViewSizeConfig(), or the HUD preset
ViewSizeConfig.wrapThresholdSp            ← derived from colSpan, or the HUD preset

  ▼ at render time, in makeFieldRemoteViews (BarberfishView.kt)
fontSizeForCell(value, valueFontSizeBase, cellWidthPx, density, wrapThresholdSp)
  └─► sp passed to renderValueBitmap, which draws field_value as a bitmap
```

`makeFieldRemoteViews` is the only caller of `fontSizeForCell` for values. Every sizing
parameter arrives through `ViewSizeConfig`; the view layer makes no sizing decisions of its own.

---

## Value baseline alignment

The visible value baseline has to stay put under three things that move around it: `fontSizeForCell` shrinking the text for a long string, the rideapp resizing cells mid-ride without calling `startView` again (rerouting toast, key-icon toggle), and the visible header wrapping to one or two lines.

### Why Barberfish can't copy native directly

Native renders inside a `ConstraintLayout`, which lets a `wrap_content` value view overflow its constraint region symmetrically. The rideapp's RemoteViews allowlist excludes `ConstraintLayout` and `Space`, so Barberfish works inside `RelativeLayout` → `LinearLayout` → `TextView`/`ImageView`, where `wrap_content` cannot overflow the parent.

### Bitmap-rendered value

`field_value` is an `ImageView` showing a `Bitmap` that [`renderValueBitmap`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/BitmapValue.kt) draws at ride time.

- Constant bitmap height per layout: `bitmap_h_px = 0.74 × valueFontBaseSp × density`. Just enough to hold the visible glyph cap (~0.7 × textSize for the `relative` monospace) plus a small buffer.
- Baseline pinned to the bitmap's bottom edge. Digits have no descenders, so bitmap bottom = visible cap bottom = baseline. When `fontSizeForCell` shrinks the text, the smaller glyphs draw inside the same-size bitmap with the baseline in the same place, so a content shrink never moves the baseline.
- `Bitmap.density = DENSITY_NONE`, so the rideapp draws it at native pixel size with no scaling.

### `header_ref` + `baseline_box` centering

The visible `field_header` may wrap to two lines and is allowed to overflow downward via `clipChildren=false`. So that the centering region keeps the same top either way, an invisible `header_ref` `TextView` (`lines=1`, `minHeight` set programmatically) anchors the top of `baseline_box` through `layout_below="@id/header_ref"`.

`header_ref` is shorter than the drawn header in two-line cells (61 px against 73 px in a 2-col 4-row cell), and that is what puts the value in the right place. Native centres a text box with more empty space above its digits than below them, so its digits sit below the box's centre. The value bitmap is tight to the digits, so centring it in a region that starts at the drawn header would land it about 6 px low. The `headerMinHeightDp` formula is tuned so the bitmap lands on native's pixels instead, which it does in every two-column layout (see [Native header and value sizing](sdk-findings.md#native-header-and-value-sizing)). Treat it as a placement constant, not a header height.

Inside `baseline_box`, two `weight=1` `TextView` spacers frame the `field_value` `ImageView` (`Space` would have been the natural choice but is blocked by the allowlist). The 1:1 weights centre the value between header bottom and cell bottom, as native does, and follow the cell when the rideapp shrinks it, with no `cellHeightPx` plumbing.

### Per-layout vertical translation

Native narrow-cell layouts sit the value a little above centre (see [Native header and value sizing](sdk-findings.md#native-header-and-value-sizing)). Barberfish bakes the same shift into per-variant XML (`barberfish_field_neg3.xml`, `barberfish_field_left_neg3.xml`, `barberfish_field_center_neg3.xml`) through `android:translationY="-3dp"` on the `field_value` `ImageView`. `BarberfishView.layoutRes(alignment, translationDp)` selects the `*_neg3` variant when `valueTranslationDp == -3` and the base XML otherwise. Only two values are in use today (`0 dp` and `-3 dp`), so one extra XML variant per alignment is enough.

The runtime `rv.setFloat(R.id.field_value, "setTranslationY", ...)` path is not used: on Karoo 2 `setTranslationY` is not `@RemotableViewMethod` and throws `ActionException` over RemoteViews IPC. An XML attribute is applied by `LayoutInflater` at inflation through direct method dispatch, which never reaches the allowlist. See [RemoteViews methods that crash on Karoo 2](sdk-findings.md#remoteviews-methods-that-crash-on-karoo-2).

### Verification

`scripts/walk_layouts.sh` captures every layout (1×1 through 5×2), screenshotting and dumping view bounds via `dumpsys`. `scripts/measure_alignment.py` parses both and reports per-pair `Δvalue_baseline` (target ±2 px on same-font 2-col paired rows).

```bash
adb shell "dumpsys activity top" | grep -E "field_root|baseline_box|field_value|dataTextView|headerLayout"
```

`uiautomator dump` does not work on Karoo during rides or in the page builder; `dumpsys activity top` does.

---

## Color system

Zone coloring and threshold coloring produce a `FieldColor` sealed variant. `FieldColor.toColorConfig(colorMode, isNightMode)` resolves it into a `ColorConfig` the view layer consumes directly:

- `colorMode = TEXT`: colored text, transparent background
- `colorMode = BACKGROUND`: colored background fill, black text and icon tint
- `colorMode = NONE`: white text, no color

`ColorConfig` carries:
- `valueText: Color`: color for the value (passed to `RemoteViews.setTextColor()` via `.toArgb()`)
- `headerText: Color`: color for the label `TextView`
- `iconTint: Color`: tint applied to the icon `ImageView`
- `background: Color?`: `null` means transparent; non-null fills the cell

### Day/night palette dispatch

`BarberfishView` reads the current system theme from `Configuration.UI_MODE_NIGHT_MASK` and threads an `isNightMode: Boolean` through `toColorConfig`. The flag forwards into `powerZoneColor`, `hrZoneColor`, and `gradeColor`, which pick between the `*ColorsReadableDark` and `*ColorsReadableLight` palette variants so Text-mode fields stay readable on either background.

`colorMode = BACKGROUND` is theme-agnostic: the cell fills with the original brand palette and the overlay text is chosen per cell by [`bestTextOnBackground`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ZoneColoring.kt), whichever of black or white gives the higher APCA `|Lc|` against that specific fill. The contrast methodology is in [Color palettes](color-palettes.md).
