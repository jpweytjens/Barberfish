package com.jpweytjens.barberfish.datatype.shared

private const val MIN_BITMAP_HEIGHT_PX = 30

/**
 * Bitmap height for a value cell at the given layout's `valueFontBase` (sp).
 *
 * The visible digit cap of Karoo's `relative` monospace is ≈ 0.7 × textSize.
 * 0.8 × textSize gives just enough room for the cap plus ~0.1 × textSize of
 * buffer above it, with the baseline pinned to the bitmap bottom (digits
 * have no descender so no padding needed there). Choosing a CONSTANT height
 * per layout — independent of the per-render `fontSizeForCell` shrunk size —
 * means the baseline is stable across content-driven font-size changes.
 *
 * Pixel-rounded with a `MIN_BITMAP_HEIGHT_PX` floor for safety.
 */
fun valueBitmapHeightPx(valueFontBaseSp: Int, density: Float): Int {
    val raw = (0.8f * valueFontBaseSp * density).toInt()
    return raw.coerceAtLeast(MIN_BITMAP_HEIGHT_PX)
}
