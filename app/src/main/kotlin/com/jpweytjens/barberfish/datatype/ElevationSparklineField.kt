package com.jpweytjens.barberfish.datatype

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.SparklineFrame
import com.jpweytjens.barberfish.datatype.shared.sparklineBitmapFlow
import com.jpweytjens.barberfish.extension.SparklineTapReceiver
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Standalone sparkline cell height as a fraction of screen height. Approximates a
// typical 3-row data cell; the SDK doesn't surface the actual cell height through
// this code path, so this is a best-guess default rather than a precise fit.
private const val STANDALONE_HEIGHT_FRACTION = 0.25f

class ElevationSparklineField(private val karooSystem: KarooSystemService) :
    BarberfishBase<Bitmap?>("barberfish", "elevation-sparkline") {

    private fun bitmapFlow(context: Context, isPreview: Boolean): Flow<Bitmap?> {
        val dm = context.resources.displayMetrics
        val widthPx = dm.widthPixels
        val heightPx = (dm.heightPixels * STANDALONE_HEIGHT_FRACTION).toInt()
        return sparklineBitmapFlow(karooSystem, context, widthPx, heightPx, isPreview)
            .map { it.bitmap }
    }

    override fun liveFlow(context: Context): Flow<Bitmap?> = bitmapFlow(context, isPreview = false)
    override fun previewFlow(context: Context): Flow<Bitmap?> = bitmapFlow(context, isPreview = true)

    override fun renderState(state: Bitmap?, config: ViewConfig, context: Context): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.barberfish_sparkline)
        if (state != null) {
            rv.setImageViewBitmap(R.id.sparkline_image, state)
        }
        if (!config.preview) {
            val intent = Intent(context, SparklineTapReceiver::class.java).apply {
                action = SparklineTapReceiver.ACTION
            }
            val pi = PendingIntent.getBroadcast(
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
