# Barberfish architecture

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
- a Label — short, all-caps, typically one word (POWER, HR, SPEED) but two lines for compound names (AVG SPEED  MOVING)

The Value is the large number below the header. Its font shrinks automatically to fit longer strings (e.g. `00:18` is smaller than `239`).

Barberfish reimplements this anatomy via `RemoteViews` using `barberfish_field.xml`, matching the native look and feel precisely, with added support for zone coloring, variable font sizes, and a three-column HUD.

---

## Rendering entry point

All field rendering goes through a single function in `datatype/BarberfishView.kt`:

```
barberfishFieldRemoteViews(field, alignment, colorMode, sizeConfig, preview, context)
  → RemoteViews (barberfish_field.xml)
      ├── field_header  LinearLayout  (icon + label, wraps content, top of cell)
      │   ├── field_icon   ImageView
      │   └── field_label  TextView
      └── field_value   TextView      (fills remaining space, vertically centered)
```

`barberfishFieldRemoteViews()` receives a `FieldState` and a `ViewSizeConfig`; it has no access to streams, DataStore, or configuration. All sizing decisions are made by the caller before this function is invoked.

Config-screen previews use the same rendering engine: `remoteViewsToBitmap()` in `RemoteViewsBitmap.kt` renders the `RemoteViews` to a `Bitmap` (via `apply/measure/layout/draw`), displayed in Compose via `Image(bitmap.asImageBitmap())`. `ViewSizeConfig.cellWidthPxOverride` allows preview callers to specify the exact cell width in pixels, bypassing the `dm.widthPixels * colSpan / 60` formula used for on-device rendering.

---

## HUD three-column layout

The HUD field uses `barberfish_hud.xml` (3-col) or `barberfish_hud_four.xml` (4-col) — a horizontal `LinearLayout` with equal-weight `FrameLayout` slots. `HUDDataType` populates each slot by calling `barberfishFieldRemoteViews()` with a `colSpanOverride` and `textSizeOverride` that depend on the column count (see *Value font sizing* below), bypassing the SDK's full-cell `textSize`.

```
barberfish_hud.xml (LinearLayout horizontal)
├── hud_slot_left   FrameLayout  (weight=1)
│   └── barberfishFieldRemoteViews(...)
├── hud_slot_middle FrameLayout  (weight=1)
│   └── barberfishFieldRemoteViews(...)
└── hud_slot_right  FrameLayout  (weight=1)
    └── barberfishFieldRemoteViews(...)
```

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
| Field          | The full cell — header + value together                                    |
| Header         | The top strip inside a field: icon and label                               |
| Icon           | The small glyph at the start of the header                                 |
| Label          | The short all-caps text in the header (1–2 lines)                          |
| Value          | The large primary number displayed below the header                        |
| FieldState     | Runtime data snapshot emitted by a `DataTypeImpl` each tick                |
| ColorConfig    | Derived per-render colors (value text, header text, icon tint, background) |
| ViewSizeConfig | Per-layout sizing constants (see below)                                    |

### ViewSizeConfig

All spacing and sizing constants for one rendering context live in a single `ViewSizeConfig` instance. Produced by `ViewConfig.toViewSizeConfig()`, which takes an optional `colSpanOverride` and `textSizeOverride` for callers that need to override the SDK-provided grid values (e.g., HUD slots).

```
Cell level   paddingH
Header       headerIconSize, headerIconLabelGap, headerFontSize, labelMaxLines
Value        valueFontSizeBase, wrapThresholdSp, valueTranslationY
```

One preset ships out of the box:

| Preset                    | Use                                                           |
| ------------------------- | ------------------------------------------------------------- |
| `ViewSizeConfig.STANDARD` | Default values; overridden by `toViewSizeConfig()` at runtime |

---

## Value font sizing

The Karoo SDK provides `ViewConfig.textSize` — the recommended value font size in sp, already adjusted for the cell's column/row span. Barberfish passes it directly to `ViewSizeConfig.valueFontSizeBase` (clamped to a minimum of 20 sp):

```kotlin
valueFontSizeBase = textSizeEff.coerceAtLeast(20)
```

This value is calibrated by the native rideapp for the number of characters that typically fill the cell at full size. In practice:

- 2-column cells (`colSpan = 30`): `textSize` fits roughly 4 wide characters — e.g. `"239"` or `"1234"` sit comfortably; a fifth character would start to clip.
- 1-column cells (`colSpan = 60`): `textSize` is proportionally larger and fits roughly 6–7 characters — e.g. time values like `"1:23:45"` or `"23m 45s"` fit at or near full size.

For HUD slots the SDK `textSize` is meaningless, so `textSizeOverride` is used instead:

| HUD columns | `colSpanOverride` | `textSizeOverride` |
| ----------- | ----------------- | ------------------ |
| 3-col       | 20                | 42 sp              |
| 4-col       | 15                | 37 sp              |

### Dynamic shrinking: `fontSizeForCell`

`valueFontSizeBase` is the *ceiling* — the size used when the value is short. For longer strings, `fontSizeForCell()` shrinks the font using exact glyph measurements:

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
  │  or textSizeOverride (HUD slots: 42 for 3-col, 37 for 4-col)
  ▼
ViewSizeConfig.valueFontSizeBase          ← toViewSizeConfig()
ViewSizeConfig.wrapThresholdSp            ← derived from colSpan

  ▼ at render time, in makeFieldRemoteViews (BarberfishView.kt)
fontSizeForCell(value, valueFontSizeBase, cellWidthPx, density, wrapThresholdSp)
  └─► actual sp applied to field_value TextView via RemoteViews.setTextViewTextSize()
```

`makeFieldRemoteViews` is the only place `fontSizeForCell` is called for values. All sizing
parameters flow in through `ViewSizeConfig`; the view layer makes no sizing decisions of its own.

---

## Color system

Zone coloring and threshold coloring produce a `FieldColor` sealed variant. `FieldColor.toColorConfig(colorMode)` resolves it into a `ColorConfig` that the view layer can consume directly:

- `colorMode = TEXT` — colored text, transparent background
- `colorMode = BACKGROUND` — colored background fill, black text and icon tint
- `colorMode = NONE` — white text, no color

`ColorConfig` carries:
- `valueText: Color` — color for the value `TextView` (`.toArgb()` for `RemoteViews`)
- `headerText: ColorProvider` — color for the label `TextView`
- `iconTint: Color` — tint applied to the icon `ImageView`
- `background: ColorProvider?` — `null` means transparent; non-null fills the cell

The value text is a `Color` (Compose) rather than a `ColorProvider` because `RemoteViews.setTextColor()` requires an `Int` ARGB value, which is only accessible via `Color.toArgb()`.
