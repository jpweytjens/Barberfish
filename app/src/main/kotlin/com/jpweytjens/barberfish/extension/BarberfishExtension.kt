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
import com.jpweytjens.barberfish.datatype.shared.buildClimbPolylineSpecs
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

private const val CLIMB_OVERLAY_WIDTH = 8           // coloured fill width; tune via screencaps

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
        val controller = ClimbMapController()
        val scope = CoroutineScope(Dispatchers.IO)
        val job: Job = scope.launch {
            combine(
                applicationContext.streamClimberMapConfig(),
                applicationContext.streamZoneConfig(),
                applicationContext.streamElevationRenderConfig(),
                karooSystem.streamNavigationState(),
            ) { climberCfg, zoneCfg, renderCfg, navEvent ->
                ClimbMapInputs(
                    enabled = climberCfg.enabled,
                    palette = zoneCfg.gradePalette,
                    readable = zoneCfg.readableColors,
                    renderCfg = renderCfg,
                    state = navEvent.state,
                )
            }
                .distinctUntilChanged { prev, next -> prev.signature() == next.signature() }
                .collect { inputs ->
                    val route = inputs.state as? OnNavigationState.NavigationState.NavigatingRoute
                    Timber.d(
                        "climber: collect enabled=${inputs.enabled} " +
                            "stateType=${inputs.state::class.simpleName} " +
                            "routeLen=${route?.routePolyline?.length ?: -1} " +
                            "elevLen=${route?.routeElevationPolyline?.length ?: -1}",
                    )
                    if (!inputs.enabled || route == null) {
                        Timber.d("climber: clearing (enabled=${inputs.enabled}, route=${route != null})")
                        controller.clearAll(emitter)
                        return@collect
                    }
                    val specs = buildClimbPolylineSpecs(
                        routePolyline = route.routePolyline,
                        routeElevationPolyline = route.routeElevationPolyline,
                        palette = inputs.palette,
                        readable = inputs.readable,
                        renderCfg = inputs.renderCfg,
                    )
                    Timber.d("climber: built ${specs.size} specs (palette=${inputs.palette} readable=${inputs.readable} simpl=${inputs.renderCfg.simplification} skipBands=${inputs.renderCfg.skipBands})")
                    controller.emit(emitter, specs, CLIMB_OVERLAY_WIDTH)
                }
        }
        emitter.setCancellable {
            Timber.d("climber: startMap cancelled")
            job.cancel()
            scope.cancel()
        }
    }
}

private data class ClimbMapInputs(
    val enabled: Boolean,
    val palette: GradePalette,
    val readable: Boolean,
    val renderCfg: ElevationRenderConfig,
    val state: OnNavigationState.NavigationState,
) {
    fun signature(): ClimbMapSignature {
        val route = state as? OnNavigationState.NavigationState.NavigatingRoute
        return ClimbMapSignature(
            enabled = enabled,
            palette = palette,
            readable = readable,
            simplification = renderCfg.simplification,
            skipBands = renderCfg.skipBands,
            routeElevationHash = route?.routeElevationPolyline?.hashCode() ?: 0,
            routePolylineHash = route?.routePolyline?.hashCode() ?: 0,
        )
    }
}

private data class ClimbMapSignature(
    val enabled: Boolean,
    val palette: GradePalette,
    val readable: Boolean,
    val simplification: ElevationSimplification,
    val skipBands: Int,
    val routeElevationHash: Int,
    val routePolylineHash: Int,
)
