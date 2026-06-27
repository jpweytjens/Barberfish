package com.jpweytjens.barberfish.datatype

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.sparklineBitmapFlow
import com.jpweytjens.barberfish.extension.SparklineTapReceiver
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.streamFieldSparklineConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ElevationSparklineField(private val karooSystem: KarooSystemService) :
    BarberfishBase<SparklineRender>("barberfish", "elevation-sparkline") {

    private fun bitmapFlow(context: Context, config: ViewConfig, isPreview: Boolean): Flow<SparklineRender> {
        val cfgFlow = context.streamFieldSparklineConfig()
        return cfgFlow
            .map { it.showHeader }
            .distinctUntilChanged()
            .flatMapLatest { showHeader ->
                val (widthPx, heightPx) = sparklineImageSize(config, context, showHeader)
                sparklineBitmapFlow(
                        karooSystem,
                        context,
                        configFlow = cfgFlow,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        isPreview = isPreview,
                    )
                    .map { SparklineRender(it.bitmap, showHeader) }
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
                context.getString(R.string.elevation_sparkline_name),
                R.drawable.ic_grade,
                config,
                context,
            )
            rv.setViewVisibility(R.id.field_header, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.field_header, View.GONE)
        }
        state.bitmap?.let { rv.setImageViewBitmap(R.id.sparkline_image, it) }
        if (!config.preview) {
            val intent =
                Intent(context, SparklineTapReceiver::class.java).apply {
                    action = SparklineTapReceiver.ACTION
                    putExtra(SparklineTapReceiver.EXTRA_SURFACE, SparklineTapReceiver.SURFACE_FIELD)
                }
            val pi =
                PendingIntent.getBroadcast(
                    context,
                    R.layout.barberfish_sparkline,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            rv.setOnClickPendingIntent(R.id.sparkline_root, pi)
        }
        return rv
    }
}
