package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.toViewSizeConfig
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

abstract class BarberfishBase<T>(extensionId: String, typeId: String) :
    DataTypeImpl(extensionId, typeId) {

    abstract fun liveFlow(context: Context): Flow<T>
    abstract fun previewFlow(context: Context): Flow<T>
    abstract fun renderState(state: T, config: ViewConfig, context: Context): RemoteViews

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
            val flow = if (config.preview) previewFlow(context) else liveFlow(context)
            flow.collect { emitter.updateView(renderState(it, config, context)) }
        }
    }
}

abstract class BarberfishDataType(extensionId: String, typeId: String) :
    BarberfishBase<FieldState>(extensionId, typeId) {

    override fun renderState(state: FieldState, config: ViewConfig, context: Context): RemoteViews {
        val sizeConfig = config.toViewSizeConfig().copy(
            cellHeightPx = config.viewSize.second.toFloat(),
        )
        return barberfishFieldRemoteViews(
            field = state,
            alignment = config.alignment,
            colorMode = state.colorMode,
            sizeConfig = sizeConfig,
            preview = config.preview,
            context = context,
        )
    }
}
