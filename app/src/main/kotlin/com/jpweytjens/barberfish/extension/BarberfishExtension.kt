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
import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.GradeMapProgress
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.chevronIconLengthM
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.gradeMapRouteKey
import com.jpweytjens.barberfish.datatype.shared.groundResolution
import com.jpweytjens.barberfish.datatype.shared.metresPerPixel
import com.jpweytjens.barberfish.datatype.shared.nativeChevronHeadingThresholdDeg
import com.jpweytjens.barberfish.datatype.shared.nativeChevronSpacingM
import com.jpweytjens.barberfish.datatype.shared.nativeChevronWindowHalfM
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber

private const val CLIMB_OVERLAY_WIDTH = 8 // coloured fill width; tune via screencaps

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
            // The rideapp keeps drawn map symbols across extension process death and startMap
            // restarts, and this generation's controllers know nothing about them. Seed the
            // controllers with the id ranges the last emission minted, so their first diff
            // hides whatever a dead predecessor left painted.
            var drawnIdSpans = applicationContext.streamGradeMapDrawnIdSpans().first()
            polylineController.assumeStale(drawnIdSpans.segments)
            chevronController.assumeStale(drawnIdSpans.chevrons)
            // Invariant: the persisted spans always cover the painted ids, whatever instant
            // the process dies. Raised to the ceiling of old and new before a draw, settled
            // to the exact spans after it, zeroed after a clear.
            suspend fun persistDrawnIdSpans(spans: GradeMapDrawnIdSpans) {
                if (spans != drawnIdSpans) {
                    applicationContext.saveGradeMapDrawnIdSpans(spans)
                    drawnIdSpans = spans
                }
            }
            // Branch A: config + nav (changes rarely — route load, settings edit).
            val configNavFlow =
                combine(
                    applicationContext.streamGradeMapConfig(),
                    applicationContext.streamFieldSparklineConfig(),
                    applicationContext.streamZoneConfig(),
                    karooSystem.streamNavigationState(),
                ) { gradeMapCfg, sparklineCfg, zoneCfg, navEvent ->
                    // Resolve sparkline-sync here so the signature() below hashes the
                    // effective tuning and a sparkline edit triggers a rebuild while synced.
                    val eff = resolveGradeMapTuning(gradeMapCfg, sparklineCfg, zoneCfg.gradePalette)
                    GradeMapConfigInputs(
                        enabled = gradeMapCfg.enabled,
                        showPolylines = gradeMapCfg.showPolylines,
                        showChevrons = gradeMapCfg.showChevrons,
                        palette = zoneCfg.gradePalette,
                        tuning = eff,
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
            val zoomFlow =
                karooSystem
                    .consumerFlow<OnMapZoomLevel>()
                    .map { it.zoomLevel }
                    .onStart { emit(SEED_ZOOM) }
                    .map { zoomBand.effectiveZoom(it) }
                    .distinctUntilChanged()
            val viewportFlow =
                combine(
                    zoomFlow,
                    karooSystem.consumerFlow<OnLocationChanged>().onStart {
                        emit(OnLocationChanged(0.0, 0.0, null))
                    },
                ) { zoom, loc ->
                    ViewportInputs(zoom, loc.lat, loc.lng)
                }

            val progressLatch = GradeMapProgress()
            var lastRoute: OnNavigationState.NavigationState.NavigatingRoute? = null
            // Distance samples carry no route identity, so a sample queued behind a route
            // change was computed against the old route; fed to the fresh latch it would
            // commit a bogus monotonic jump on the new one. Hold ticks briefly after every
            // reset until the stream reflects the new route.
            var progressSettleUntilMs = 0L
            val rebuildFlow =
                configNavFlow
                    .combine(viewportFlow) { cfg, vp -> GradeMapRebuild(cfg, vp) }
                    .distinctUntilChanged { prev, next ->
                        prev.inputs.signature() == next.inputs.signature() &&
                            prev.viewport.bucketedSignature() == next.viewport.bucketedSignature()
                    }
            // Progress alone never triggers a rebuild: a tick costs a HideSymbols batch, nothing
            // else. The latch's bucketing keeps ticks rare; extra samples return false and stop
            // here.
            val progressFlow =
                karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION).map { state ->
                    val streaming = state as? StreamState.Streaming
                    GradeMapProgressTick(
                        distanceToDestinationM =
                            streaming
                                ?.dataPoint
                                ?.values
                                ?.get(DataType.Field.DISTANCE_TO_DESTINATION),
                        onRoute =
                            streaming?.dataPoint?.values?.get(DataType.Field.ON_ROUTE)?.let {
                                it >= 0.5
                            } ?: true,
                    )
                }
            merge(rebuildFlow, progressFlow).collect { event ->
                when (event) {
                    is GradeMapProgressTick -> {
                        val route = lastRoute ?: return@collect
                        if (System.currentTimeMillis() < progressSettleUntilMs) return@collect
                        val advanced =
                            progressLatch.advance(
                                event.distanceToDestinationM,
                                event.onRoute,
                                route.routeDistance,
                            )
                        if (advanced) {
                            Timber.d("grademap: progress=${progressLatch.progressM.toInt()}m")
                            chevronController.hidePassed(emitter, progressLatch.progressM)
                        }
                    }
                    is GradeMapRebuild -> {
                        val inputs = event.inputs
                        val viewport = event.viewport
                        val route =
                            inputs.state as? OnNavigationState.NavigationState.NavigatingRoute
                        // Keep the progress branch's view of the route current.
                        lastRoute = route?.takeIf { inputs.enabled }
                        if (!inputs.enabled || route == null) {
                            // Navigation cleared forgets progress; a mere disable keeps the
                            // latch, since the route identity is unchanged.
                            if (route == null) progressLatch.clear()
                            polylineController.clearAll(emitter)
                            chevronController.clearAll(emitter)
                            persistDrawnIdSpans(GradeMapDrawnIdSpans())
                            return@collect
                        }
                        // Reset the latch when the route identity changes.
                        if (
                            progressLatch.trackRoute(
                                gradeMapRouteKey(route.routePolyline, route.reversed)
                            )
                        ) {
                            progressSettleUntilMs = System.currentTimeMillis() + PROGRESS_SETTLE_MS
                        }
                        // Spacing/window are zoom-driven; latitude only scales the cos(lat)
                        // term. Before the first GPS fix viewport.lat is 0.0 (equator) — the
                        // sparsest case — which is the safe direction to err. Once a fix lands
                        // it tightens to the true value.
                        val chevronStep =
                            nativeChevronSpacingM(xdpi, viewport.lat, viewport.zoomLevel)
                        val chevronWindow =
                            nativeChevronWindowHalfM(xdpi, viewport.lat, viewport.zoomLevel)
                        val chevronCollision =
                            chevronIconLengthM(
                                CHEVRON_ICON_HEIGHT_DP,
                                density,
                                viewport.lat,
                                viewport.zoomLevel,
                            )
                        val headingThreshold = nativeChevronHeadingThresholdDeg(viewport.zoomLevel)
                        // Viewport filtering disabled for now — the rideapp's IPC reordering
                        // between HideSymbols and ShowSymbols causes chevrons to vanish when
                        // the set shrinks rapidly (200 → 5). The bucketed distinctUntilChanged
                        // already prevents excessive rebuilds.
                        val bounds: com.jpweytjens.barberfish.datatype.shared.LatLngBounds? = null
                        // Diagnostic only — buildGradeMapSpecs colours from the elevation
                        // polyline and ignores climbs. Logged raw, matching what
                        // SparklineDataFlow consumes; the reference frame of reroute-time
                        // climb distances is unverified, so no correction is applied.
                        val climbRanges =
                            route.climbs.map {
                                it.startDistance to (it.startDistance + it.length)
                            }
                        // Round line-cap overhang per end = (width/2) px in ground metres.
                        // The overlay only re-emits on a band crossing, so this trim is fixed for
                        // the whole band while the rendered zoom moves across it. Centring on the
                        // band's midpoint bounds the error at about 1.41x either way instead of 2x.
                        val capTrimM =
                            (CLIMB_OVERLAY_WIDTH / 2.0) *
                                groundResolution(viewport.lat, floor(viewport.zoomLevel) + 0.5)
                        val specs =
                            buildGradeMapSpecs(
                                routePolyline = route.routePolyline,
                                routeElevationPolyline = route.routeElevationPolyline,
                                palette = inputs.palette,
                                readable = false,
                                tuning = inputs.tuning,
                                includeChevrons = inputs.showChevrons,
                                chevronSpacingM = chevronStep,
                                chevronWindowHalfM = chevronWindow,
                                chevronHeadingThresholdDeg = headingThreshold,
                                chevronMinSpacingM = chevronCollision,
                                chevronViewport = bounds,
                                capTrimM = capTrimM,
                                reversed = route.reversed,
                                metresPerPixel = metresPerPixel(viewport.zoomLevel),
                            )
                        Timber.d(
                            "grademap: ${specs.polylines.size} polylines, ${specs.chevrons.size} chevrons (step=${chevronStep.toInt()}m window±${chevronWindow.toInt()}m collision=${chevronCollision.toInt()}m thresh=${headingThreshold.toInt()}° zoom=${viewport.zoomLevel} loc=${viewport.lat},${viewport.lng} bounds=$bounds palette=${inputs.palette} simpl=${inputs.tuning.simplification} climbEdge=${inputs.tuning.climbEdge} descentEdge=${inputs.tuning.descentEdge})"
                        )
                        if (BuildConfig.DEBUG) {
                            val elev = decodeElevationPolyline(route.routeElevationPolyline ?: "")
                            Timber.d(
                                "grademap: routeDist=${route.routeDistance.toInt()}m rejoinDist=${route.rejoinDistance?.toInt()} reversed=${route.reversed} elevSpan=${elev.firstOrNull()?.first?.toInt()}..${elev.lastOrNull()?.first?.toInt()} climbs=${route.climbs.size} ranges=${climbRanges.map { "${it.first.toInt()}-${it.second.toInt()}" }}"
                            )
                            // Direction diagnostic. routePolyline always arrives in saved
                            // order, so gpsFirst/gpsLast are identical forward and reversed;
                            // segStart is what moves once the reversal is applied.
                            val gpsPts = decodeGpsPolyline(route.routePolyline)
                            val segStart =
                                specs.polylines.firstOrNull()?.let {
                                    decodeGpsPolyline(it.encoded).firstOrNull()
                                }
                            Timber.d(
                                "grademap: direction name=${route.name} reversed=${route.reversed} gpsFirst=${gpsPts.firstOrNull()} gpsLast=${gpsPts.lastOrNull()} segStart=$segStart elevFirst=${elev.firstOrNull()?.second} elevLast=${elev.lastOrNull()?.second}"
                            )
                            val segLen =
                                specs.polylines
                                    .map { decodeGpsPolyline(it.encoded) }
                                    .map {
                                        if (it.size < 2) 0.0 else cumulativeDistancesM(it).last()
                                    }
                                    .sorted()
                            Timber.d(
                                "grademap: segment lengths (m) min=${segLen.firstOrNull()?.toInt()} median=${segLen.getOrNull(segLen.size / 2)?.toInt()} max=${segLen.lastOrNull()?.toInt()} <collision=${segLen.count { it < chevronCollision }}"
                            )
                        }
                        val newSpans =
                            GradeMapDrawnIdSpans(
                                segments = if (inputs.showPolylines) specs.segmentIdSpan else 0,
                                chevrons = specs.chevronIdSpan,
                            )
                        persistDrawnIdSpans(
                            GradeMapDrawnIdSpans(
                                segments = maxOf(drawnIdSpans.segments, newSpans.segments),
                                chevrons = maxOf(drawnIdSpans.chevrons, newSpans.chevrons),
                            )
                        )
                        if (inputs.showPolylines) {
                            polylineController.emit(emitter, specs.polylines, CLIMB_OVERLAY_WIDTH)
                        } else {
                            // Native route line shows through; we just drop our grade overlay.
                            polylineController.clearAll(emitter)
                        }
                        // A rebuild must not resurrect chevrons the rider already passed.
                        val visibleChevrons =
                            specs.chevrons.filter { it.distanceM >= progressLatch.progressM }
                        chevronController.emit(emitter, visibleChevrons)
                        persistDrawnIdSpans(newSpans)
                    }
                }
            }
        }
        emitter.setCancellable {
            Timber.d("grademap: startMap cancelled")
            job.cancel()
            scope.cancel()
        }
    }
}

// How long progress ticks are held after a route change, while the distance stream may
// still deliver samples computed against the previous route.
private const val PROGRESS_SETTLE_MS = 2_000L

// The two things that can touch the map, merged into one serially-collected flow so the
// single-consumer controllers never see concurrent calls.
private sealed interface GradeMapEvent

private data class GradeMapRebuild(
    val inputs: GradeMapConfigInputs,
    val viewport: ViewportInputs,
) : GradeMapEvent

private data class GradeMapProgressTick(
    val distanceToDestinationM: Double?,
    val onRoute: Boolean,
) : GradeMapEvent

internal data class GradeMapConfigInputs(
    val enabled: Boolean,
    val showPolylines: Boolean,
    val showChevrons: Boolean,
    val palette: GradePalette,
    // Sparkline-sync is resolved upstream, so everything downstream — the rebuild signature
    // and the specs themselves — reads one already-effective tuning.
    val tuning: EffectiveGradeMapTuning,
    val state: OnNavigationState.NavigationState,
) {
    internal fun signature(): GradeMapConfigSignature {
        val route = state as? OnNavigationState.NavigationState.NavigatingRoute
        return GradeMapConfigSignature(
            enabled = enabled,
            showPolylines = showPolylines,
            showChevrons = showChevrons,
            palette = palette,
            simplification = tuning.simplification,
            skipBands = tuning.skipBands,
            climbEdge = tuning.climbEdge,
            descentEdge = tuning.descentEdge,
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
    /**
     * Bucket lat at 0.05 deg (~5.5 km). Location's only use in the rebuild is the cos(lat) term in
     * the spacing math, which is insensitive below tens of km; lng is unused (log line only), so it
     * stays out of the signature entirely. Zoom is already frozen per integer band at the flow
     * source, so it needs no bucketing.
     */
    fun bucketedSignature() =
        ViewportSignature(
            zoomLevel = zoomLevel,
            latBucket = (lat / 0.05).toLong(),
        )
}

private data class ViewportSignature(
    val zoomLevel: Double,
    val latBucket: Long,
)
