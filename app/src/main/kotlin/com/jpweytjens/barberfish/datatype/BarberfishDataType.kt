package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.util.Log
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.toViewSizeConfig
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
abstract class BarberfishDataType(extensionId: String, typeId: String) :
    DataTypeImpl(extensionId, typeId) {

    /** Throttle applied to [liveFlow] emissions. Override for slower fields (e.g. 1000L). */
    open val sampleMs: Long = 400L

    /** Emits FieldStates from real sensor streams. Must not sample internally. */
    abstract fun liveFlow(context: Context): Flow<FieldState>

    /** Emits FieldStates for the Karoo config-screen preview carousel. */
    abstract fun previewFlow(context: Context): Flow<FieldState>

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val density = context.resources.displayMetrics.density
        val cellHeightDp = config.viewSize.second / density
        val sizeConfig = config.toViewSizeConfig()
        Log.d("Barberfish", "density=$density cellH=${cellHeightDp}dp textSize=${config.textSize}sp gridSize=${config.gridSize} → headerSp=${sizeConfig.headerFontSize} typeId=$typeId")
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow =
                if (config.preview) previewFlow(context) else liveFlow(context).sample(sampleMs)
            flow.collect { field ->
                val rv = barberfishFieldRemoteViews(
                    field = field,
                    alignment = config.alignment,
                    colorMode = field.colorMode,
                    sizeConfig = sizeConfig,
                    preview = config.preview,
                    wideLayout = config.gridSize.first == 60,
                    cellWidthPx = config.viewSize.first,
                    context = context,
                )
                emitter.updateView(rv)
            }
        }
    }
}
