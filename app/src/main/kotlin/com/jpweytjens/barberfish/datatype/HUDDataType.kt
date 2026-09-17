package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.slots
import com.jpweytjens.barberfish.datatype.shared.toHudSlotSizeConfig
import com.jpweytjens.barberfish.datatype.shared.visibleColumns
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
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

    override fun renderState(
        state: HUDState,
        design: DataFieldDesignConfig,
        config: ViewConfig,
        context: Context,
    ): RemoteViews = buildHudRemoteViews(state, design, config, context)

    protected fun buildHudRemoteViews(
        state: HUDState,
        design: DataFieldDesignConfig,
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
        // Slots whose sensor is unpaired are hidden; the weighted slot row hands their width to
        // the survivors, and sizing follows the visible count rather than the configured one.
        val visible = state.visibleColumns()
        val paddingHPx = (4f * density).toInt()
        // hud_root insets the slot row by paddingHPx per side and the HUD cell is narrower
        // than the screen, so the colSpan-based fallback width overshoots and the label
        // bitmap gets cropped (scaleType=center). Pass the real slot width instead.
        val slotWidthPx = (config.viewSize.first - 2f * paddingHPx) / visible.size
        val sizeConfig =
            config
                .toHudSlotSizeConfig(visible.size, design)
                .copy(cellWidthPxOverride = slotWidthPx.takeIf { it > 0f })
        val layoutRes =
            if (state.columns == 4) R.layout.barberfish_hud_four else R.layout.barberfish_hud
        val rv = RemoteViews(context.packageName, layoutRes)
        rv.setViewPadding(R.id.hud_root, paddingHPx, paddingPx, paddingHPx, paddingPx)
        // Clip slot row above the sparkline so colored backgrounds don't bleed through
        rv.setViewPadding(R.id.hud_slot_row, 0, 0, 0, sparklineHeightPx)
        // Preview corner radius
        if (config.preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setViewOutlinePreferredRadius(R.id.hud_root, 12f, TypedValue.COMPLEX_UNIT_DIP)
            rv.setBoolean(R.id.hud_root, "setClipToOutline", true)
        }
        val slotIds =
            listOf(
                R.id.hud_slot_left,
                R.id.hud_slot_middle,
                R.id.hud_slot_right,
                R.id.hud_slot_fourth,
            )
        state.slots.forEachIndexed { index, slot ->
            val slotId = slotIds[index]
            if (index !in visible) {
                rv.setViewVisibility(slotId, View.GONE)
                return@forEachIndexed
            }
            rv.setViewVisibility(slotId, View.VISIBLE)
            rv.removeAllViews(slotId)
            rv.addView(
                slotId,
                barberfishFieldRemoteViews(
                    field = slot.field,
                    alignment = config.alignment,
                    colorMode = slot.colorMode,
                    sizeConfig = sizeConfig,
                    preview = false,
                    context = context,
                ),
            )
        }
        return rv
    }
}
