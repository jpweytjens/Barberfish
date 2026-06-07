package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.overviewBitmapFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.flow.Flow

private const val STANDALONE_HEIGHT_FRACTION = 0.25f

class RouteRemainingField(private val karooSystem: KarooSystemService) :
    BarberfishBase<Bitmap?>("barberfish", "route-remaining") {

    private fun bitmapFlow(context: Context, isPreview: Boolean): Flow<Bitmap?> {
        val dm = context.resources.displayMetrics
        return overviewBitmapFlow(
            karooSystem,
            context,
            widthPx = dm.widthPixels,
            heightPx = (dm.heightPixels * STANDALONE_HEIGHT_FRACTION).toInt(),
            isPreview = isPreview,
        )
    }

    override fun liveFlow(context: Context): Flow<Bitmap?> = bitmapFlow(context, isPreview = false)

    override fun previewFlow(context: Context): Flow<Bitmap?> =
        bitmapFlow(context, isPreview = true)

    override fun renderState(state: Bitmap?, config: ViewConfig, context: Context): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.barberfish_sparkline)
        if (state != null) rv.setImageViewBitmap(R.id.sparkline_image, state)
        return rv
    }
}
