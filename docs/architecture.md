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
- a Label: short, all-caps, typically one word (POWER, HR, SPEED) but two lines for compound names (AVG SPEED  MOVING)

The Value is the large number below the header. Its font shrinks automatically to fit longer strings (e.g. `00:18` is smaller than `239`).

Barberfish reimplements this anatomy via `RemoteViews` using three alignment-specific layouts (`barberfish_field.xml` for right, `barberfish_field_left.xml` for left, `barberfish_field_center.xml` for center), matching the native look and feel precisely, with added support for zone coloring, variable font sizes, and a three-column HUD.

---

## Rendering entry point

All field rendering goes through a single function in `datatype/BarberfishView.kt`:

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

Alignment determines the layout file: `barberfish_field.xml` (right), `barberfish_field_left.xml` (left), `barberfish_field_center.xml` (center). Per-layout vertical translation is baked into `*_neg3.xml` variants via `android:translationY` on `field_value` (selected when `valueTranslationDp == -3`). No programmatic `setGravity()` or `setTranslationY()` calls are made; both are blacklisted on K2 (see `docs/karoo2-compatibility.md`).

`barberfishFieldRemoteViews()` receives a `FieldState` and a `ViewSizeConfig`; it has no access to streams, DataStore, or configuration. All sizing decisions are made by the caller before this function is invoked.

Config-screen previews use the same rendering engine: `remoteViewsToBitmap()` in `RemoteViewsBitmap.kt` renders the `RemoteViews` to a `Bitmap` (via `apply/measure/layout/draw`), displayed in Compose via `Image(bitmap.asImageBitmap())`. `ViewSizeConfig.cellWidthPxOverride` allows preview callers to specify the exact cell width in pixels, bypassing the `dm.widthPixels * colSpan / 60` formula used for on-device rendering.

### Decimal separator chokepoint

Fields format values with `"%.1f".format(...)`, which uses `Locale.getDefault()`. In comma-decimal locales (es, de, fr, ...) that produces `9,2%` instead of `9.2%`. The native Karoo fields always render a dot regardless of locale, and the comma is a descender that clips against the digit-tuned value bitmap (issue #6).

So `makeFieldRemoteViews` normalizes the value with `field.primary.replace(',', '.')` into a local `valueText`, used for both `fontSizeForCell` and `renderValueBitmap`. This is the single point every value flows through, so it covers standalone fields, HUD slots, and previews without per-field changes.

This is safe only because value strings carry no grouping separator (Karoo and Barberfish render `1234 W`, never `1,234`), so the only comma a locale can introduce is the decimal separator. If a field ever adopts grouped formatting, replace this with locale-fixed formatting (`String.format(Locale.US, ...)`) at the source instead. Adding a new value formatter? It inherits the dot automatically; nothing to do.

---

## HUD three-column layout

The HUD field uses `barberfish_hud.xml` (3-col) or `barberfish_hud_four.xml` (4-col), a horizontal `LinearLayout` with equal-weight `FrameLayout` slots. `HUDDataType` populates each slot by calling `barberfishFieldRemoteViews()` with a `colSpanOverride` and `textSizeOverride` that depend on the column count (see *Value font sizing* below), bypassing the SDK's full-cell `textSize`.

```
barberfish_hud.xml (LinearLayout horizontal)
├── hud_slot_left   FrameLayout  (weight=1)
│   └── barberfishFieldRemoteViews(...)
├── hud_slot_middle FrameLayout  (weight=1)
│   └── barberfishFieldRemoteViews(...)
└── hud_slot_right  FrameLayout  (weight=1)
    └── barberfishFieldRemoteViews(...)
```

The 4-col variant `barberfish_hud_four.xml` follows the same shape and adds a fourth `hud_slot_fourth` `FrameLayout`.

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

All spacing and sizing constants for one rendering context live in a single `ViewSizeConfig` instance. Produced by `ViewConfig.toViewSizeConfig()`, which takes an optional `colSpanOverride` and `textSizeOverride` for callers that need to override the SDK-provided grid values (e.g., HUD slots).

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

The Karoo SDK provides `ViewConfig.textSize`, the recommended value font size in sp, already adjusted for the cell's column/row span. Barberfish passes it directly to `ViewSizeConfig.valueFontSizeBase` (clamped to a minimum of 20 sp):

```kotlin
valueFontSizeBase = textSizeEff.coerceAtLeast(20)
```

This value is calibrated by the native rideapp for the number of characters that typically fill the cell at full size. In practice:

- 2-column cells (`colSpan = 30`): `textSize` fits roughly 4 wide characters; e.g. `"239"` or `"1234"` sit comfortably, a fifth character would start to clip.
- 1-column cells (`colSpan = 60`): `textSize` is proportionally larger and fits roughly 6–7 characters; e.g. time values like `"1:23:45"` or `"23m 45s"` fit at or near full size.

For HUD slots the SDK `textSize` is meaningless, since each slot fills only a third or quarter of the cell. `HUDDataType` selects the preset directly:

| HUD columns | Preset                     | `valueFontSizeBase` |
| ----------- | -------------------------- | ------------------- |
| 3-col       | `ViewSizeConfig.HUD_THREE` | 42 sp               |
| 4-col       | `ViewSizeConfig.HUD_FOUR`  | 32 sp               |

### Dynamic shrinking: `fontSizeForCell`

`valueFontSizeBase` is the *ceiling*, the size used when the value is short. For longer strings, `fontSizeForCell()` shrinks the font using exact glyph measurements:

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

`makeFieldRemoteViews` is the only place `fontSizeForCell` is called for values. All sizing
parameters flow in through `ViewSizeConfig`; the view layer makes no sizing decisions of its own.

---

## Value baseline alignment

Goal: keep the visible value baseline stable regardless of (a) `fontSizeForCell` shrinking the text for long strings, (b) the rideapp resizing cells mid-ride without re-calling `startView` (rerouting toast, key-icon toggle), and (c) the visible header wrapping to one or two lines.

### Why Barberfish can't copy native directly

Native renders inside a `ConstraintLayout`, which lets a `wrap_content` value view overflow its constraint region symmetrically. The Karoo ride app's RemoteViews allowlist excludes `ConstraintLayout` and `Space`, so Barberfish works inside `RelativeLayout` → `LinearLayout` → `TextView`/`ImageView`, where `wrap_content` cannot overflow the parent.

### Bitmap-rendered value

`field_value` is an `ImageView` displaying a `Bitmap` rendered at ride time by `renderValueBitmap()` in `shared/BitmapValue.kt`.

- Constant bitmap height per layout: `bitmap_h_px = 0.74 × valueFontBaseSp × density`. Just enough to hold the visible glyph cap (~0.7 × textSize for the `relative` monospace) plus a small buffer.
- Baseline pinned to the bitmap's bottom edge. Digits have no descenders, so bitmap bottom = visible cap bottom = baseline. When `fontSizeForCell` shrinks the text, the smaller glyphs draw inside the same-size bitmap with the baseline at the same position, so content shrinks don't move the baseline.
- `Bitmap.density = DENSITY_NONE` so the rideapp renders at native pixel size with no scaling.

### `header_ref` + `baseline_box` centering

The visible `field_header` may wrap to two lines and is allowed to overflow downward via `clipChildren=false`. To keep the centering region top stable regardless, an invisible `header_ref` `TextView` (`lines=1`, mirrors `dataHeaderTextStyle`, `minHeight` set programmatically to match the visible header) anchors `baseline_box`'s top via `layout_below="@id/header_ref"`.

Inside `baseline_box`, two `weight=1` `TextView` spacers frame the `field_value` `ImageView` (`Space` would have been the natural choice but is blocked by the allowlist). The 1:1 weights geometrically reproduce native's `bias=0.5` centering between header bottom and cell bottom, and adapt automatically when the rideapp shrinks the cell (no `cellHeightPx` plumbing).

### Per-layout vertical translation

Mirrors the small upward translation observed in native narrow-cell layouts (see `docs/sdk-findings.md` § "Native header and value sizing"). Baked into per-variant XML (`barberfish_field_neg3.xml`, `barberfish_field_left_neg3.xml`, `barberfish_field_center_neg3.xml`) via `android:translationY="-3dp"` on the `field_value` `ImageView`. `BarberfishView.layoutRes(alignment, translationDp)` selects the `*_neg3` variant when `valueTranslationDp == -3`, the base XML otherwise. Only two distinct values are in use today (`0 dp` and `-3 dp`), so only one extra XML variant per alignment is needed.

The runtime `rv.setFloat(R.id.field_value, "setTranslationY", ...)` path is not used. `setTranslationY` is not `@RemotableViewMethod` on K2 (API 27) and throws `ActionException` over RemoteViews IPC. XML attributes are processed at inflation by `LayoutInflater` via direct method dispatch, bypassing the allowlist. See `docs/karoo2-compatibility.md`.

### Verification

`scripts/walk_layouts.sh` captures every layout (1×1 through 5×2), screenshotting and dumping view bounds via `dumpsys`. `scripts/measure_alignment.py` parses both and reports per-pair `Δvalue_baseline` (target ±2 px on same-font 2-col paired rows).

```bash
adb shell "dumpsys activity top" | grep -E "field_root|baseline_box|field_value|dataTextView|headerLayout"
```

`uiautomator dump` does not work on Karoo during rides or in the page builder; `dumpsys activity top` works reliably.

---

## Color system

Zone coloring and threshold coloring produce a `FieldColor` sealed variant. `FieldColor.toColorConfig(colorMode, isNightMode)` resolves it into a `ColorConfig` that the view layer can consume directly:

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

`colorMode = BACKGROUND` is theme-agnostic: the cell fills with the original brand palette and the overlay text is chosen per cell by `bestTextOnBackground`, whichever of black or white gives the higher APCA `|Lc|` against that specific fill. See `docs/color-palettes.md` for the full contrast methodology.

## Grade map overlay

The grade map paints the route on the map page as a band coloured by grade, with white
direction chevrons on top. It replaces the native route line rather than decorating it:
extension polylines draw above the native line and its direction chevrons, and nothing an
extension draws can go underneath, so a band narrower than the native chevrons leaves their
wings poking out either side. The band is therefore 18 dp wide, enough to cover them, inside a
21 dp black casing that keeps its edge crisp over water, parks and buildings. Widths are dp;
the map applies them as they are.

The band tiles the whole route. Emphasis decides which runs take a grade colour; runs inside
the emphasis edges draw in the palette's neutral at the same width, so the band never changes
width along the route and the native line stays covered end to end. Adjacent runs are separate
polylines with round caps, so at a colour change the downstream run's cap sits as a small nose
on the upstream run. Layer order within one depth is whatever order the map processed the batch
in; the nose is a few pixels and reads the same either way.

Each piece has its own black casing beneath it, with the same extent. Getting casings beneath
fills, and earlier visits above later ones, depends on two rules the map applies: a new id is
added above everything the extension has drawn, and an update to an existing id keeps its
place. A batch is not processed in emission order, so a stacking pass hides everything already
painted, waits, puts every casing down, waits, then shows the fills one depth at a time from the
bottom, waiting between depths. After that, updates are in place and need no waiting. An id new
to the map, or a painted id whose depth changed, triggers the stacking pass again; extent and
owner changes update in place, because pieces of one depth never share ground and an update
keeps a layer's position. A route with no repeated ground therefore never restacks after its
first draw. On a repeated route a zoom re-cut coarser than about zoom 15 shifts positional ids,
and an id that moves across a depth boundary restacks. `docs/sdk-findings.md` records the
measurements behind this.

Ground the route covers more than once is handled by a route index built once per route. A
matcher finds GPS edges whose endpoints agree within 1.5 m, in either direction, and groups
them. Each group is a unit of ground with a stack of visits in ride order; ground covered once
is a unit with a single visit. Colour runs are cut at visit boundaries into pieces, and a piece
inherits its visit's depth: one plus the number of later visits to the same ground, so the
first pass draws on top and the last at the bottom. Removing a ridden visit exposes the next,
a last-in-first-out stack at each shared stretch.

Visibility follows the rider's accepted progress, rescaled from the navigation distance onto
the polyline's own axis. On a visit with no later pass over its ground, whether the ground is
covered once or this is the last pass over a repeated stretch, a piece hides as progress passes
its end and the piece under the rider is re-cut to start at progress, so the native grey trace
trails the rider by one progress bucket. A ridden visit with a later pass beneath it is retained
while it remains in view, so the colours behind the rider do not flip to the return leg's; it
hides once the rider is more than a screen radius from it, or once its next visit starts within
that radius of route distance ahead, which on a return leg is the moment the ground enters the
screen. Chevrons are generated for the whole route without collision filtering and selected at
draw time: a mark is drawn only on the exposed visit of its ground, and collisions are resolved
among the drawable marks, so a return-leg mark suppressed by an outbound mark appears once the
outbound visit hides. Same-direction laps look identical on every pass; there the only visible
effect is the grey trace after the last lap.

Chevrons carry direction only; grade stays in the band. Each palette draws its chevron in its
own yellow climb band colour with a black outline, 24 by 16 dp, so the glyph reads as the
palette's own; on the yellow band itself only the outline shows, as the native chevron does on
the native yellow line, and the outline is what carries it there. HSLuv has no yellow and takes
the Barberfish yellow. One drawable per yellow, six in all, mapped from the palette in
`ChevronDrawables.kt`. A palette change re-shows every chevron in place with the new icon,
without a hide, since a hide and a show for one id in the same batch race on the map. Chevron
cadence tightens with grade and grade change and is documented with the placement code.

The grade map is one switch. Band and chevrons are a single design: the band covers the native
chevrons, so chevrons without it would sit on nothing, and the band without chevrons has no
direction cue.

Off route, the navigation state carries the path back to the route as its own polyline. The
overlay draws it as a second band of the same widths in the map's rerouting red, chevrons in the
same red at the route's sparse cadence, since there is no grade to vary them with. It has its own
ids, its own casing under the same first-emit rule, and clears when the path goes away. The
rejoin polyline's hash is part of the rebuild signature, so a new reroute redraws it.

## Wind sock

Barberfish reads wind from the Headwind extension (`karoo-headwind`) through the
same stream helper every field uses, with extension-qualified ids
(`TYPE_EXT::karoo-headwind::windDirection` and so on). The rideapp serves any
extension's streams to any other, and the Headwind README invites it.

One glyph, a windsock seen from above, serves two surfaces. Its geometry lives
in `WindSockGeometry`; the five drawables are generated from the same numbers by
`scripts/gen_wind_sock_drawables.py`, and `WindSockDrawablesTest` pins the two.

On the map, `WindSockController` keeps one symbol on a mast 53 dp ahead of the
puck along the course, oriented to the absolute direction the wind blows toward.
The map rotates symbols with itself, so the same bearing reads relative to the
rider on a heading-up map and true on a north-up map; the rideapp does not
expose which mode is active, and absolute is the choice that is right on a map
in both. Calm hides the symbol.

In the `Wind` field the sock is composed into the value bitmap by
`renderWindSockValueBitmap`, rotated by the rider-relative angle about its own
midpoint, in a box the height of the value; the number takes the remaining width
through the usual `fontSizeForCell` shrink. Strength follows the airfield rule,
one band per 3 knots, five at most. Speed arrives in the Headwind extension's
configured unit, which Barberfish cannot read; it assumes that extension's
default for the Karoo profile (km/h or mph).
