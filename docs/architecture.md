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

### Decimal separator chokepoint

Fields format values with `"%.1f".format(...)`, which follows `Locale.getDefault()`. In a comma-decimal locale (es, de, fr, ...) that gives `9,2%` instead of `9.2%`. Native Karoo fields render a dot in every locale, and the comma is a descender that clips against the digit-tuned value bitmap (issue #6).

So `makeFieldRemoteViews` normalizes the value with `field.primary.replace(',', '.')` into a local `valueText`, which feeds both `fontSizeForCell` and `renderValueBitmap`. Every value passes through this one point, so standalone fields, HUD slots and previews are all covered without per-field changes.

This is safe only because value strings carry no grouping separator (Karoo and Barberfish render `1234 W`, never `1,234`), so the only comma a locale can introduce is the decimal one. If a field ever adopts grouped formatting, replace the chokepoint with locale-fixed formatting at the source (`String.format(Locale.US, ...)`). A new value formatter inherits the dot; there is nothing to add.

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
| `ViewSizeConfig.HUD_THREE`| On-device HUD 3-column slots (colSpan=20)                     |
| `ViewSizeConfig.HUD_FOUR` | On-device HUD 4-column slots (colSpan=15)                     |
| `PREVIEW_HUD_THREE/FOUR`  | Config-screen HUD previews (same sizing, `labelMaxLines = 2`) |

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
| 3-col       | `ViewSizeConfig.HUD_THREE` | 42 sp               |
| 4-col       | `ViewSizeConfig.HUD_FOUR`  | 32 sp               |

### Dynamic shrinking: `fontSizeForCell`

`valueFontSizeBase` is the ceiling, the size a short value gets. For longer strings, [`fontSizeForCell`](../app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/DynamicFontSp.kt) shrinks the font from exact glyph measurements:

```
fontSizeForCell(text, fontSizeBaseSp, cellWidthPx, density, wrapThresholdSp)
```

1. Measure text width at `fontSizeBaseSp` using `Paint.measureText()`.
2. If it fits in `cellWidthPx` → return `fontSizeBaseSp` unchanged.
3. Otherwise → scale proportionally: `floor(fontSizeBaseSp × cellWidthPx / measuredWidth)`.
4. If the scaled result drops below `wrapThresholdSp` → attempt 2-line split at the word boundary
   nearest the midpoint; size to the longer half.

### `wrapThresholdSp` per layout

| `colSpan` | Layout context | `wrapThresholdSp` |
| --------- | -------------- | ----------------- |
| 60        | 1-column field | 22                |
| 30        | 2-column field | 18                |
| 20        | HUD 3-col slot | 14                |
| 15        | HUD 4-col slot | 12                |

### End-to-end flow

```
ViewConfig.textSize  (SDK, sp, layout-aware)
  │  or textSizeOverride (HUD slots: 42 for 3-col, 32 for 4-col)
  ▼
ViewSizeConfig.valueFontSizeBase          ← toViewSizeConfig()
ViewSizeConfig.wrapThresholdSp            ← derived from colSpan

  ▼ at render time, in makeFieldRemoteViews (BarberfishView.kt)
fontSizeForCell(value, valueFontSizeBase, cellWidthPx, density, wrapThresholdSp)
  └─► actual sp applied to field_value TextView via RemoteViews.setTextViewTextSize()
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

The visible `field_header` may wrap to two lines and is allowed to overflow downward via `clipChildren=false`. So that the centering region keeps the same top either way, an invisible `header_ref` `TextView` (`lines=1`, styled like the visible label, `minHeight` set programmatically to match the visible header) anchors the top of `baseline_box` through `layout_below="@id/header_ref"`.

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
