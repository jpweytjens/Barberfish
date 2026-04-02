# Value text baseline alignment

Pin the value text baseline at a fixed vertical position regardless of font size,
using RemoteViews constraints (no ConstraintLayout).

## Approach: top gravity with direct baseline placement

`field_value` is a `match_parent` TextView with `gravity="top"` and
`includeFontPadding=false`. Dynamic top padding positions the baseline directly.

## Formula

```
paddingTop = cellH - margin + ascent     (ascent is negative)
baseline   = paddingTop + |ascent| = cellH - margin
```

margin = 13.5px (7.2dp at density 1.875) = distance from cell bottom to baseline.
Descent is not in the equation — it hangs below the baseline and gets clipped for large fonts.

## Why top gravity instead of center_vertical

`center_vertical` centers the full ascent-to-descent text block in the content area.
For numeric content (no visual descenders), the descent space is empty but still
participates in centering. Since descent scales with font size, the centering offset
varies per font size, causing baselines to drift.

`top` gravity eliminates centering entirely. The text block starts at paddingTop,
so baseline = paddingTop + |ascent| — deterministic for any font size.

## Font metrics on Karoo 3 (monospace, density 1.875)

```
ascent  = -1.0  * fontSizePx
descent =  0.2  * fontSizePx
```

## Example values (cellH=148, 2-col 4-row)

| fontSp | fontPx | ascent  | paddingTop | baseline | distFromBottom |
|--------|--------|---------|------------|----------|----------------|
| 50     | 93.75  | -93.75  | 41         | 134      | 14             |
| 48     | 90.0   | -90.0   | 44         | 134      | 14             |
| 34     | 63.75  | -63.75  | 71         | 134      | 14             |
| 30     | 56.25  | -56.25  | 78         | 134      | 14             |
