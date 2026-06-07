package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.flow.Flow

abstract class HUDDataType(extensionId: String, typeId: String) :
    BarberfishBase<HUDState>(extensionId, typeId) {

    // The HUD strip sizes itself from its own layout; it doesn't need the cell size in the flow.
    abstract fun liveFlow(context: Context): Flow<HUDState>

    abstract fun previewFlow(context: Context): Flow<HUDState>

    final override fun liveFlow(context: Context, config: ViewConfig): Flow<HUDState> =
        liveFlow(context)

    final override fun previewFlow(context: Context, config: ViewConfig): Flow<HUDState> =
        previewFlow(context)

    override fun renderState(state: HUDState, config: ViewConfig, context: Context): RemoteViews =
        buildHudRemoteViews(state, config, context)

    protected fun buildHudRemoteViews(
        state: HUDState,
        config: ViewConfig,
        context: Context,
        sparklineHeightPx: Int = 0,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val paddingPx = (2f * density).toInt()
        // Slot row fills full cell; sparkline overlays at bottom (FrameLayout root).
        // hud_slot_row gets bottom padding = sparklineHeightPx so each slot's real bottom
        // sits above the sparkline. The field layout's baseline_box uses
        // layout_alignParentBottom, so its centering region shrinks with the slot and
        // keeps the bitmap value above the sparkline.
        val sizeConfig =
            if (state.columns == 4) ViewSizeConfig.HUD_FOUR else ViewSizeConfig.HUD_THREE
        val layoutRes =
            if (state.columns == 4) R.layout.barberfish_hud_four else R.layout.barberfish_hud
        val rv = RemoteViews(context.packageName, layoutRes)
        val paddingHPx = (4f * density).toInt()
        rv.setViewPadding(R.id.hud_root, paddingHPx, paddingPx, paddingHPx, paddingPx)
        // Clip slot row above the sparkline so colored backgrounds don't bleed through
        rv.setViewPadding(R.id.hud_slot_row, 0, 0, 0, sparklineHeightPx)
        // Preview corner radius
        if (config.preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setViewOutlinePreferredRadius(R.id.hud_root, 12f, TypedValue.COMPLEX_UNIT_DIP)
            rv.setBoolean(R.id.hud_root, "setClipToOutline", true)
        }
        buildList {
                add(Triple(R.id.hud_slot_left, state.left.field, state.left.colorMode))
                add(Triple(R.id.hud_slot_middle, state.middle.field, state.middle.colorMode))
                add(Triple(R.id.hud_slot_right, state.right.field, state.right.colorMode))
                if (state.columns == 4)
                    add(Triple(R.id.hud_slot_fourth, state.fourth.field, state.fourth.colorMode))
            }
            .forEach { (slotId, field, colorMode) ->
                rv.removeAllViews(slotId)
                rv.addView(
                    slotId,
                    barberfishFieldRemoteViews(
                        field = field,
                        alignment = config.alignment,
                        colorMode = colorMode,
                        sizeConfig = sizeConfig,
                        preview = false,
                        context = context,
                    ),
                )
            }
        return rv
    }
}
