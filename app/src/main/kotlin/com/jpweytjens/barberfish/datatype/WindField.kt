package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HEADWIND_ANGLE_STREAM
import com.jpweytjens.barberfish.datatype.shared.HEADWIND_SPEED_STREAM
import com.jpweytjens.barberfish.datatype.shared.WIND_SPEED_STREAM
import com.jpweytjens.barberfish.datatype.shared.WindSockGlyph
import com.jpweytjens.barberfish.datatype.shared.WindUnit
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.formatHeadwind
import com.jpweytjens.barberfish.datatype.shared.windFieldColor
import com.jpweytjens.barberfish.datatype.shared.windSockBands
import com.jpweytjens.barberfish.datatype.shared.windUnitFor
import com.jpweytjens.barberfish.extension.WindFieldConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamWindFieldConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Wind from the Headwind extension: the sock turned by the rider-relative angle beside the signed
 * headwind component. Three of that extension's streams feed it; the profile decides the unit the
 * bands assume (see WindUnit).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WindField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "wind") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamWindFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) -> liveStates(karooSystem, profile, cfg) }

    override fun previewFlow(context: Context): Flow<FieldState> =
        context.streamWindFieldConfig().flatMapLatest { cfg -> cyclePreview(previewStates(cfg)) }

    companion object {
        private const val LABEL = "Wind"
        private val ICON = R.drawable.ic_air

        /**
         * No forecast yet, no internet before the first one, or the Headwind app missing; the
         * streams look the same from outside. noSensor drops the HUD column.
         */
        fun noWindData(): FieldState =
            FieldState(
                "No wind data",
                LABEL,
                FieldColor.StreamState,
                iconRes = ICON,
                noSensor = true,
            )

        /** Shared by the standalone field and the HUD slot. */
        fun liveStates(
            karooSystem: KarooSystemService,
            profile: UserProfile,
            cfg: WindFieldConfig,
        ): Flow<FieldState> =
            combine(
                karooSystem.streamDataFlow(HEADWIND_ANGLE_STREAM),
                karooSystem.streamDataFlow(HEADWIND_SPEED_STREAM),
                karooSystem.streamDataFlow(WIND_SPEED_STREAM),
            ) { angle, headwindSpeed, windSpeed ->
                toFieldState(angle, headwindSpeed, windSpeed, profile, cfg)
            }

        // Suppressed: the three-stream toErrorFieldState early-return pattern (see
        // Extensions.kt) is required so streaming, error and unavailable states share one
        // label; that is inherently more returns than the default threshold allows.
        @Suppress("ReturnCount")
        fun toFieldState(
            angle: StreamState,
            headwindSpeed: StreamState,
            windSpeed: StreamState,
            profile: UserProfile,
            cfg: WindFieldConfig,
        ): FieldState {
            angle.toErrorFieldState(LABEL, ICON, noWindData())?.let {
                return it
            }
            headwindSpeed.toErrorFieldState(LABEL, ICON, noWindData())?.let {
                return it
            }
            windSpeed.toErrorFieldState(LABEL, ICON, noWindData())?.let {
                return it
            }
            val angleDeg = angle.single() ?: return noWindData()
            val headwind = headwindSpeed.single() ?: return noWindData()
            val speed = windSpeed.single() ?: return noWindData()
            val unit = windUnitFor(profile)
            val bands = windSockBands(speed, unit)
            return FieldState(
                primary = formatHeadwind(headwind),
                label = LABEL,
                color = windFieldColor(headwind, unit, cfg.colorMode),
                iconRes = ICON,
                colorMode = cfg.colorMode,
                windSock = if (bands > 0) WindSockGlyph(bands, angleDeg.toFloat()) else null,
            )
        }

        private fun StreamState.single(): Double? =
            (this as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.SINGLE)

        /** Calm, a light tailwind, a crosswind, a strong front-right headwind. */
        fun previewStates(cfg: WindFieldConfig): List<FieldState> {
            val unit = WindUnit.KPH
            data class Sample(val angleDeg: Float, val headwind: Double, val speed: Double)
            return listOf(
                    Sample(90f, 0.0, 1.0),
                    Sample(0f, -6.0, 6.0),
                    Sample(270f, 1.0, 14.0),
                    Sample(225f, 12.4, 15.0),
                    Sample(180f, 29.0, 29.0),
                )
                .map { s ->
                    val bands = windSockBands(s.speed, unit)
                    FieldState(
                        primary = formatHeadwind(s.headwind),
                        label = LABEL,
                        color = windFieldColor(s.headwind, unit, cfg.colorMode),
                        iconRes = ICON,
                        colorMode = cfg.colorMode,
                        windSock = if (bands > 0) WindSockGlyph(bands, s.angleDeg) else null,
                    )
                }
        }
    }
}
