package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.datatype.AvgHRField
import com.jpweytjens.barberfish.datatype.AvgPowerField
import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.BarberfishBase
import com.jpweytjens.barberfish.datatype.CadenceField
import com.jpweytjens.barberfish.datatype.ETAField
import com.jpweytjens.barberfish.datatype.ETAKind
import com.jpweytjens.barberfish.datatype.EffortField
import com.jpweytjens.barberfish.datatype.ElevationSparklineField
import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.HRField
import com.jpweytjens.barberfish.datatype.HRMaxPercentField
import com.jpweytjens.barberfish.datatype.HRZoneField
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.LapAvgHRField
import com.jpweytjens.barberfish.datatype.LapPowerField
import com.jpweytjens.barberfish.datatype.LastLapAvgHRField
import com.jpweytjens.barberfish.datatype.MaxHRField
import com.jpweytjens.barberfish.datatype.MaxPowerField
import com.jpweytjens.barberfish.datatype.NPField
import com.jpweytjens.barberfish.datatype.PowerField
import com.jpweytjens.barberfish.datatype.PowerZoneField
import com.jpweytjens.barberfish.datatype.RouteRemainingField
import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.TimeField
import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.datatype.ValueField
import com.jpweytjens.barberfish.datatype.ValueKind
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.chevronIconLengthM
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.groundResolution
import com.jpweytjens.barberfish.datatype.shared.nativeChevronHeadingThresholdDeg
import com.jpweytjens.barberfish.datatype.shared.nativeChevronSpacingM
import com.jpweytjens.barberfish.datatype.shared.nativeChevronWindowHalfM
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.OnNavigationState
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber

private const val CLIMB_OVERLAY_WIDTH = 8          // coloured fill width; tune via screencaps

// Chevron icon height in dp — keep in sync with ic_climber_chevron*.xml. Drives the
// collision-dedup spacing so chevrons never overlap regardless of zoom.
private const val CHEVRON_ICON_HEIGHT_DP = 17f

// Zoom assumed until the map reports one, so the overlay builds on route load. Provisional:
// the first real zoom replaces it whatever band it lands in.
private const val SEED_ZOOM = 15.0

// Order matches extension_info.xml — keep in sync when adding fields.
// Top-level so the instrumented preview-render harness can iterate every field.
fun barberfishDataTypes(karooSystem: KarooSystemService): List<BarberfishBase<*>> =
    listOf(
        HUDField(karooSystem),
        // Power
        PowerField(karooSystem),
        AvgPowerField(karooSystem),
        NPField(karooSystem),
        LapPowerField(karooSystem, isLastLap = false),
        LapPowerField(karooSystem, isLastLap = true),
        PowerZoneField(karooSystem),
        MaxPowerField(karooSystem),
        // HR
        HRField(karooSystem),
        AvgHRField(karooSystem),
        LapAvgHRField(karooSystem),
        LastLapAvgHRField(karooSystem),
        HRMaxPercentField(karooSystem),
        MaxHRField(karooSystem),
        HRZoneField(karooSystem),
        // Speed
        SpeedField(karooSystem),
        AvgSpeedField(karooSystem, includePaused = true),
        AvgSpeedField(karooSystem, includePaused = false),
        // Other
        CadenceField(karooSystem),
        GradeField(karooSystem),
        ElevationSparklineField(karooSystem),
        // Distance & route-remaining
        ValueField(karooSystem, ValueKind.DISTANCE),
        ValueField(karooSystem, ValueKind.DISTANCE_REMAINING),
        ValueField(karooSystem, ValueKind.ELEVATION_REMAINING),
        ValueField(karooSystem, ValueKind.DESCENT_REMAINING),
        EffortField(karooSystem),
        RouteRemainingField(karooSystem),
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

class BarberfishExtension : KarooExtension("barberfish", BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService

    override val types by lazy { barberfishDataTypes(karooSystem) }

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
        Timber.d("grademap: startMap invoked")
        val polylineController = GradeMapController()
        val chevronController = GradeMapChevronController()
        val zoomBand = ChevronZoomBand()
        val xdpi = applicationContext.resources.displayMetrics.xdpi
        val density = applicationContext.resources.displayMetrics.density
        val scope = CoroutineScope(Dispatchers.IO)
        val job: Job = scope.launch {
            // Branch A: config + nav (changes rarely — route load, settings edit).
            val configNavFlow = combine(
                applicationContext.streamGradeMapConfig(),
                applicationContext.streamFieldSparklineConfig(),
                applicationContext.streamZoneConfig(),
                karooSystem.streamNavigationState(),
            ) { gradeMapCfg, sparklineCfg, zoneCfg, navEvent ->
                // Resolve sparkline-sync here so the signature() below hashes the
                // effective tuning and a sparkline edit triggers a rebuild while synced.
                val eff = resolveGradeMapTuning(gradeMapCfg, sparklineCfg, zoneCfg.gradePalette)
                val effectiveCfg = gradeMapCfg.copy(
                    skipBands = eff.skipBands,
                    simplification = eff.simplification,
                    climbEdge = eff.climbEdge,
                    descentEdge = eff.descentEdge,
                )
                GradeMapConfigInputs(
                    enabled = gradeMapCfg.enabled,
                    showChevrons = gradeMapCfg.showChevrons,
                    palette = zoneCfg.gradePalette,
                    cfg = effectiveCfg,
                    state = navEvent.state,
                )
            }
            // Branch B: zoom + location (changes on every GPS tick / map interaction).
            // onStart seeds defaults so the combine fires immediately on route load even
            // before the first location fix.
            //
            // The zoom is frozen per integer band before the combine, never inside it: the
            // band's provisional-seed rule must see one call per zoom emission, and a
            // location tick arriving with an unchanged zoom would otherwise spend it.
            val zoomFlow = karooSystem.consumerFlow<OnMapZoomLevel>()
                .map { it.zoomLevel }
                .onStart { emit(SEED_ZOOM) }
                .map { zoomBand.effectiveZoom(it) }
                .distinctUntilChanged()
            val viewportFlow = combine(
                zoomFlow,
                karooSystem.consumerFlow<OnLocationChanged>()
                    .onStart { emit(OnLocationChanged(0.0, 0.0, null)) },
            ) { zoom, loc ->
                ViewportInputs(zoom, loc.lat, loc.lng)
            }

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
                    // Spacing/window are zoom-driven; latitude only scales the cos(lat)
                    // term. Before the first GPS fix viewport.lat is 0.0 (equator) — the
                    // sparsest case — which is the safe direction to err. Once a fix lands
                    // it tightens to the true value.
                    val chevronStep = nativeChevronSpacingM(xdpi, viewport.lat, viewport.zoomLevel)
                    val chevronWindow = nativeChevronWindowHalfM(xdpi, viewport.lat, viewport.zoomLevel)
                    val chevronCollision =
                        chevronIconLengthM(CHEVRON_ICON_HEIGHT_DP, density, viewport.lat, viewport.zoomLevel)
                    val headingThreshold = nativeChevronHeadingThresholdDeg(viewport.zoomLevel)
                    // Viewport filtering disabled for now — the rideapp's IPC reordering
                    // between HideSymbols and ShowSymbols causes chevrons to vanish when
                    // the set shrinks rapidly (200 → 5). The bucketed distinctUntilChanged
                    // already prevents excessive rebuilds.
                    val bounds: com.jpweytjens.barberfish.datatype.shared.LatLngBounds? = null
                    // Climb.startDistance is measured from the rider's position including
                    // the off-route rejoin path; our elevation polyline is pure route
                    // distance (0..routeDistance). Subtract rejoinDistance to align them.
                    val rejoinOffset = route.rejoinDistance ?: 0.0
                    val climbRanges = route.climbs.map {
                        (it.startDistance - rejoinOffset) to
                            (it.startDistance + it.length - rejoinOffset)
                    }
                    // Round line-cap overhang per end = (width/2) px in ground metres.
                    // The overlay only re-emits on a band crossing, so this trim is fixed for
                    // the whole band while the rendered zoom moves across it. Centring on the
                    // band's midpoint bounds the error at about 1.41x either way instead of 2x.
                    val capTrimM = (CLIMB_OVERLAY_WIDTH / 2.0) *
                        groundResolution(viewport.lat, floor(viewport.zoomLevel) + 0.5)
                    val specs = buildGradeMapSpecs(
                        routePolyline = route.routePolyline,
                        routeElevationPolyline = route.routeElevationPolyline,
                        palette = inputs.palette,
                        readable = false,
                        cfg = inputs.cfg,
                        climbRanges = climbRanges,
                        includeChevrons = inputs.showChevrons,
                        chevronSpacingM = chevronStep,
                        chevronWindowHalfM = chevronWindow,
                        chevronHeadingThresholdDeg = headingThreshold,
                        chevronMinSpacingM = chevronCollision,
                        chevronViewport = bounds,
                        capTrimM = capTrimM,
                        reversed = route.reversed,
                    )
                    Timber.d("grademap: ${specs.polylines.size} polylines, ${specs.chevrons.size} chevrons (step=${chevronStep.toInt()}m window±${chevronWindow.toInt()}m collision=${chevronCollision.toInt()}m thresh=${headingThreshold.toInt()}° zoom=${viewport.zoomLevel} loc=${viewport.lat},${viewport.lng} bounds=$bounds palette=${inputs.palette} simpl=${inputs.cfg.simplification} skipBands=${inputs.cfg.skipBands})")
                    if (BuildConfig.DEBUG) {
                        val elev = decodeElevationPolyline(route.routeElevationPolyline ?: "")
                        Timber.d("grademap: routeDist=${route.routeDistance.toInt()}m rejoinDist=${route.rejoinDistance?.toInt()} reversed=${route.reversed} elevSpan=${elev.firstOrNull()?.first?.toInt()}..${elev.lastOrNull()?.first?.toInt()} climbs=${route.climbs.size} ranges=${climbRanges.map { "${it.first.toInt()}-${it.second.toInt()}" }}")
                        // Direction diagnostic. routePolyline always arrives in saved
                        // order, so gpsFirst/gpsLast are identical forward and reversed;
                        // segStart is what moves once the reversal is applied.
                        val gpsPts = decodeGpsPolyline(route.routePolyline)
                        val segStart = specs.polylines.firstOrNull()
                            ?.let { decodeGpsPolyline(it.encoded).firstOrNull() }
                        Timber.d("grademap: direction name=${route.name} reversed=${route.reversed} gpsFirst=${gpsPts.firstOrNull()} gpsLast=${gpsPts.lastOrNull()} segStart=$segStart elevFirst=${elev.firstOrNull()?.second} elevLast=${elev.lastOrNull()?.second}")
                        val segLen = specs.polylines
                            .map { decodeGpsPolyline(it.encoded) }
                            .map { if (it.size < 2) 0.0 else cumulativeDistancesM(it).last() }
                            .sorted()
                        Timber.d("grademap: segment lengths (m) min=${segLen.firstOrNull()?.toInt()} median=${segLen.getOrNull(segLen.size / 2)?.toInt()} max=${segLen.lastOrNull()?.toInt()} <collision=${segLen.count { it < chevronCollision }}")
                    }
                    if (inputs.cfg.showPolylines) {
                        polylineController.emit(emitter, specs.polylines, CLIMB_OVERLAY_WIDTH)
                    } else {
                        // Native route line shows through; we just drop our grade overlay.
                        polylineController.clearAll(emitter)
                    }
                    chevronController.emit(emitter, specs.chevrons)
                }
        }
        emitter.setCancellable {
            Timber.d("grademap: startMap cancelled")
            job.cancel()
            scope.cancel()
        }
    }
}

internal data class GradeMapConfigInputs(
    val enabled: Boolean,
    val showChevrons: Boolean,
    val palette: GradePalette,
    val cfg: GradeMapConfig,
    val state: OnNavigationState.NavigationState,
) {
    internal fun signature(): GradeMapConfigSignature {
        val route = state as? OnNavigationState.NavigationState.NavigatingRoute
        return GradeMapConfigSignature(
            enabled = enabled,
            showPolylines = cfg.showPolylines,
            showChevrons = showChevrons,
            palette = palette,
            simplification = cfg.simplification,
            skipBands = cfg.skipBands,
            climbEdge = cfg.climbEdge,
            descentEdge = cfg.descentEdge,
            routeElevationHash = route?.routeElevationPolyline?.hashCode() ?: 0,
            routePolylineHash = route?.routePolyline?.hashCode() ?: 0,
            climbsHash = route?.climbs?.hashCode() ?: 0,
            reversed = route?.reversed ?: false,
            // Bucket the rejoin offset to ~50 m so the filler tracks the rider riding the
            // rejoin path without rebuilding on every metre.
            rejoinBucket = ((route?.rejoinDistance ?: 0.0) / 50.0).toInt(),
        )
    }
}

internal data class GradeMapConfigSignature(
    val enabled: Boolean,
    val showPolylines: Boolean,
    val showChevrons: Boolean,
    val palette: GradePalette,
    val simplification: ElevationSimplification,
    val skipBands: Int,
    val climbEdge: Double?,
    val descentEdge: Double?,
    val routeElevationHash: Int,
    val routePolylineHash: Int,
    val climbsHash: Int,
    val reversed: Boolean,
    val rejoinBucket: Int,
)

private data class ViewportInputs(
    val zoomLevel: Double,
    val lat: Double,
    val lng: Double,
) {
    /** Bucket lat at 0.05 deg (~5.5 km). Location's only use in the rebuild is the
     *  cos(lat) term in the spacing math, which is insensitive below tens of km; lng
     *  is unused (log line only), so it stays out of the signature entirely. Zoom is
     *  already frozen per integer band at the flow source, so it needs no bucketing. */
    fun bucketedSignature() = ViewportSignature(
        zoomLevel = zoomLevel,
        latBucket = (lat / 0.05).toLong(),
    )
}

private data class ViewportSignature(
    val zoomLevel: Double,
    val latBucket: Long,
)
