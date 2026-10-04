package com.jpweytjens.barberfish.screens

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jpweytjens.barberfish.datatype.barberfishFieldRemoteViews
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.ViewConfig

/**
 * Rasterizes a field through the same RemoteViews the live cell uses, cached until any input
 * changes. Shared by the config-screen field previews and the HUD slot previews.
 */
@Composable
internal fun rememberFieldPreviewBitmap(
    field: FieldState,
    colorMode: ZoneColorMode,
    sizeConfig: ViewSizeConfig,
    widthPx: Int,
    heightPx: Int,
): Bitmap {
    val context = LocalContext.current
    return remember(field, colorMode, sizeConfig, widthPx, heightPx) {
        val rv =
            barberfishFieldRemoteViews(
                field = field,
                alignment = ViewConfig.Alignment.RIGHT,
                colorMode = colorMode,
                sizeConfig = sizeConfig,
                preview = true,
                context = context,
            )
        remoteViewsToBitmap(rv, widthPx, heightPx, context)
    }
}
