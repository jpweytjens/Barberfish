package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.overviewBitmapFlow
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.streamRouteRemainingConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RouteRemainingField(private val karooSystem: KarooSystemService) :
    BarberfishBase<SparklineRender>("barberfish", "route-remaining") {

    private fun bitmapFlow(context: Context, config: ViewConfig, isPreview: Boolean): Flow<SparklineRender> {
        return context.streamRouteRemainingConfig()
            .map { it.showHeader }
            .distinctUntilChanged()
            .flatMapLatest { showHeader ->
                val (widthPx, heightPx) = sparklineImageSize(config, context, showHeader)
                overviewBitmapFlow(
                        karooSystem,
                        context,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        isPreview = isPreview,
                    )
                    .map { SparklineRender(it, showHeader) }
            }
    }

    override fun liveFlow(context: Context, config: ViewConfig): Flow<SparklineRender> =
        bitmapFlow(context, config, isPreview = false)

    override fun previewFlow(context: Context, config: ViewConfig): Flow<SparklineRender> =
        bitmapFlow(context, config, isPreview = true)

    override fun renderState(state: SparklineRender, design: DataFieldDesignConfig, config: ViewConfig, context: Context): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.barberfish_sparkline)
        if (state.showHeader) {
            applySparklineHeaderChrome(
                rv,
                context.getString(R.string.route_remaining_name),
                R.drawable.ic_landscape,
                config,
                context,
            )
        } else {
            rv.setViewVisibility(R.id.field_header, View.GONE)
        }
        state.bitmap?.let { rv.setImageViewBitmap(R.id.sparkline_image, it) }
        return rv
    }
}
