# Value baseline alignment

Relates to [#2](https://github.com/jpweytjens/Barberfish/issues/2).

## Goal

Consistent value baseline position for a given grid size, independent of cell
height (survives rerouting toast and key-icon toggles that shrink cells mid-ride)
and independent of font size (values shrunk by `fontSizeForCell` line up with
unshrunk values in neighbouring cells).

## Native approach (from decompiled data_element_single.xml)

```xml
<AppCompatTextView
    android:id="@+id/dataTextView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintTop_toBottomOf="@+id/headerLayout"
    style="@style/singleNumericDataStyle"/>
```

Native uses ConstraintLayout with `wrap_content` height and opposing top/bottom
constraints. In ConstraintLayout, this means: size to content, center between
constraints (default bias 0.5). The view can be LARGER than the constraint space.

Verified via `dumpsys activity top` on a 1x3 page:
```
dataElementRoot:  1,0-479,190      cell: 478x190px
headerLayout:     0,0-478,48       header: 48px
dataTextView:     0,24-470,214     value: 190px tall, extends 24px into header
                                   and 24px past cell bottom
```

The value view is the same height as the cell (190px) even though the constraint
space is only 142px (190-48). ConstraintLayout allows `wrap_content` views to
overflow constraints symmetrically. The centering AXIS is between headerBottom
and cellBottom; the VIEW extends equally past both.

For the SDK extension container (`data_element_sdk.xml`), native adds extra room:
`translationY="-15dp"` shifts the container up, and a 25dp Space below the cell
extends the bottom. This gives SDK views ~40dp of extra centering room beyond cell
bounds. This container is for non-RemoteViews SDK views only — our RemoteViews are
in a separate FrameLayout that fills the cell exactly.

## Why we can't copy native

RemoteViews cannot use ConstraintLayout. Our views live in a RelativeLayout inside
the rideapp's FrameLayout. The fundamental constraint:

In ConstraintLayout, a `wrap_content` view between opposing constraints can be
LARGER than the space and is centered between the constraints. In RelativeLayout,
views fill or fit within their parent — they cannot have bounds larger than their
container. `layout_centerVertical` stretches a `wrap_content` view to fill the
parent, then `gravity=center_vertical` centers the text within the stretched view.
The geometric centering is the same, but the view bounds differ.

The rideapp's FrameLayout wrapper clips overflow at the cell boundary. Native
fields (direct ConstraintLayout children) have more forgiving clipping. When the
value font is larger than the centering space, native's overflow is visible while
ours gets clipped at the cell bottom.

### Approaches tried and abandoned

- `gravity="bottom"` + computed `refFontSp` from cellHeight arithmetic:
  required reading `statusBarHeight` + `navBarHeight` to derive `cellHeightPx`.
  Stale after key-icon toggles and rerouting toast (no `startView` re-call).

- `gravity="center_vertical"` on baseline_ref directly in field_root:
  `match_parent` height with `layout_below` offset created measurement ambiguity —
  `gravity` may center in the measured height (full cell) rather than the
  constrained height (below spacer). Values shifted when key icons toggled.

- `gravity="bottom"` with `valueFontSizeBase`: zero downward overflow but
  values sat too high (the descent gap below digits was visually prominent).

- Capped reference font from `viewSize.second`: `viewSize` is stale after
  cell resize (same as cellHeight arithmetic).

- Computed reference font `R = (H/2 - 0.256*F) / 0.244` matching native centering:
  correct math but required `viewSize` and didn't adapt to dynamic resizes.

- Scaling `valueFontSizeBase` by a fixed factor (e.g. 0.85): linear tradeoff
  between centering accuracy and descent overflow. No single scale worked for
  all layouts.

## Current approach — nested baseline_box + per-layout ref font

```
field_root (RelativeLayout, clipChildren=false)
├── stream_state_tv (match_parent, GONE by default)
├── header_spacer (22dp, alignParentTop, invisible)
├── field_header (wrap_content, alignParentTop, draws on top via z-order)
└── baseline_box (RelativeLayout, layout_below=spacer, alignParentBottom, clipChildren=false)
    ├── baseline_ref (1px, wrap_content, layout_centerVertical, gravity=center_vertical,
    │                 includeFontPadding=true, invisible. Font: baselineRefSp from lookup)
    └── field_value (match_parent, wrap_content, alignBaseline=baseline_ref)
```

### How it works

`baseline_box` fills from `header_spacer` bottom to cell bottom — a nested
RelativeLayout that provides correctly-measured bounds for centering.
`layout_centerVertical` on `baseline_ref` inside this box centers the reference
view within the box's actual laid-out height (no measurement ambiguity from the
outer RelativeLayout). `gravity=center_vertical` centers the text within the
(possibly stretched) view. `includeFontPadding=true` adds extra ascent/descent
padding, making the `wrap_content` view taller and giving the centering more range.

`field_value.alignBaseline` locks the value's baseline to the reference's baseline.
When `fontSizeForCell` shrinks the value, the baseline stays locked.

### Per-layout tuning via baselineRefSp

`baselineRefSp` in `ViewSizeConfig` is a per-`(colSpan, rowSpan)` lookup that
controls the reference font size. Larger ref → taller reference → baseline
shifts up → value appears higher. Smaller ref → baseline shifts down → value
appears lower. Seeded from `valueFontSizeBase` (native's font) and tuned visually.

### Why the spacer height matters

The spacer (22dp) acts as our substitute for native's `translationY="-15dp"`.
Native shifts the SDK container UP, giving the value more centering room above.
We can't use `translationY` (blocked on K2). Instead, the spacer defines where
the centering box starts — a smaller spacer moves the center up (value more
centered but overlaps header more), a larger spacer moves it down (less overlap
but values sit lower).

22dp matches native's `DataHeaderView minHeight`. This is a compromise: it
prevents most header overlap while keeping the centering box large enough.

### Monospace font metrics (Karoo 3)

```
ascent  = 0.756 * fontSize   (baseline to glyph top)
descent = 0.244 * fontSize   (baseline to glyph bottom)
total   = 1.000 * fontSize
```

When `gravity=center_vertical` centers text in a box:
```
baseline_from_bottom = box/2 - 0.256 * fontSize
```

The `0.256 = ascent_ratio - 0.5` is the asymmetry — digits are top-heavy
(more ascent than descent), so centering shifts the baseline below the box
midpoint. For numeric content without descenders, the visible digit bottom
(at the baseline) is `0.244 * fontSize` above the descent bottom.

### Cell resize behavior

When key-icon toggles or rerouting toast shrink cells mid-ride:
1. `baseline_box` (`layout_below` + `alignParentBottom`) re-sizes automatically
2. `layout_centerVertical` re-centers `baseline_ref` in the new box
3. `field_value.alignBaseline` follows

No `startView` re-call needed. No `viewSize` dependency. The `baselineRefSp`
is fixed (set once per render), so the centering shifts slightly from the ideal
position for the new cell height. But the layout engine handles the reflow —
overflow stays upward-biased into the header zone (`clipChildren=false`).

### Remaining limitation

When the value font is larger than the centering box (common for 5-row cells
with key icons ON), the descent extends past the cell bottom. The rideapp's
FrameLayout clips this overflow. Native's ConstraintLayout avoids this because
the value VIEW can be larger than the constraint space — the overflow is
"owned" by the ConstraintLayout and not clipped by a wrapper.

This is an inherent limitation of RemoteViews in a FrameLayout container.
The `baselineRefSp` lookup mitigates it by allowing per-layout tuning of
the baseline position.

## Debugging layout bounds

Use `dumpsys activity top` to inspect actual view bounds on-device:

```bash
adb shell "dumpsys activity top" | grep -E "field_root|baseline_box|baseline_ref|field_value|header_spacer|dataTextView|headerLayout"
```

Example output (5x2 layout):
```
field_root:    0,0-238,126      → cell: 238x126px
header_spacer: 7,0-8,41         → 22dp = 41px
baseline_box:  7,41-231,126     → centering box: 224x85px
baseline_ref:  0,0-1,85         → fills box (layout_centerVertical stretches)
field_value:   0,0-224,85       → fills box, baseline-aligned to ref
```

Compare with native fields in the same dump to verify baseline positions.

`uiautomator dump` does not work on Karoo during rides or the page builder
(fails with "could not get idle state" even with animations disabled).
`dumpsys activity top` works reliably.

## Native style attribute mapping

The decompiled rideapp resources at
`docs/ride_decompiled/resources/res/values/styles.xml` give us the canonical
attribute values for every text style the native field uses. Our XML mirrors
these where possible. This is the source of truth for any attribute we copy.

### `dataHeaderTextStyle` (header label TextView, `styles.xml:3655`)

| Attribute | Native value | Our `field_label` | Mirror? |
|---|---|---|---|
| `textSize` | 17sp (default, overridden by code per `(colSpan, rowSpan)` lookup) | dynamic per `ViewSizeConfig.headerFontSize` | ✓ |
| `textColor` | `elementViewHeaderColor` | set in code | ✓ |
| `ellipsize` | end | end | ✓ |
| `gravity` | `center_vertical \| end` | matches per variant | ✓ |
| `lines` | 2 | 2 | ✓ |
| `includeFontPadding` | false | false | ✓ |
| `lineSpacingMultiplier` | 0.7 | 0.7 | ✓ |
| `textAllCaps` | true | true | ✓ |
| `fontFamily` | `@string/commonmodule_data_label_font` = `ibm-plex-sans-condensed` | `ibm-plex-sans-condensed` | ✓ |
| `layout_marginStart` | 1dp | 1dp | ✓ |
| `breakStrategy` | simple (0) | simple | ✓ |
| `textAlignment` | textStart (1) | **deliberately unset** | ✗ (see below) |

### `dataHeader` (header container, `styles.xml:3646`)

| Attribute | Native value | Our `field_header` | Mirror? |
|---|---|---|---|
| `clipChildren` | false | inherited from root (`clipChildren=false`) | ✓ |
| `clipToPadding` | false | false | ✓ |
| `minHeight` | 22dp | 22dp (constant in `ViewSizeConfig.headerMinHeightDp`; the label's `lines=2` lets the header grow naturally for narrow layouts) | ✓ |
| `paddingStart` / `paddingEnd` | 1dp | 1dp | ✓ |

### `singleNumericDataStyle` (value TextView, `styles.xml:3714`)

| Attribute | Native value | Our `field_value` | Mirror? |
|---|---|---|---|
| `gravity` | `center_vertical \| end` | end (vertical handled via `alignBaseline=baseline_ref`) | functionally ✓ |
| `maxLines` | 1 | 1 | ✓ |
| `includeFontPadding` | false | false | ✓ |
| `fontFamily` | `@string/commonmodule_monospace_font` = `relative` | `relative` (Karoo system font; falls back to default monospace if absent) | ✓ |
| `letterSpacing` | -0.04 | -0.04 | ✓ |
| `baselineAligned` | false | false | ✓ |

### Why we don't mirror `textAlignment`

Native's `dataHeaderTextStyle` sets `textAlignment="textStart"` (= 1). We
tried mirroring it and it broke right-alignment on every header label —
the metric tool showed labels visibly left-aligned in their cells. The
reason is a layout-engine difference, not a style bug:

- **Native** places `headerTextView` inside a `ConstraintLayout` with
  `layout_width=wrap_content` and `horizontal_bias=1` (right-biased).
  The TextView itself is sized to the text, and its horizontal position
  inside the parent is set by the constraint bias. `textAlignment=textStart`
  is irrelevant — the view fits the text exactly, so "start" vs "end"
  alignment within the view doesn't affect the visible result.
- **Ours** places `field_label` inside a `LinearLayout` with
  `layout_width=0dp` + `layout_weight=1`. That makes the TextView fill
  *all* available horizontal space, so the view is much wider than the
  text. With `gravity=end` the text naturally hugs the right edge — but
  `textAlignment=textStart` overrides `gravity` for text positioning
  (textAlignment wins when both are set on API 17+) and pushes the text
  to the left edge.

In other words, native's combination is harmless because the constraint
geometry positions the view, not the text alignment attribute. Our
RemoteViews-allowed `LinearLayout` doesn't have constraint bias, so we
have to let `gravity` win — meaning we leave `textAlignment` unset.

### Stream-state overlay vs `streamStateStyle`

The native `streamStateStyle` (`styles.xml:3725`) governs the
"Searching / Not available / Idle" overlay. We render our equivalent in
the separate `stream_state_tv` TextView (declared in `barberfish_field.xml`).
Native uses `lineSpacingMultiplier=0.6` and `maxLines=2`; we use
`lineSpacingMultiplier=0.7` and `maxLines=2`. Close enough for now;
tighten if the stream-state overlay drifts noticeably from native.

## K2 compatibility

All layout attributes (`layout_centerVertical`, `layout_alignBaseline`,
`layout_below`, `layout_alignParentBottom`, `gravity`) are baked into XML
and processed at inflation time. K2 blocks `setTranslationY`, `setGravity`,
and `setTextAlignment` at runtime, but XML-baked parameters are unaffected.

`setTextViewTextSize` and `setViewPadding` (used for baseline_ref font and
stream_state_tv padding) are standard RemoteViews methods, safe on both K2
and K3.

## Stream state rendering

`stream_state_tv` (Searching / Not available / Idle) is a separate overlay
TextView, shown when `field.color is FieldColor.StreamState`. It uses
`gravity="center_vertical|{end,start,center}"` with `paddingTop` set to
`actualHeaderPx` (computed from `headerHeightPx` with real `labelLines`).
`field_value` and `baseline_ref` are hidden (GONE) in this case.
