# Barberfish architecture

## The native Karoo field anatomy

Every native Hammerhead data field follows the same visual structure:

```
┌─────────────────────────────┐
│ 🗲  POWER          ← Header │
│                             │
│          239        ← Value │
└─────────────────────────────┘
```

The **Header** sits at the top of the cell and contains:
- an optional **Icon** (tinted teal, or black when the cell has a colored background)
- a **Label** — short, all-caps, typically one word (POWER, HR, SPEED) but two lines for compound names (AVG SPEED / MOVING)

The **Value** is the large number below the header. Its font shrinks automatically to fit longer strings (e.g. `00:18` is smaller than `239`).

Barberfish reimplements this anatomy as a Glance composable hierarchy that matches the native look and feel precisely, with added support for zone coloring, variable font sizes, and a three-column HUD.

---

## Component hierarchy

```
BarberfishView          — full cell composable (Box: value first, header on top)
├── BarberfishValue     — the large primary number (AndroidRemoteViews)
└── BarberfishHeader    — icon + label strip (Row + AndroidRemoteViews)
    └── BarberfishIcon  — tinted icon with optional top-padding
```

`BarberfishView` uses a `Box` layout rather than a `Column`. `BarberfishValue` is placed first and fills the entire cell height (`fillMaxHeight`). `BarberfishHeader` is placed second and floats on top at `Alignment.TopStart`. This avoids giving a `defaultWeight` modifier to an `AndroidRemoteViews` child — Glance cannot measure RemoteViews content, so weight distribution is unreliable.

---

## Why AndroidRemoteViews for label and value

Jetpack Glance 1.1.1 compiles composables to `RemoteViews` but the composable API exposes only a subset of `RemoteViews` capabilities. Two features critical to matching the native look are absent:

| Need | Glance `Text` | `AndroidRemoteViews` |
|---|---|---|
| `maxLines="1"` per line | Not available | ✓ via XML attribute |
| `includeFontPadding="false"` | Not available | ✓ via XML attribute |
| `setTranslationY()` | Not available | ✓ via `RemoteViews` reflection |
| `textAllCaps` | Not available | ✓ via XML attribute |

### Label (`BarberfishHeader`)

Without `maxLines="1"`, Glance wraps "AVG SPEED" into three lines at 17 sp in the narrow label column, overflowing into the value area. The `AndroidRemoteViews` solution uses two `TextView`s (one per line) and collapses the gap between them with `setTranslationY`. The gap is calculated from the font's descent and the space above capital letters — the two quantities that `includeFontPadding="false"` removes — minus a configurable `headerLineSpacing` to leave a hair of breathing room.

### Value (`BarberfishValue`)

Without `includeFontPadding="false"`, Glance adds roughly 12 dp of invisible padding below the digits, pushing the number upward away from the cell bottom. The `AndroidRemoteViews` solution uses a single `TextView` with `gravity=BOTTOM`, no font padding, and `setTranslationY(descentPx - valueBottomPadding)` to shift the number up by exactly the font's descent so the visual bottom of the digits lands at the intended distance from the cell edge.

---

## Data flow

```
DataTypeImpl subclass
  │  emits FieldState every update tick
  ▼
BarberfishDataType.Content(field, config)
  │  calls
  ▼
BarberfishView(field, alignment, colorMode, sizeConfig)
  │  derives ColorConfig from field.color
  ├─► BarberfishValue(field.primary, …)
  └─► BarberfishHeader(field.label, field.iconRes, …)
```

`FieldState` is the runtime snapshot of one field: it carries the primary value string, the header label, the icon resource, the zone color, and the color rendering mode. `DataTypeImpl` subclasses emit `FieldState` values; views are pure functions of `FieldState` and configuration.

---

## Naming conventions

| Term | Meaning |
|---|---|
| **Field** | The full cell — header + value together |
| **Header** | The top strip inside a field: icon and label |
| **Icon** | The small glyph at the start of the header |
| **Label** | The short all-caps text in the header (1–2 lines) |
| **Value** | The large primary number displayed below the header |
| **FieldState** | Runtime data snapshot emitted by a `DataTypeImpl` each tick |
| **ColorConfig** | Derived per-render colors (value text, header text, icon tint, background) |
| **ViewSizeConfig** | Per-tier layout constants (see below) |

### ViewSizeConfig

All spacing and sizing constants for one rendering tier (standard single-field or compact HUD) live in a single `ViewSizeConfig` instance. Parameters are grouped by the level they control:

```
Cell level   paddingH, paddingTop
Header       headerIconSize, headerIconLabelGap, headerFontSize,
             headerLineSpacing, headerIconTopPadding
Value        valueFontSizeBase, valueBottomPadding
```

`headerIconTopPadding` is the extra vertical offset applied to the icon when the label is two lines. The usage site decides when to apply it (`if (isMultiLine) config.headerIconTopPadding else 0.dp`); the config stores only the non-zero amount.

Two presets ship out of the box:

| Preset | Use |
|---|---|
| `ViewSizeConfig.STANDARD` | Full-size single-value fields (Power, HR, Speed, …) |
| `ViewSizeConfig.HUD` | Compact three-column HUD columns |

---

## Color system

Zone coloring and threshold coloring produce a `FieldColor` sealed variant. `FieldColor.toColorConfig(colorMode)` resolves it into a `ColorConfig` that the view layer can consume directly:

- `colorMode = TEXT` — colored text, transparent background
- `colorMode = BACKGROUND` — colored background fill, black text and icon tint
- `colorMode = NONE` — white text, no color

`ColorConfig` carries:
- `valueText: Color` — color for the value `TextView` (`.toArgb()` for `RemoteViews`)
- `headerText: ColorProvider` — color for the label `TextView`s
- `iconTint: Color` — tint applied to the `Image` composable
- `background: ColorProvider?` — `null` means transparent; non-null fills the cell

The value text is a `Color` (Compose) rather than a `ColorProvider` because `RemoteViews.setTextColor()` requires an `Int` ARGB value, which is only accessible via `Color.toArgb()`.
