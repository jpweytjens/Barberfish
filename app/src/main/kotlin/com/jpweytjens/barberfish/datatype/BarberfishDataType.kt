package com.jpweytjens.barberfish.datatype

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
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

/** Extract the numeric value for [fieldKey] from a [StreamState], or null if not streaming. */
fun StreamState.streamingValue(fieldKey: String): Double? =
    (this as? StreamState.Streaming)?.dataPoint?.values?.get(fieldKey)

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

    /** Emits FieldValues from real sensor streams. Must not sample internally. */
    abstract fun liveFlow(context: Context): Flow<FieldValue>

    /** Emits FieldValues for the Karoo config-screen preview carousel. */
    abstract fun previewFlow(context: Context): Flow<FieldValue>

    /** Renders the Glance UI for one FieldValue. Default: plain BarberfishView. */
    @Composable
    open fun Content(field: FieldValue, config: ViewConfig) {
        BarberfishView(field, config.alignment)
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = true))
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
