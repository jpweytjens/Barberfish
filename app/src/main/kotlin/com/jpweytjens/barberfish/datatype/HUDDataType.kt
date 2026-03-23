package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.HUDState
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

@OptIn(FlowPreview::class)
abstract class HUDDataType(extensionId: String, typeId: String) :
    DataTypeImpl(extensionId, typeId) {

    /** Throttle applied to [liveFlow] emissions. */
    open val sampleMs: Long = 400L

    /** Emits HUDState from real sensor streams. Must not sample internally. */
    abstract fun liveFlow(context: Context): Flow<HUDState>

    /** Emits HUDState for the Karoo config-screen preview carousel. */
    abstract fun previewFlow(context: Context): Flow<HUDState>

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val flow =
                if (config.preview) previewFlow(context) else liveFlow(context).sample(sampleMs)
            flow.collect { state ->
                val rv = RemoteViews(context.packageName, R.layout.barberfish_hud)
                // Preserve the original 2dp vertical padding from the Glance Row
                val paddingPx = (2f * context.resources.displayMetrics.density).toInt()
                rv.setViewPadding(R.id.hud_root, 0, paddingPx, 0, paddingPx)
                // Preview corner radius
                if (config.preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    rv.setViewOutlinePreferredRadius(R.id.hud_root, 12f, TypedValue.COMPLEX_UNIT_DIP)
                    rv.setBoolean(R.id.hud_root, "setClipToOutline", true)
                }
                listOf(
                    Triple(R.id.hud_slot_left, state.leftSlot, state.leftColorMode),
                    Triple(R.id.hud_slot_middle, state.middleSlot, state.middleColorMode),
                    Triple(R.id.hud_slot_right, state.rightSlot, state.rightColorMode),
                ).forEach { (slotId, field, colorMode) ->
                    rv.removeAllViews(slotId)
                    rv.addView(
                        slotId,
                        barberfishFieldRemoteViews(
                            field = field,
                            alignment = config.alignment,
                            colorMode = colorMode,
                            sizeConfig = ViewSizeConfig.HUD,
                            preview = false,
                            context = context,
                        ),
                    )
                }
                emitter.updateView(rv)
            }
        }
    }
}
