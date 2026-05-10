# Karoo 2 Compatibility

Karoo 2 runs Android 8 (API 26). Karoo 3 runs Android 13 (API 33).
The project minSdk is 23, so the APK installs on both devices.

## RemoteViews methods blacklisted on K2

Confirmed via on-device testing. These crash at `RemoteViews.apply` time on K2 but
work on K3 — bake the equivalent into XML.

- `rv.setInt("setGravity", ...)` → bake `android:gravity` into XML layout variants
- `rv.setInt("setTextAlignment", ...)` → bake `android:textAlignment` into XML layout variants
- `rv.setFloat("setTranslationY", ...)` → bake `android:translationY` into XML layout variants

## Crash log shape

When a RemoteViews method isn't `@RemotableViewMethod` on the device's API level,
the rideapp catches an `ActionException` and the cell renders blank. Example:

```
android.widget.RemoteViews$ActionException:
  view: android.widget.TextView can't use method with RemoteViews: setGravity(int)
  at android.widget.RemoteViews.getMethod(RemoteViews.java:974)
  at android.widget.RemoteViews$ReflectionAction.apply(RemoteViews.java:1521)
```
