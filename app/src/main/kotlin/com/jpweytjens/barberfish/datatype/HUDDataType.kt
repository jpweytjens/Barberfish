package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.HUDState
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

abstract class HUDDataType(extensionId: String, typeId: String) :
    DataTypeImpl(extensionId, typeId) {

    /** Emits HUDState from real sensor streams. */
    abstract fun liveFlow(context: Context): Flow<HUDState>

    /** Emits HUDState for the Karoo config-screen preview carousel. */
    abstract fun previewFlow(context: Context): Flow<HUDState>

    protected fun buildHudRemoteViews(
        state: HUDState,
        config: ViewConfig,
        context: Context,
        sparklineHeightPx: Int = 0,
    ): RemoteViews {
        val colSpanOverride = if (state.columns == 4) 15 else 20
        val textSizeOverride = if (state.columns == 4) 37 else 42
        val sizeConfig = config.toViewSizeConfig(
            colSpanOverride  = colSpanOverride,
            textSizeOverride = textSizeOverride,
        ).let { cfg ->
            if (sparklineHeightPx > 0)
                cfg.copy(valueTranslationY = (cfg.valueTranslationY - sparklineHeightPx / 2f).coerceAtLeast(0f))
            else cfg
        }
        val layoutRes =
            if (state.columns == 4) R.layout.barberfish_hud_four else R.layout.barberfish_hud
        val rv = RemoteViews(context.packageName, layoutRes)
        // 2dp top padding; drop bottom padding when sparkline fills the bottom edge
        val paddingPx = (2f * context.resources.displayMetrics.density).toInt()
        val bottomPaddingPx = if (sparklineHeightPx > 0) 0 else paddingPx
        rv.setViewPadding(R.id.hud_root, 0, paddingPx, 0, bottomPaddingPx)
        // Preview corner radius
        if (config.preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setViewOutlinePreferredRadius(R.id.hud_root, 12f, TypedValue.COMPLEX_UNIT_DIP)
            rv.setBoolean(R.id.hud_root, "setClipToOutline", true)
        }
        buildList {
            add(Triple(R.id.hud_slot_left,   state.leftSlot,   state.leftColorMode))
            add(Triple(R.id.hud_slot_middle, state.middleSlot, state.middleColorMode))
            add(Triple(R.id.hud_slot_right,  state.rightSlot,  state.rightColorMode))
            if (state.columns == 4)
                add(Triple(R.id.hud_slot_fourth, state.fourthSlot, state.fourthColorMode))
        }.forEach { (slotId, field, colorMode) ->
            rv.removeAllViews(slotId)
            rv.addView(
                slotId,
                barberfishFieldRemoteViews(
                    field      = field,
                    alignment  = config.alignment,
                    colorMode  = colorMode,
                    sizeConfig = sizeConfig,
                    preview    = false,
                    context    = context,
                ),
            )
        }
        return rv
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow =
                if (config.preview) previewFlow(context) else liveFlow(context)
            flow.collect { state ->
                emitter.updateView(buildHudRemoteViews(state, config, context))
            }
        }
    }
}
