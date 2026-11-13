package com.jpweytjens.barberfish.datatype.shared

import androidx.annotation.DrawableRes
import com.jpweytjens.barberfish.R

/**
 * The sock drawable for [bands] standing bands, 1 to 5, grey when [muted]. Calm (0) has no
 * drawable: nothing is drawn.
 */
@DrawableRes
internal fun windSockDrawable(bands: Int, muted: Boolean): Int =
    when (bands.coerceIn(1, WindSockGeometry.MAX_BANDS)) {
        1 -> if (muted) R.drawable.ic_wind_sock_muted_1 else R.drawable.ic_wind_sock_1
        2 -> if (muted) R.drawable.ic_wind_sock_muted_2 else R.drawable.ic_wind_sock_2
        3 -> if (muted) R.drawable.ic_wind_sock_muted_3 else R.drawable.ic_wind_sock_3
        4 -> if (muted) R.drawable.ic_wind_sock_muted_4 else R.drawable.ic_wind_sock_4
        else -> if (muted) R.drawable.ic_wind_sock_muted_5 else R.drawable.ic_wind_sock_5
    }
