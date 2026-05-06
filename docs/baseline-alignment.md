# Value baseline alignment

Relates to [#2](https://github.com/jpweytjens/Barberfish/issues/2).

## Goal

Consistent value baseline position for a given grid size, independent of:

- Cell height (survives rerouting toast and key-icon toggles that shrink
  cells mid-ride without re-calling `startView`).
- Font size (values shrunk by `fontSizeForCell` line up with unshrunk
  values in neighbouring cells).

## Native approach (decompiled rideapp)

Native uses a `ConstraintLayout` (`data_element_single.xml`) with the
value view (`dataTextView`) constrained between `headerLayout` bottom and
parent bottom (default bias 0.5). Per-layout sizing comes from
`DataElementConstraints` (rideapp `hhv5/d.hha()`), which returns:

- `dataSize` — value font px
- `dataTranslationY` — per-layout vertical shift, applied at runtime via
  `dataTextView.setTranslationY(...)` (`hhu5/f.java:48`)
- `labelSize`, `labelLineSpacingMultiplier`, etc.

`ConstraintLayout` allows `wrap_content` views to overflow constraints
symmetrically, so the value view's line metrics (1.2 × textSize) extend
above the cap and below the descent without affecting the centering axis.

## Why we can't copy native directly

The Karoo rideapp's RemoteViews allowlist excludes `ConstraintLayout` and
`Space`. Our value rendering lives inside a `RelativeLayout` →
`LinearLayout`, where a `wrap_content` view cannot overflow its parent
the way ConstraintLayout permits.

## Current approach — `ImageView` + `Bitmap`

`field_value` is an `ImageView` displaying a `Bitmap` rendered at ride
time by `renderValueBitmap()` in `BitmapValue.kt`. The bitmap is sized to
exactly the visible cap region (no line-metric padding) so it fits inside
even the tightest `baseline_box` (5×1) without `ImageView.fitCenter`
silently downscaling.

```
field_root (RelativeLayout, clipChildren=false)
├── stream_state_tv (match_parent, GONE by default)
├── header_ref (wrap_content, alignParentTop, lines=1, invisible.
│               Anchors baseline_box's top via layout_below.)
├── field_header (wrap_content, alignParentTop, overlaps header_ref;
│                 may overflow downward via clipChildren=false)
└── baseline_box (LinearLayout vertical, layout_below=header_ref,
                  alignParentBottom, match_parent, clipChildren=false)
    ├── TextView (height=0dp, weight=1)   — top spacer
    ├── ImageView field_value (wrap_content, weight=0)
    └── TextView (height=0dp, weight=1)   — bottom spacer
```

The two `weight=1` `TextView`s (used because `Space` is blocked)
geometrically reproduce native's bias-0.5 centering between
`headerLayout` bottom and cell bottom: leftover space is split 50/50
around the bitmap. The centering region adapts automatically when the
rideapp shrinks the cell — no `cellHeightPx` plumbing needed.

### Bitmap geometry

```
bitmap_h_px = 0.74 × valueFontBaseSp × density
```

`0.74` is just enough to hold the visible glyph cap (~0.7 × textSize for
Karoo's `relative` monospace) plus a small buffer above. Baseline is
pinned to the bitmap's bottom edge — for digits (no descenders) that is
also the visible bottom. `bitmap.density = Bitmap.DENSITY_NONE` so
RemoteViews displays at native pixel size with no scaling.

The height is a **constant function of `valueFontBaseSp`**, not the
per-render `fontSizeForCell` shrunk size. When content forces a font
shrink, the smaller text is drawn inside the same-size bitmap with the
baseline still pinned to bitmap bottom — so the baseline stays at the
same position across short and long content.

### Per-layout vertical alignment

Mirrors native's `DataElementConstraints.dataTranslationY`. Applied
paint-time via:

```kotlin
rv.setFloat(R.id.field_value, "setTranslationY", translationYPx)
```

`View.setTranslationY` is `@RemotableViewMethod` since API 21 (works on
both K2 and K3). Paint-time transform — does not affect layout, so the
bitmap stays inside `baseline_box` regardless of translation magnitude.

Per-layout values in `ViewSizeConfig.valueTranslationDp` were predicted
from bitmap-centre maths and refined with `scripts/measure_alignment.py`.

### Header anchor

`header_ref` (invisible, `lines=1`, styled to mirror
`dataHeaderTextStyle`) anchors `baseline_box`'s top via
`layout_below="@id/header_ref"`. Its programmatic `minHeight` is content-
driven from `labelSp` and `labelMaxLines`, mirroring native's
`headerLayout` height. The visible `field_header` (which may wrap to 2
lines) overlaps via `layout_alignParentTop`, and any overflow into
`baseline_box` is absorbed by `clipChildren=false`. The centering region
top is therefore independent of how many lines the visible header
actually wraps to.

### Per-layout label sp

Lookup in `ViewSizeConfig.toViewSizeConfig` mirrors native's
`DataElementConstraints` lookup keyed on `(colSpan, rowSpan)`. Driving
the same `labelSp` per layout makes our `header_ref` height equal
native's `headerLayout` height, which in turn makes our centering region
geometrically equivalent to native's.

## Verification

`scripts/walk_layouts.sh` captures every layout (1×1 through 5×2),
including dumpsys for view bounds and a screencap. `scripts/measure_alignment.py`
parses both, paying attention to:

- Same-font 2-col paired rows: `Δvalue_baseline` should be within ±2 px.
- Single-col 5×1 cells: bitmap height should match design (76 px) and
  fit inside `baseline_box` (~79 px on K3 with key bar).

## Debugging layout bounds

```bash
adb shell "dumpsys activity top" | grep -E "field_root|baseline_box|field_value|dataTextView|headerLayout"
```

Example (5×1 cell):

```
field_root:    7,0-471,143      cell: 464×143 px
field_header:  7,0-471,48       header: 48 px
baseline_box:  7,48-471,143     centering region: 95 px
field_value:   276,10-464,85    bitmap: 188×75 px (right-aligned)
```

`uiautomator dump` does not work on Karoo during rides or the page
builder; `dumpsys activity top` works reliably.

## Native style attribute mapping

Source: `docs/ride_decompiled/resources/res/values/styles.xml`.

### `dataHeaderTextStyle` (header label, `styles.xml:3655`)

| Attribute | Native | Our `field_label` | Mirror? |
|---|---|---|---|
| `textSize` | per-layout via `DataElementConstraints` | dynamic per `ViewSizeConfig.headerFontSize` | ✓ |
| `textColor` | `elementViewHeaderColor` | set in code | ✓ |
| `ellipsize` | end | end | ✓ |
| `gravity` | `center_vertical \| end` | matches per variant | ✓ |
| `lines` | 2 | 2 (forced via `setLines(2)`) | ✓ |
| `includeFontPadding` | false | false | ✓ |
| `lineSpacingMultiplier` | 0.6 (narrow cells) | 0.6 | ✓ |
| `textAllCaps` | true | true | ✓ |
| `fontFamily` | `ibm-plex-sans-condensed` | `ibm-plex-sans-condensed` | ✓ |
| `layout_marginStart` | 1 dp | 1 dp | ✓ |
| `breakStrategy` | simple (0) | simple | ✓ |
| `textAlignment` | textStart | **deliberately unset** | ✗ (see below) |

### `dataHeader` (header container, `styles.xml:3646`)

| Attribute | Native | Our `field_header` | Mirror? |
|---|---|---|---|
| `clipChildren` | false | inherited from root | ✓ |
| `clipToPadding` | false | false | ✓ |
| `minHeight` | 22 dp | content-driven via `headerMinHeightDp` (= max(26 dp, label band)) | ≈ |
| `paddingStart` / `paddingEnd` | 1 dp | 1 dp | ✓ |

### `singleNumericDataStyle` (value, `styles.xml:3714`)

| Attribute | Native | Our `field_value` (bitmap render) | Mirror? |
|---|---|---|---|
| `fontFamily` | `relative` | `Typeface.create("relative", NORMAL)` in `Paint` | ✓ |
| `letterSpacing` | -0.04 | -0.04 | ✓ |
| `gravity` | `center_vertical \| end` | bitmap drawn at requested align; centering via LinearLayout weights | ≈ |
| `includeFontPadding` | false | n/a (no TextView padding involved) | n/a |

### Why we don't mirror `textAlignment`

Native's `dataHeaderTextStyle` sets `textAlignment="textStart"` but
positions the TextView via `ConstraintLayout` `horizontal_bias=1`. Our
`LinearLayout` + `layout_weight=1` makes the TextView fill all
horizontal space, so we rely on `gravity=end` for the visible right-edge
hug. `textAlignment` overrides `gravity` for text positioning on API
17+, so leaving it unset is required.

## K2 compatibility

- Layout attributes (`layout_below`, `layout_alignParentBottom`,
  `layout_weight`, `gravity`) are XML-baked, processed at inflation —
  unaffected by K2's runtime restrictions.
- `setImageViewBitmap` and `setFloat(viewId, "setTranslationY", v)` are
  standard `@RemotableViewMethod` since API 21.
- `setTextViewTextSize`, `setViewPadding`, `setMinimumHeight` are
  standard RemoteViews methods.

## Stream state rendering

`stream_state_tv` (Searching / Not available / Idle) is a separate
overlay TextView, shown when `field.color is FieldColor.StreamState`.
It uses `gravity="center_vertical|{end,start,center}"` with `paddingTop`
set to `headerHeightPx` so it sits below the header. `field_value` is
hidden (GONE) in this case.

## Historical — abandoned approaches

> Kept as a reasoning trail. Current implementation uses bitmap rendering
> as described above; the approaches below are no longer load-bearing.

- **`baseline_ref` + `layout_alignBaseline` + `layout_centerVertical`:**
  invisible reference TextView centred in `baseline_box` with
  `field_value` baseline-locked to it. Worked well at moderate font sizes
  but Android's RelativeLayout clipped `baseline_ref` to its parent when
  `ref_view_h > box_h` (5×1, 5×2 with key bar), collapsing the centering
  geometry. A clip-safe `baselineRefSp` cap mitigated it but couldn't
  match native pixel-for-pixel on tight layouts.
- **`gravity="bottom"` + computed `refFontSp` from cellHeight arithmetic:**
  required `statusBarHeight + navBarHeight` to derive `cellHeightPx`;
  stale after key-icon toggles and rerouting toast.
- **`gravity="bottom"` with `valueFontSizeBase`:** zero downward
  overflow but values sat too high; the descent gap below digits was
  visually prominent.
- **Asymmetric bitmap padding** (early bitmap iteration): translation
  was baked into the bitmap by adding `2N` rows of padding on the
  opposite side. Worked geometrically but inflated the bitmap, causing
  `ImageView.fitCenter` to silently downscale on tight 1-col layouts.
  Replaced by paint-time `setTranslationY`.
