package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.Wind
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.forecastClock
import com.jpweytjens.barberfish.datatype.shared.formatHeadwind
import com.jpweytjens.barberfish.datatype.shared.headwindComponent
import com.jpweytjens.barberfish.datatype.shared.relativeWindDeg
import com.jpweytjens.barberfish.datatype.shared.windAt
import com.jpweytjens.barberfish.datatype.shared.windFieldColor
import com.jpweytjens.barberfish.datatype.shared.windSockBands
import com.jpweytjens.barberfish.extension.RiderFix
import com.jpweytjens.barberfish.extension.WindFieldConfig
import com.jpweytjens.barberfish.extension.streamHeadwindSnapshots
import com.jpweytjens.barberfish.extension.streamRiderFix
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamWindFieldConfig
import de.timklge.headwind.client.HeadwindSnapshot
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Wind from the Headwind extension: a plain arrow turned by the rider-relative angle beside the
 * signed headwind component. The wind is that extension's forecast blended to the rider's position
 * and the time (see windAt), projected onto the rider's course, the same held course the map sock
 * uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WindField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "wind") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamWindFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) -> liveStates(context, karooSystem, profile, cfg) }

    override fun previewFlow(context: Context): Flow<FieldState> =
        context.streamWindFieldConfig().flatMapLatest { cfg -> cyclePreview(previewStates(cfg)) }

    companion object {
        private const val LABEL = "Wind"
        private val ICON = R.drawable.ic_air

        /**
         * No forecast yet, no internet before the first one, or the Headwind app missing; the
         * service looks the same from outside. noSensor drops the HUD column.
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
            context: Context,
            karooSystem: KarooSystemService,
            profile: UserProfile,
            cfg: WindFieldConfig,
        ): Flow<FieldState> =
            fieldStates(
                karooSystem.streamRiderFix(),
                context.streamHeadwindSnapshots(),
                forecastClock(),
                profile,
                cfg,
            )

        /** The field's states over time: the wind at the latest fix, projected onto its course. */
        internal fun fieldStates(
            fixes: Flow<RiderFix>,
            snapshots: Flow<HeadwindSnapshot?>,
            clock: Flow<Long>,
            profile: UserProfile,
            cfg: WindFieldConfig,
        ): Flow<FieldState> =
            combine(fixes, snapshots, clock) { rider, snapshot, now ->
                toFieldState(
                    snapshot.windAt(rider.position, now),
                    rider.courseDeg,
                    profile,
                    cfg,
                    stale = isStale(snapshot?.lastSuccessfulFetchEpochSeconds, now),
                )
            }

        /** Headwind downloads hourly; two hours without a download means it has lost the feed. */
        private const val STALE_AFTER_S = 2 * 3600L

        /**
         * Whether a forecast last downloaded at [fetchedAt] is too old to vouch for at [now], both
         * in epoch seconds. An unknown download time counts as stale.
         */
        internal fun isStale(fetchedAt: Long?, now: Long): Boolean =
            fetchedAt == null || now - fetchedAt > STALE_AFTER_S

        /**
         * One reading from the wind and the held course. No wind reads "No wind data", even before
         * a course; with wind but no course yet, "Searching…". A [stale] reading keeps its number
         * and arrow but greys.
         */
        // Suppressed: the wind and course fallbacks share one text state each, which is inherently
        // more returns than the default threshold allows.
        @Suppress("ReturnCount")
        internal fun toFieldState(
            wind: Wind?,
            courseDeg: Double?,
            profile: UserProfile,
            cfg: WindFieldConfig,
            stale: Boolean = false,
        ): FieldState {
            if (wind == null) return noWindData()
            if (courseDeg == null) return FieldState.searching(LABEL, ICON)
            val angleDeg = relativeWindDeg(wind.fromDeg, courseDeg)
            val headwindMs = headwindComponent(wind.speedMs, angleDeg)
            return FieldState(
                primary = formatHeadwind(ConvertType.SPEED.apply(headwindMs, profile)),
                label = LABEL,
                color = if (stale) FieldColor.Muted else windFieldColor(headwindMs, cfg.colorMode),
                iconRes = ICON,
                colorMode = cfg.colorMode,
                windArrowDeg = if (windSockBands(wind.speedMs) > 0) angleDeg.toFloat() else null,
            )
        }

        /** Calm, a light tailwind, a crosswind, a strong front-right headwind; in km/h. */
        fun previewStates(cfg: WindFieldConfig): List<FieldState> {
            data class Sample(val angleDeg: Float, val headwindKmh: Double, val speedKmh: Double)
            return listOf(
                    Sample(90f, 0.0, 1.0),
                    Sample(20f, -24.0, 25.0),
                    Sample(270f, 1.0, 14.0),
                    Sample(225f, 12.4, 15.0),
                    Sample(180f, 29.0, 29.0),
                )
                .map { s ->
                    FieldState(
                        primary = formatHeadwind(s.headwindKmh),
                        label = LABEL,
                        color = windFieldColor(s.headwindKmh / KMH_PER_MS, cfg.colorMode),
                        iconRes = ICON,
                        colorMode = cfg.colorMode,
                        windArrowDeg =
                            if (windSockBands(s.speedKmh / KMH_PER_MS) > 0) s.angleDeg else null,
                    )
                }
        }

        private const val KMH_PER_MS = 3.6
    }
}
