package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.overviewBitmapFlow
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.flow.Flow

class RouteRemainingField(private val karooSystem: KarooSystemService) :
    BarberfishBase<Bitmap?>("barberfish", "route-remaining") {

    private fun bitmapFlow(context: Context, config: ViewConfig, isPreview: Boolean): Flow<Bitmap?> {
        val (widthPx, heightPx) = sparklineImageSize(config, context)
        return overviewBitmapFlow(
            karooSystem,
            context,
            widthPx = widthPx,
            heightPx = heightPx,
            isPreview = isPreview,
        )
    }

    override fun liveFlow(context: Context, config: ViewConfig): Flow<Bitmap?> =
        bitmapFlow(context, config, isPreview = false)

    override fun previewFlow(context: Context, config: ViewConfig): Flow<Bitmap?> =
        bitmapFlow(context, config, isPreview = true)

    override fun renderState(state: Bitmap?, design: DataFieldDesignConfig, config: ViewConfig, context: Context): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.barberfish_sparkline)
        applySparklineHeaderChrome(
            rv,
            context.getString(R.string.route_remaining_name),
            R.drawable.ic_landscape,
            config,
            context,
        )
        if (state != null) rv.setImageViewBitmap(R.id.sparkline_image, state)
        return rv
    }
}
