package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.graphics.BitmapFactory
import android.os.DeadObjectException

import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews

import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.Job
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter

import com.jpweytjens.barberfish.extensions.streamDoubleFieldSettings
import com.jpweytjens.barberfish.extensions.streamGeneralSettings
import com.jpweytjens.barberfish.R

import com.jpweytjens.barberfish.extensions.streamUserProfile
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.HardwareType
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig

import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel

import kotlinx.coroutines.flow.catch

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

import timber.log.Timber

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random


@OptIn(ExperimentalGlanceRemoteViewsApi::class)
abstract class CustomDoubleTypeBase(
    private val karooSystem: KarooSystemService,
    datatype: String,
    private val globalIndex: Int
) : DataTypeImpl("kcustomfield", datatype) {


    private val glance = GlanceRemoteViews()
    private val firstField = { settings: DoubleFieldSettings -> settings.onefield }
    private val secondField = { settings: DoubleFieldSettings -> settings.secondfield }
    private val ishorizontal = { settings: DoubleFieldSettings -> settings.ishorizontal }

    private val refreshTime: Long
        get() = when (karooSystem.hardwareType) {
            HardwareType.K2 -> RefreshTime.MID.time
            else -> RefreshTime.HALF.time
        }.coerceAtLeast(100L)

    

    private fun previewFlow(): Flow<StreamState> = flow {
        while (true) {
            emit(StreamState.Streaming(
                DataPoint(
                    dataTypeId,
                    mapOf(DataType.Field.SINGLE to (0..100).random().toDouble()),
                    extension
                )
            ))
            delay(Delay.PREVIEW.time)
        }
    }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("DOUBLE StartView: field $extension index $globalIndex field $dataTypeId config: $config emitter: $emitter")

        val scopeJob = Job()
        val scope = CoroutineScope(Dispatchers.IO + scopeJob)
        ViewState.setCancelled(false)

        val dataflow = context.streamDoubleFieldSettings()
            .onStart {
                Timber.d("Iniciando streamDoubleFieldSettings")
                emit(previewDoubleFieldSettings as MutableList<DoubleFieldSettings>)
            }
            .combine(
                context.streamGeneralSettings()
                    .onStart {
                        Timber.d("Iniciando streamGeneralSettings")
                        emit(GeneralSettings())
                    }
            ) { settings, generalSettings ->
                settings to generalSettings
            }.combine(
                karooSystem.streamUserProfile()

            ) { (settings, generalSettings), userProfile ->
                GlobalConfigState(settings, generalSettings, userProfile)
            }



        val configjob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }

        val baseBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.circle)
        val viewjob = scope.launch {
            try {
                Timber.d("DOUBLE Starting view: $extension $globalIndex ")

                try {
                    if (!config.preview) {
                            try {
                                val initialRemoteViews = withContext(Dispatchers.Main) {
                                    glance.compose(context, DpSize.Unspecified) {
                                        NotSupported("Searching ...",21)
                                    }.remoteViews
                                }
                                withContext(Dispatchers.Main) {
                                    emitter.updateView(initialRemoteViews)
                                }

                            } catch (e: Exception) {
                                Timber.e(e, "DOUBLE Error en vista inicial: $extension $globalIndex ")
                            }
                        delay(400L + (Random.nextInt(10) * 150L))

                    }

                    Timber.d("DOUBLE Starting view flow: $extension $globalIndex  karooSystem@$karooSystem ")


                    dataflow.flatMapLatest { state ->
                            val (settings, generalSettings, userProfile) = state

                            if (userProfile == null) {
                                Timber.d("DOUBLE UserProfile no disponible")
                                return@flatMapLatest flowOf(Triple(
                                    StreamState.Searching,
                                    StreamState.Searching,
                                    state
                                ))
                            }

                            val currentSettings = settings.getOrNull(globalIndex)
                                ?: throw IndexOutOfBoundsException("Invalid index $globalIndex")

                            val primaryField = firstField(currentSettings)
                            val secondaryField = secondField(currentSettings)

                            val headwindFlow =
                                if (listOf(primaryField, secondaryField).any { it.kaction.name == "HEADWIND" } && generalSettings.isheadwindenabled)
                                    createHeadwindFlow(karooSystem, refreshTime) else flowOf(StreamHeadWindData(0.0, 0.0))

                            val firstFieldFlow = if (!config.preview) karooSystem.getFieldFlow(primaryField, headwindFlow, generalSettings) else previewFlow()
                            val secondFieldFlow = if (!config.preview) karooSystem.getFieldFlow( secondaryField, headwindFlow, generalSettings) else previewFlow()

                            combine(firstFieldFlow, secondFieldFlow) { firstState, secondState ->
                                Triple(firstState, secondState, state)
                            }
                    }.onEach { (firstFieldState, secondFieldState, globalConfig) ->

                        if ( ViewState.isCancelled()) {
                            Timber.d("DOUBLE Skipping update, job cancelled: $extension $globalIndex")
                            return@onEach
                        }

                            val (setting, generalSettings, userProfile) = globalConfig

                            if (userProfile == null) {
                                Timber.d("UserProfile no disponible")
                                return@onEach
                            }
                            val settings = setting[globalIndex]

                            val (firstvalue, firstIconcolor, firstColorzone, isleftzone, firstvalueRight) = getFieldState(
                                firstFieldState,
                                firstField(settings),
                                context,
                                userProfile,
                                generalSettings.ispalettezwift
                            )

                            val (secondvalue, secondIconcolor, secondColorzone, isrightzone, secondvalueRight) = getFieldState(
                                secondFieldState,
                                secondField((settings)),
                                context,
                                userProfile,
                                generalSettings.ispalettezwift
                            )

                            val (winddiff, windtext) = if (firstFieldState !is StreamState || secondFieldState !is StreamState) {
                                val windData = (firstFieldState as? StreamHeadWindData)
                                    ?: (secondFieldState as StreamHeadWindData)
                                windData.diff to windData.windSpeed.roundToInt().toString()
                            } else 0.0 to ""

                            val fieldNumber = when {
                                firstFieldState is StreamState && secondFieldState is StreamState -> 3
                                firstFieldState is StreamState -> 0
                                secondFieldState is StreamState -> 1
                                else -> 2
                            }

                            val clayout = when {
                                generalSettings.iscenterkaroo -> when (config.alignment) {
                                    ViewConfig.Alignment.CENTER -> FieldPosition.CENTER
                                    ViewConfig.Alignment.LEFT -> FieldPosition.LEFT
                                    ViewConfig.Alignment.RIGHT -> FieldPosition.RIGHT
                                }
                                ishorizontal(settings) -> generalSettings.iscenteralign
                                else -> generalSettings.iscentervertical
                            }

                            try {
                                if ( ViewState.isCancelled()) {
                                    Timber.d("DOUBLE Skipping composition, job cancelled: $extension $globalIndex")
                                    return@onEach
                                }
                                val newView = withContext(Dispatchers.Main) {
                                    if ( ViewState.isCancelled()) {
                                        return@withContext null
                                    }
                                    glance.compose(context, DpSize.Unspecified) {
                                        DoubleScreenSelector(
                                            fieldNumber,
                                            ishorizontal(settings),
                                            firstvalue,
                                            secondvalue,
                                            firstField(settings),
                                            secondField(settings),
                                            firstIconcolor,
                                            secondIconcolor,
                                            firstColorzone,
                                            secondColorzone,
                                            getFieldSize(config.gridSize.second),
                                            karooSystem.hardwareType == HardwareType.KAROO,
                                            clayout,
                                            windtext,
                                            winddiff.roundToInt(),
                                            baseBitmap,
                                            generalSettings.isdivider,
                                            firstvalueRight,
                                            secondvalueRight
                                        )
                                    }.remoteViews
                                }
                                if (newView == null) return@onEach

                                Timber.d("DOUBLE Updating view: $extension $globalIndex values: $firstvalue, $secondvalue layout: $clayout")
                                withContext(Dispatchers.Main) {
                                    if ( ViewState.isCancelled()) return@withContext
                                    emitter.updateView(newView)
                                }
                                delay(refreshTime)
                            } catch (e: Exception) {
                                if (e is CancellationException) {
                                    Timber.d("DOUBLE View update cancelled normally: $extension $globalIndex")
                                } else {
                                    Timber.e(e, "DOUBLE Error composing/updating view: $extension $globalIndex")
                                    if (coroutineContext.isActive && !ViewState.isCancelled()) {
                                        throw e
                                    }
                                }
                            }
                        }
                        .catch { e ->
                            when (e) {
                                is CancellationException -> {
                                    Timber.d("DOUBLE Flow cancelled: $extension $globalIndex")
                                    throw e
                                }
                                else -> {
                                    Timber.e(e, "DOUBLE Flow error: $extension $globalIndex")
                                    throw e
                                }
                            }
                        }
                        .retryWhen { cause, attempt ->

                            when {

                                cause is CancellationException && ViewState.isCancelled() -> {
                                    Timber.d("DOUBLE No se reintenta el flujo cancelado por el emitter: $extension $globalIndex")
                                    false
                                }
                                attempt > 4 -> {
                                    Timber.e(cause, "DOUBLE Max retries reached: $extension $globalIndex (attempt $attempt)")
                                    delay(Delay.RETRY_LONG.time)
                                    true
                                }
                                else -> {
                                    Timber.w(cause, "DOUBLE Retrying flow: $extension $globalIndex (attempt $attempt)")
                                    delay(Delay.RETRY_SHORT.time)
                                    true
                                }
                            }

                        }
                        .launchIn(scope)

                } catch (e: CancellationException) {
                    Timber.d("DOUBLE View operation cancelled: $extension $globalIndex ")
                    throw e
                }
                catch (e: DeadObjectException) {
                    Timber.e(e, "ROLLING Dead object en vista principal, parando")
                    scope.cancel()
                }

          } catch (e: Exception) {
                Timber.e(e, "DOUBLE ViewJob error: $extension $globalIndex ")
                if (!scope.isActive) return@launch
                delay(1000L)

            }
        }

        emitter.setCancellable {
            try {
                Timber.d("Iniciando cancelación de CustomDoubleTypeBase")


                ViewState.setCancelled(true)

                configjob.cancel()
                viewjob.cancel()


                scope.launch {
                    delay(100)
                    if (scope.isActive) {
                        Timber.w("Forzando cancelación del scope de double")
                    }
                }

                scope.cancel()
                scopeJob.cancel()

                Timber.d("Cancelación de CustomDoubleTypeBase completada")

            } catch (e: CancellationException) {

            } catch (e: Exception) {
                Timber.e(e, "Error durante la cancelación")
            }

        }
    }
}