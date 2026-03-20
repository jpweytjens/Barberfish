package com.jpweytjens.barberfish.datatype

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.jpweytjens.barberfish.datatype.shared.HudState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlanceRemoteViewsApi::class, FlowPreview::class)
abstract class HUDDataType(extensionId: String, typeId: String) :
    DataTypeImpl(extensionId, typeId) {

    protected val glance = GlanceRemoteViews()

    /** Throttle applied to [liveFlow] emissions. */
    open val sampleMs: Long = 400L

    /** Emits HudState from real sensor streams. Must not sample internally. */
    abstract fun liveFlow(context: Context): Flow<HudState>

    /** Emits HudState for the Karoo config-screen preview carousel. */
    abstract fun previewFlow(context: Context): Flow<HudState>

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow =
                if (config.preview) previewFlow(context) else liveFlow(context).sample(sampleMs)
            flow.collect { state ->
                val result =
                    glance.compose(context, DpSize.Unspecified) {
                        Row(
                            modifier =
                                GlanceModifier.fillMaxSize().padding(vertical = 2.dp).let {
                                    if (config.preview) it.cornerRadius(8.dp) else it
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BarberfishView(
                                state.speed,
                                config.alignment,
                                state.colorMode,
                                ViewSizeConfig.HUD,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            BarberfishView(
                                state.hr,
                                config.alignment,
                                state.colorMode,
                                ViewSizeConfig.HUD,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            BarberfishView(
                                state.power,
                                config.alignment,
                                state.colorMode,
                                ViewSizeConfig.HUD,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }
                emitter.updateView(result.remoteViews)
            }
        }
    }
}
