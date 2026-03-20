package com.jpweytjens.barberfish.datatype

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.jpweytjens.barberfish.datatype.shared.FieldState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalGlanceRemoteViewsApi::class,
    ExperimentalCoroutinesApi::class,
    FlowPreview::class,
)
abstract class BarberfishDataType(extensionId: String, typeId: String) :
    DataTypeImpl(extensionId, typeId) {

    protected val glance = GlanceRemoteViews()

    /** Throttle applied to [liveFlow] emissions. Override for slower fields (e.g. 1000L). */
    open val sampleMs: Long = 400L

    /** Emits FieldStates from real sensor streams. Must not sample internally. */
    abstract fun liveFlow(context: Context): Flow<FieldState>

    /** Emits FieldStates for the Karoo config-screen preview carousel. */
    abstract fun previewFlow(context: Context): Flow<FieldState>

    /** Renders the Glance UI for one FieldState. Default: plain BarberfishView. */
    @Composable
    open fun Content(field: FieldState, config: ViewConfig) {
        BarberfishView(field, config.alignment, field.colorMode)
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow =
                if (config.preview) previewFlow(context) else liveFlow(context).sample(sampleMs)
            flow.collect { field ->
                val result = glance.compose(context, DpSize.Unspecified) { Content(field, config) }
                emitter.updateView(result.remoteViews)
            }
        }
    }
}
