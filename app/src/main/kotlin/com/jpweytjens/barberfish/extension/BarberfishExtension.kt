package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.datatype.AvgHRField
import com.jpweytjens.barberfish.datatype.AvgPowerField
import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.CadenceField
import com.jpweytjens.barberfish.datatype.ETAField
import com.jpweytjens.barberfish.datatype.ETAKind
import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.HRField
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.LapAvgHRField
import com.jpweytjens.barberfish.datatype.LapPowerField
import com.jpweytjens.barberfish.datatype.LastLapAvgHRField
import com.jpweytjens.barberfish.datatype.NPField
import com.jpweytjens.barberfish.datatype.PowerField
import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.TimeField
import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.datatype.shared.DEFAULT_CHEVRON_SPACING_M
import com.jpweytjens.barberfish.datatype.shared.buildClimbOverlaySpecs
import com.jpweytjens.barberfish.datatype.shared.mapDiagonalMeters
import com.jpweytjens.barberfish.datatype.shared.mapViewportBounds
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.OnNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber

private const val CLIMB_OVERLAY_WIDTH = 8          // coloured fill width; tune via screencaps

// Number of chevrons we aim to show along one visible map diagonal. Matches
// timklge/karoo-routegraph's MEDIUM frequency — dense enough to read climbs as
// climbs, sparse enough to not clutter the map.
private const val CHEVRONS_PER_DIAGONAL = 6


class BarberfishExtension : KarooExtension("barberfish", BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService

    // Order matches extension_info.xml — keep in sync when adding fields.
    override val types by lazy {
        listOf(
            HUDField(karooSystem),
            // Power
            PowerField(karooSystem),
            AvgPowerField(karooSystem),
            NPField(karooSystem),
            LapPowerField(karooSystem, isLastLap = false),
            LapPowerField(karooSystem, isLastLap = true),
            // HR
            HRField(karooSystem),
            AvgHRField(karooSystem),
            LapAvgHRField(karooSystem),
            LastLapAvgHRField(karooSystem),
            // Speed
            SpeedField(karooSystem),
            AvgSpeedField(karooSystem, includePaused = true),
            AvgSpeedField(karooSystem, includePaused = false),
            // Other
            CadenceField(karooSystem),
            GradeField(karooSystem),
            // Time
            TimeField(karooSystem, TimeKind.TOTAL),
            TimeField(karooSystem, TimeKind.RIDING),
            TimeField(karooSystem, TimeKind.PAUSED),
            TimeField(karooSystem, TimeKind.LAP),
            TimeField(karooSystem, TimeKind.LAST_LAP),
            ETAField(karooSystem, ETAKind.REMAINING_RIDE_TIME),
            ETAField(karooSystem, ETAKind.TIME_TO_DESTINATION),
            ETAField(karooSystem, ETAKind.TIME_OF_ARRIVAL),
            TimeField(karooSystem, TimeKind.TIME_TO_SUNRISE),
            TimeField(karooSystem, TimeKind.TIME_TO_SUNSET),
            TimeField(karooSystem, TimeKind.TIME_TO_CIVIL_DAWN),
            TimeField(karooSystem, TimeKind.TIME_TO_CIVIL_DUSK),
        )
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { Timber.d("Karoo system connected") }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }

    override fun startMap(emitter: Emitter<MapEffect>) {
        Timber.d("climber: startMap invoked")
        val polylineController = ClimbMapController()
        val chevronController = ClimbChevronController()
        val scope = CoroutineScope(Dispatchers.IO)
        val job: Job = scope.launch {
            // Branch A: config + nav (changes rarely — route load, settings edit).
            val configNavFlow = combine(
                applicationContext.streamClimberMapConfig(),
                applicationContext.streamZoneConfig(),
                applicationContext.streamElevationRenderConfig(),
                karooSystem.streamNavigationState(),
            ) { climberCfg, zoneCfg, renderCfg, navEvent ->
                ClimbMapConfigInputs(
                    enabled = climberCfg.enabled,
                    showChevrons = climberCfg.showChevrons,
                    palette = zoneCfg.gradePalette,
                    readable = zoneCfg.readableColors,
                    renderCfg = renderCfg,
                    state = navEvent.state,
                )
            }
            // Branch B: zoom + location (changes on every GPS tick / map interaction).
            // Debounce so rapid bursts coalesce; onStart seeds defaults so the combine
            // fires immediately on route load even before the first location fix.
            val viewportFlow = combine(
                karooSystem.consumerFlow<OnMapZoomLevel>()
                    .onStart { emit(OnMapZoomLevel(15.0)) },
                karooSystem.consumerFlow<OnLocationChanged>()
                    .onStart { emit(OnLocationChanged(0.0, 0.0, null)) },
            ) { zoom, loc -> ViewportInputs(zoom.zoomLevel, loc.lat, loc.lng) }

            configNavFlow.combine(viewportFlow) { cfg, vp -> cfg to vp }
                .distinctUntilChanged { prev, next ->
                    prev.first.signature() == next.first.signature() &&
                        prev.second.bucketedSignature() == next.second.bucketedSignature()
                }
                .collect { (inputs, viewport) ->
                    val route = inputs.state as? OnNavigationState.NavigationState.NavigatingRoute
                    if (!inputs.enabled || route == null) {
                        polylineController.clearAll(emitter)
                        chevronController.clearAll(emitter)
                        return@collect
                    }
                    val hasLocation = viewport.lat != 0.0 || viewport.lng != 0.0
                    val diagonal = mapDiagonalMeters(viewport.lat, viewport.lng, viewport.zoomLevel)
                    val chevronStep = if (hasLocation) {
                        (diagonal / CHEVRONS_PER_DIAGONAL).coerceAtLeast(20.0)
                    } else {
                        DEFAULT_CHEVRON_SPACING_M
                    }
                    // Viewport filtering disabled for now — the rideapp's IPC reordering
                    // between HideSymbols and ShowSymbols causes chevrons to vanish when
                    // the set shrinks rapidly (200 → 5). The bucketed distinctUntilChanged
                    // already prevents excessive rebuilds.
                    val bounds: com.jpweytjens.barberfish.datatype.shared.LatLngBounds? = null
                    val specs = buildClimbOverlaySpecs(
                        routePolyline = route.routePolyline,
                        routeElevationPolyline = route.routeElevationPolyline,
                        palette = inputs.palette,
                        readable = inputs.readable,
                        renderCfg = inputs.renderCfg,
                        includeChevrons = inputs.showChevrons,
                        chevronSpacingM = chevronStep,
                        chevronViewport = bounds,
                    )
                    Timber.d("climber: ${specs.polylines.size} polylines, ${specs.chevrons.size} chevrons (step=${chevronStep.toInt()}m zoom=${viewport.zoomLevel} loc=${viewport.lat},${viewport.lng} bounds=$bounds)")
                    polylineController.emit(emitter, specs.polylines, CLIMB_OVERLAY_WIDTH)
                    chevronController.emit(emitter, specs.chevrons)
                }
        }
        emitter.setCancellable {
            Timber.d("climber: startMap cancelled")
            job.cancel()
            scope.cancel()
        }
    }
}

private data class ClimbMapConfigInputs(
    val enabled: Boolean,
    val showChevrons: Boolean,
    val palette: GradePalette,
    val readable: Boolean,
    val renderCfg: ElevationRenderConfig,
    val state: OnNavigationState.NavigationState,
) {
    fun signature(): ClimbMapConfigSignature {
        val route = state as? OnNavigationState.NavigationState.NavigatingRoute
        return ClimbMapConfigSignature(
            enabled = enabled,
            showChevrons = showChevrons,
            palette = palette,
            readable = readable,
            simplification = renderCfg.simplification,
            skipBands = renderCfg.skipBands,
            routeElevationHash = route?.routeElevationPolyline?.hashCode() ?: 0,
            routePolylineHash = route?.routePolyline?.hashCode() ?: 0,
        )
    }
}

private data class ClimbMapConfigSignature(
    val enabled: Boolean,
    val showChevrons: Boolean,
    val palette: GradePalette,
    val readable: Boolean,
    val simplification: ElevationSimplification,
    val skipBands: Int,
    val routeElevationHash: Int,
    val routePolylineHash: Int,
)

private data class ViewportInputs(
    val zoomLevel: Double,
    val lat: Double,
    val lng: Double,
) {
    /** Bucket lat/lng to ~11 m and zoom to 0.5 so GPS jitter doesn't trigger rebuilds. */
    fun bucketedSignature() = ViewportSignature(
        zoomBucket = (zoomLevel * 2).toInt(),
        latBucket = (lat * 1e4).toLong(),
        lngBucket = (lng * 1e4).toLong(),
    )
}

private data class ViewportSignature(
    val zoomBucket: Int,
    val latBucket: Long,
    val lngBucket: Long,
)
