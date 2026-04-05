package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews

fun remoteViewsToBitmap(
    remoteViews: RemoteViews,
    widthPx: Int,
    heightPx: Int,
    context: Context,
): Bitmap {
    val view = remoteViews.apply(context, FrameLayout(context))
    view.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, widthPx, heightPx)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    return bitmap
}
