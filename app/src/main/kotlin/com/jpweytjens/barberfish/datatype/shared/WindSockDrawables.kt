package com.jpweytjens.barberfish.datatype.shared

import androidx.annotation.DrawableRes
import com.jpweytjens.barberfish.R

/**
 * The sock drawable for [bands] standing bands, 1 to 5. Calm (0) has no drawable: nothing is drawn.
 */
@DrawableRes
internal fun windSockDrawable(bands: Int): Int =
    when (bands.coerceIn(1, WindSockGeometry.MAX_BANDS)) {
        1 -> R.drawable.ic_wind_sock_1
        2 -> R.drawable.ic_wind_sock_2
        3 -> R.drawable.ic_wind_sock_3
        4 -> R.drawable.ic_wind_sock_4
        else -> R.drawable.ic_wind_sock_5
    }
