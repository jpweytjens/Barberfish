# Karoo 2 Compatibility

Karoo 2 runs Android 8 (API 26). Karoo 3 runs Android 13 (API 33).
The project minSdk is 23, so the APK installs on both devices.

## RemoteViews compatibility

Tested on a physical Karoo 2 (2026-03-29).

| Feature                                   | K2 (API 26)       | K3 (API 33) | Alternative for K2                                                                   | Alt works on K3? |
| ----------------------------------------- | ----------------- | ----------- | ------------------------------------------------------------------------------------ | ---------------- |
| `rv.setInt("setGravity", ...)`            | CRASH             | Yes         | Bake gravity into XML layout variants                                                | Yes              |
| `rv.setInt("setTextAlignment", ...)`      | CRASH             | Yes         | Bake alignment into XML layout variants                                              | Yes              |
| `rv.setViewLayoutWidth/Height`            | No (API 31+)      | Yes         | Already guarded -- falls back to XML default `18dp`                                  | Yes              |
| `rv.setViewOutlinePreferredRadius`        | No (API 31+)      | Yes         | Already guarded -- no rounded preview corners                                        | Yes              |
| `rv.setBoolean("setClipToOutline")`       | No (API 31+)      | Yes         | Already guarded                                                                      | Yes              |
| `xml: android:fontWeight="500"`           | Ignored (API 28+) | Yes         | Bundle medium-weight font as explicit `fontFamily` variant, or accept default weight | Yes              |
| `rv.setInt("setBackgroundColor")`         | Yes               | Yes         | --                                                                                   | --               |
| `rv.setTextViewText`                      | Yes               | Yes         | --                                                                                   | --               |
| `rv.setTextColor`                         | Yes               | Yes         | --                                                                                   | --               |
| `rv.setTextViewTextSize`                  | Yes               | Yes         | --                                                                                   | --               |
| `rv.setViewPadding`                       | Yes (API 16+)     | Yes         | --                                                                                   | --               |
| `rv.setImageViewResource`                 | Yes               | Yes         | --                                                                                   | --               |
| `rv.setInt("setColorFilter")`             | Yes               | Yes         | --                                                                                   | --               |
| `rv.setViewVisibility`                    | Yes               | Yes         | --                                                                                   | --               |
| `rv.setInt("setMaxLines")`                | Yes               | Yes         | --                                                                                   | --               |
| `rv.setInt("setLines")`                   | Yes               | Yes         | --                                                                                   | --               |
| `rv.setFloat("setLineSpacingMultiplier")` | Likely yes*       | Yes         | Set in XML if it crashes                                                             | Yes              |
| `rv.setFloat("setTranslationY")`          | CRASH             | Yes         | Skip translationY; use topPadding fallback                                           | Yes              |
| `rv.addView` / `rv.removeAllViews`        | Yes               | Yes         | --                                                                                   | --               |
| `xml: android:breakStrategy`              | Yes (API 23+)     | Yes         | --                                                                                   | --               |
| `xml: android:letterSpacing`              | Yes (API 21+)     | Yes         | --                                                                                   | --               |

*"Likely yes" = `@RemotableViewMethod` annotated in AOSP API 26, but not confirmed on the
Karoo 2 hardware. The `setGravity` crash shows Hammerhead's ROM may differ from stock AOSP.

## Crash log (setGravity)

```
android.widget.RemoteViews$ActionException:
  view: android.widget.TextView can't use method with RemoteViews: setGravity(int)
  at android.widget.RemoteViews.getMethod(RemoteViews.java:974)
  at android.widget.RemoteViews$ReflectionAction.apply(RemoteViews.java:1521)
  ...
  at com.jpweytjens.barberfish.datatype.shared.RemoteViewsBitmapKt.remoteViewsToBitmap(RemoteViewsBitmap.kt:16)
  at com.jpweytjens.barberfish.screens.MainActivityKt.FieldPreviewBox(MainActivity.kt:1002)
```

Crash happens when the config screen renders a field preview via `remoteViewsToBitmap()`.
The same `setGravity` call would also crash on the ride screen.

## Hardware detection

The SDK provides `HardwareType.K2` / `HardwareType.KAROO` via `KarooSystemService.getHardwareType()`
for runtime device detection if needed.

## Display differences

|         | Karoo 2      | Karoo 3         |
| ------- | ------------ | --------------- |
| Screen  | 3.2" 480x800 | 3.2" higher-res |
| Density | unknown      | 1.875 (300 dpi) |

The label font size lookup table in CLAUDE.md is Karoo 3-specific (density 1.875).
`ViewConfig.textSize` from the SDK is density-aware and should adapt automatically,
but visual tuning on-device may be needed for the K2 display.

## Summary

`setGravity` was the only confirmed blocker. Fixed by replacing all 3 `setGravity` calls
in `BarberfishView.kt` with `setTextAlignment` (a `@RemotableViewMethod` since API 17).
XML `android:gravity="center_vertical"` handles vertical centering; `setTextAlignment`
handles horizontal alignment. Works on both K2 and K3 with no version-specific branching.
