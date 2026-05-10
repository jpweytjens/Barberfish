# Value Rendering — Recap & K2 Issue

## Problem statement

Match native Karoo data-field value baselines from a RemoteViews-based
extension. Specifically, the value digit baseline must:

1. **Match native per layout** — the rideapp's `DataElementConstraints`
   factory (decompiled `hhv5/d.hha()`) returns a per-`(colSpan, rowSpan)`
   `dataSize`/`dataTranslationY` that natively positions the value.
2. **Stay stable across content-driven font shrinks** — when a long value
   forces `fontSizeForCell` to shrink the rendered text, the baseline
   should not move.
3. **Track runtime cell-height changes** — the rideapp resizes cells
   without re-calling `startView` (e.g. key-icon toggle, rerouting toast),
   so the layout must self-adjust.
4. **Render correctly on both Karoo 2 (K2) and Karoo 3 (K3).**

The hard constraint: extensions render via `RemoteViews.apply`. The
RemoteViews allowlist excludes `ConstraintLayout` (which native uses) and
`Space` (native uses for the cell decoration). We're stuck inside
`RelativeLayout` + `LinearLayout` + `TextView`/`ImageView`.

## Iterations on a TextView-based value

Several attempts inside RemoteViews-allowed views all hit limits:

- `gravity="bottom"` + computed `refFontSp` from cell height arithmetic —
  fragile; required reading status/nav bar heights, stale after key-icon
  toggle.
- `baseline_ref` (invisible reference TextView) + `layout_alignBaseline`
  on `field_value` — at tight cells `baseline_ref`'s `wrap_content` got
  clipped to its parent, the centering geometry collapsed and digits
  pushed past the cell bottom.
- `baseline_ref` + a clip-safe `baselineRefSp` cap formula + per-layout
  `paddingTop` nudges + an `observedBoxHeightDp` lookup + `cellHeightPx`
  plumbed through the whole stack — still couldn't close the residual on
  5×2 / 5×1 without breaking other layouts.

Two months of iteration converged on the conclusion that mimicking
native's `ConstraintLayout` line-metric overflow inside RemoteViews-safe
views was not tractable.

## Bitmap solution

`field_value` became an `ImageView` displaying a `Bitmap` rendered at ride
time by `renderValueBitmap()`.

```
field_root (RelativeLayout, clipChildren=false)
├── stream_state_tv (overlay, GONE by default)
├── header_ref (invisible 1-line probe, anchors baseline_box)
├── field_header (alignParentTop, may overflow downward)
└── baseline_box (LinearLayout vertical, layout_below=header_ref,
                  alignParentBottom, match_parent height)
    ├── TextView (height=0dp, weight=1)   — top spacer
    ├── ImageView field_value (wrap_content, weight=0)
    └── TextView (height=0dp, weight=1)   — bottom spacer
```

Key design points:

- **Constant bitmap height per layout** = `0.74 × valueFontBaseSp ×
  density`. Just enough to hold the visible cap (~`0.7 × textSize`) plus a
  small buffer above; tight enough to fit 5×1's `baseline_box` (~79 px on
  K3 with key bar) without ImageView fitCenter downscaling.
- **Baseline pinned to the bitmap's bottom row.** Digits have no
  descender, so the bitmap bottom = visible cap bottom = baseline.
  Independent of `fontSizeForCell` shrinks → stable baseline across
  content changes.
- **`Bitmap.density = DENSITY_NONE`**, so RemoteViews renders at native
  pixel size with no scaling.
- **Centering via 1:1 LinearLayout weight spacers** in `baseline_box`.
  Geometrically equivalent to native's `bias=0.5` between `header_b` and
  `cell_b`. Adapts automatically to runtime cell-h changes.
- **Per-layout vertical offset** mirroring native's
  `DataElementConstraints.dataTranslationY`. We applied this paint-time
  via `rv.setFloat(R.id.field_value, "setTranslationY", translationYPx)`
  on the host ImageView. Paint-time transform — does not affect layout,
  so the bitmap stays inside `baseline_box` regardless of magnitude.

This satisfied properties (1), (2), (3) and rendered well on **K3** —
within ±0–5 px of native on all measured pairs.

## K2 incompatibility

**`View.setTranslationY` is not a `@RemotableViewMethod` on Android 8.1
(API 27 — Karoo 2's OS).** That annotation was added to the framework in
API 28 (Android 9 / Pie). On K2, calling
`RemoteViews.setFloat(viewId, "setTranslationY", v)` throws
`ActionException` at apply time. The rideapp catches it, leaves the
cell empty, and logs a warning.

### Empirical confirmation

Logcat from K2 (Android 8.1.0, API 27) immediately after our extension
attempts to render any BF cell with the `setFloat` call:

```
W HHApp: Extensions: Error in view for TYPE_EXT::barberfish::power
W HHApp: android.widget.RemoteViews$ActionException:
  view: android.widget.ImageView can't use method with RemoteViews:
  setTranslationY(float)
```

Repeated for every BF cell (`barberfish::power`, `::time-paused`,
`::speed`, `::avg-speed-total`, …). On screen: native fields render
normally, BF cells render entirely blank.

### Why the rideapp's own use is fine

The rideapp owns its native data field views directly. K2's
`hhk5/f.java:46` (= K3's `hhu5/f.java:48`):

```java
((AppCompatTextView) c0032k.hhc).setTranslationY(constraints.f2347hhb);
```

This is a **direct method call** on a view the rideapp owns — not via
RemoteViews. No `@RemotableViewMethod` check, no allowlist. The method
exists on `View` since API 11; calling it directly works at any API
level.

Our path is different. We're an extension running in our own process; we
describe view manipulations as a `RemoteViews` parcel, the karoo-ext SDK
ships it to the rideapp via IPC, and the rideapp's `RemoteViews.apply()`
dispatches actions through the framework's reflection machinery. That
dispatch is gated on the framework's `@RemotableViewMethod` allowlist —
a security boundary so extensions can't invoke arbitrary view methods on
the rideapp's UI process.

So:

| Caller | Mechanism | API 27 result |
|---|---|---|
| Rideapp internal (native fields) | direct method call | ✅ works |
| Extension via RemoteViews | reflective dispatch + allowlist check | ❌ throws on K2 |
| Extension via RemoteViews (K3, API 33) | same | ✅ works (allowlist expanded in API 28+) |

It's not the rideapp blocking us; it's the cross-process safety
boundary in the Android framework itself. Lowering our app's
`targetSdk` doesn't help — the check uses the **device's** framework
annotations (the `View` class shipped with K2's OS image), not our
app's targetSdk.

### Reference

- Decompiled K2 rideapp: `docs/ride_decompiled_k2/sources/hhk5/f.java`
  (gitignored — pull from device with
  `adb pull $(adb shell pm path io.hammerhead.rideapp | sed 's/package://') docs/ride_k2.apk`
  then `./docs/decompile.sh` after pointing it at `ride_k2.apk`).
- The K2 rideapp applies our RemoteViews via plain
  `RemoteViews.apply(context, parent)` in `hhn5/C0402j.java:71` — same
  unfiltered path as K3, no extra restrictions on the rideapp's side.

## Options to fix K2

### A — Asymmetric padding inside the bitmap

Add `2T` empty pixel rows above the cap (positive T → shift down) or
`2|T|` rows below the baseline (negative T → shift up). Bitmap grows by
`2|T|` px. With 1:1 spacer centering, the visible baseline shifts by
exactly `T`, identical effect to `setTranslationY(T)`.

K2-compatible: yes (only `setImageViewBitmap` is needed).

**Issue:** 5×1 at T = −3 dp needs `bitmap_h + 2|T|` = 75 + 11 = 86 px,
which exceeds the 79 px `baseline_box` → ImageView fitCenter downscales
(the original problem we tried to avoid).

**Mitigation:** drop T_5×1 to 0 (or −1 dp ⇒ 4 px padding ⇒ 79 px total,
exact fit). Accept small residual on 5×1.

### B — Center across the full cell

Replace `baseline_box`'s anchor-below-`header_ref` with full-cell
centering (FrameLayout or RelativeLayout `centerVertical`). This is what
native effectively does for fields where `headerLayout` is GONE.

K2-compatible: yes (XML-baked).

**Issue:** changes the centering region for all layouts. Native 2-col
fields use `(header_b → cell_b)` centering, so BF baselines on 2-col
would drift relative to native, regressing the currently-passing
2-col cases.

**Variant:** per-layout XML (1-col uses full cell, 2-col keeps current).
Adds two more layout XMLs and a runtime selector.

### C — `setViewPadding` on `field_value`

Use the K2-supported `setViewPadding(viewId, l, t, r, b)` to apply
asymmetric padding to the ImageView. The bitmap shifts visually inside
the ImageView; the ImageView grows by the padding amount.

K2-compatible: yes (`setViewPadding` is a standard remotable method).

**Issue:** same size-growth concern as Option A — at 5×1 with T = −3 dp,
`paddingBottom = 11` makes ImageView height 86 px vs 79 px box.

### D — Line-height bitmap with overflow

Make `bitmap_h = 1.03 × textSize × density` (= native's `wrap_content`
TextView line height for `relative` font). Allow overflow above and below
via `clipChildren=false` (already set on `field_root` and `baseline_box`).
Encode translation via where the digit baseline is drawn within the
bitmap.

This most closely mirrors native's behavior — its TextView line metrics
overflow the constraint region the same way our bitmap would.

K2-compatible: yes.

**Pros:** structural parity with native, less special-casing.
**Cons:** ~30% more bitmap pixels per render (memory + draw cost). Need
to verify the rideapp's outer FrameLayout doesn't clip the overflow at
the cell boundary.

### E — Hybrid: `setTranslationY` on K3, padding on K2

Detect the device at runtime and use the appropriate mechanism per
platform.

K2-compatible: yes (when on K2, uses padding path).

**Pros:** preserves K3's optimal alignment (the current state).
**Cons:** two code paths to maintain; runtime branching adds a small
amount of complexity.

### F — Drop all translations (T=0 everywhere)

Simplest fix. Removes the `setTranslationY` call entirely and uses T=0
for every layout.

| Layout | Current value | Effect of dropping |
|---|---|---|
| 1×1 / 2×1 | +4 dp | BF baseline ~7.5 px UP (further from cell bottom) |
| 3×1 / 4×1 | 0 | unchanged |
| 5×1 | −3 dp | BF baseline ~5.6 px DOWN (closer to cell bottom) |

K2-compatible: yes.

**Pros:** zero special-casing, identical render path on K2 and K3.
**Cons:** 1×1/2×1 and 5×1 will be visibly misaligned with native (~6 px).

## Recommendation

Either:

- **Option A with T_5×1 = 0** — restore bitmap-baked padding, drop only
  5×1's translation. 1×1/2×1 keep their +4 dp via `extraTop=15` (bitmap
  fits comfortably in those tall cells), 3×1/4×1 unchanged, 5×1 takes a
  small residual.
- **Option D** — the cleaner long-term path; reproduces native's
  structural overflow behavior. Worth prototyping if memory is not a
  concern (~30% per-cell increase, still small in absolute terms).

## Current state on the branch

- K3: aligned within ±0–5 px on most paired rows after the
  `setTranslationY` tune (commit `395bf95`).
- K2: BF cells fail to render entirely on the right column (and any
  position where 1-col/2-col layouts trigger the `setFloat` call with
  non-zero translation).
- No K2 fix committed yet.

## Related references

- `docs/baseline-alignment.md` — the canonical structural reference.
- `app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/BitmapValue.kt`
  — `renderValueBitmap()` and `valueBitmapHeightPx()`.
- `app/src/main/kotlin/com/jpweytjens/barberfish/datatype/BarberfishView.kt`
  — `rv.setFloat(R.id.field_value, "setTranslationY", ...)` (the K2
  incompatible call).
- `app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ViewSizeConfig.kt`
  — per-layout `valueTranslationDp` table.
- Decompiled native: `docs/ride_decompiled/sources/hhu5/f.java:48`
  (`dataTextView.setTranslationY(constraints.f4284hhb)`) and
  `hhv5/d.hha()` (the `DataElementConstraints` factory).
