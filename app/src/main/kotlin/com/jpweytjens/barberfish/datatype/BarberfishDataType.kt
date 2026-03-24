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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BarberfishDataType(extensionId: String, typeId: String) :
    DataTypeImpl(extensionId, typeId) {

    /** Emits FieldStates from real sensor streams. */
    abstract fun liveFlow(context: Context): Flow<FieldState>

    /** Emits FieldStates for the Karoo config-screen preview carousel. */
    abstract fun previewFlow(context: Context): Flow<FieldState>

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val density = context.resources.displayMetrics.density
        val cellHeightDp = config.viewSize.second / density
        val sizeConfig = config.toViewSizeConfig()
        val cellWidthPx = config.viewSize.first
        Log.d("Barberfish", "density=$density cellH=${cellHeightDp}dp cellW=${cellWidthPx}px textSize=${config.textSize}sp gridSize=${config.gridSize} → headerSp=${sizeConfig.headerFontSize} typeId=$typeId")
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow =
                if (config.preview) previewFlow(context) else liveFlow(context)
            flow.collect { field ->
                val rv = barberfishFieldRemoteViews(
                    field = field,
                    alignment = config.alignment,
                    colorMode = field.colorMode,
                    sizeConfig = sizeConfig,
                    preview = config.preview,
                    context = context,
                )
                emitter.updateView(rv)
            }
        }
    }
}
