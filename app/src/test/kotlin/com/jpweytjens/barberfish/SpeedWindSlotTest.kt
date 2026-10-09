package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.WindField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.SPEED_WIND_ROW_GAP_PX
import com.jpweytjens.barberfish.datatype.shared.SlotState
import com.jpweytjens.barberfish.datatype.shared.speedWindGeometry
import com.jpweytjens.barberfish.datatype.shared.visibleColumns
import com.jpweytjens.barberfish.datatype.speedWindState
import com.jpweytjens.barberfish.datatype.withSpeedPreview
import com.jpweytjens.barberfish.datatype.withSpeedStates
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.WindFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedWindSlotTest {

    private val metric =
        UserProfile(
            weight = 70f,
            preferredUnit =
                UserProfile.PreferredUnit(
                    distance = UserProfile.PreferredUnit.UnitType.METRIC,
                    elevation = UserProfile.PreferredUnit.UnitType.METRIC,
                    temperature = UserProfile.PreferredUnit.UnitType.METRIC,
                    weight = UserProfile.PreferredUnit.UnitType.METRIC,
                ),
            maxHr = 190,
            restingHr = 60,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = emptyList(),
        )
    private val cfg = WindFieldConfig(colorMode = ZoneColorMode.TEXT)

    private fun single(v: Double) =
        StreamState.Streaming(DataPoint("x", mapOf(DataType.Field.SINGLE to v)))

    private fun speed(kph: Double, smoothing: SpeedSmoothingStream = SpeedSmoothingStream.S0) =
        SpeedField.toFieldState(
            StreamState.Streaming(
                DataPoint(smoothing.typeId, mapOf(smoothing.fieldId to kph / 3.6))
            ),
            metric,
            smoothing,
        )

    // Riding north (course 0): the wind's direction is where it blows from.
    private fun wind(windFromDeg: Double, windSpeed: Double) =
        WindField.toFieldState(single(windFromDeg), single(windSpeed), 0.0, metric, cfg)

    @Test
    fun both_live_stack_speed_over_the_wind() {
        // 15 km/h from the north-east: 225 degrees, 15 cos 45 = 10.6 into the wind.
        val w = wind(45.0, 15.0)
        val s = speedWindState(speed(28.4), w)
        assertEquals("11", s.primary)
        assertEquals("28.4", s.speedRow)
        assertEquals("Speed", s.label)
        assertEquals(R.drawable.ic_col_speed, s.iconRes)
        assertEquals(w.iconRes, s.secondaryIconRes)
        assertEquals(225f, s.windArrowDeg!!, 1e-6f)
        assertEquals(w.color, s.color)
        assertTrue((s.color as FieldColor.Threshold).factor < 0f)
        assertFalse(s.noSensor)
    }

    @Test
    fun tailwind_colours_green() {
        val s = speedWindState(speed(30.0), wind(180.0, 6.0))
        assertEquals("-6", s.primary)
        assertTrue((s.color as FieldColor.Threshold).factor > 0f)
    }

    @Test
    fun calm_keeps_the_stack_without_an_arrow() {
        val s = speedWindState(speed(25.0), wind(90.0, 1.0))
        assertNull(s.windArrowDeg)
        assertNotNull(s.speedRow)
    }

    @Test
    fun smoothed_speed_label_passes_through() {
        val s = speedWindState(speed(25.0, SpeedSmoothingStream.S3), wind(90.0, 9.0))
        assertEquals("3s Speed", s.label)
    }

    @Test
    fun no_wind_data_falls_back_to_plain_speed_and_keeps_the_column() {
        val sp = speed(25.0)
        val s = speedWindState(sp, WindField.noWindData())
        assertEquals(sp, s)
        assertFalse(s.noSensor)
        val live = SlotState(FieldState("142", "HR", FieldColor.Default), ZoneColorMode.TEXT)
        val hud = HUDState(3, live, SlotState(s, ZoneColorMode.TEXT), live, live, metric)
        assertEquals(listOf(0, 1, 2), hud.visibleColumns())
    }

    @Test
    fun wind_searching_falls_back_to_plain_speed() {
        val sp = speed(25.0)
        assertEquals(sp, speedWindState(sp, FieldState.searching("Wind")))
    }

    @Test
    fun speed_missing_shows_the_plain_wind() {
        val w = wind(45.0, 15.0)
        assertEquals(w, speedWindState(FieldState.searching("Speed"), w))
    }

    @Test
    fun both_missing_show_the_speed_text_and_never_collapse() {
        val s = speedWindState(FieldState.searching("Speed"), WindField.noWindData())
        assertEquals("Searching…", s.primary)
        assertEquals("Speed", s.label)
        assertFalse(s.noSensor)
    }

    @Test
    fun speed_shows_at_once_while_headwind_is_silent() = runBlocking {
        val sp = speed(25.0)
        val first = emptyFlow<FieldState>().withSpeedStates(flowOf(sp)).first()
        assertEquals(sp, first)
    }

    @Test
    fun stack_follows_both_flows() = runBlocking {
        val w = wind(0.0, 10.0)
        val states = flowOf(w).withSpeedStates(flowOf(speed(20.0))).toList()
        assertEquals("20.0", states.last().speedRow)
        assertEquals("10", states.last().primary)
    }

    @Test
    fun preview_unchanged_when_off() {
        val frames = WindField.previewStates(cfg)
        assertEquals(
            frames,
            frames.withSpeedPreview(HUDSlotConfig(field = HUDSlotField.Wind), metric),
        )
    }

    @Test
    fun preview_stacks_every_wind_frame_when_on() {
        val frames = WindField.previewStates(cfg)
        val slot = HUDSlotConfig(field = HUDSlotField.Wind, windShowSpeed = true)
        val stacked = frames.withSpeedPreview(slot, metric)
        assertEquals(frames.size, stacked.size)
        assertTrue(stacked.all { it.speedRow != null })
        assertNull(stacked.first().windArrowDeg) // the calm frame
    }

    @Test
    fun geometry_on_a_karoo_3_hud_slot() {
        val g = speedWindGeometry(bitmapHeightPx = 60, arrowBoxPx = 60, density = 1.875f)
        assertEquals(28f, g.bandPx, 1e-6f)
        assertEquals(60, g.boxPx)
        assertEquals(7, g.gapPx)
        assertEquals(67, g.textLeftPx)
        assertEquals(4f, SPEED_WIND_ROW_GAP_PX, 0f)
    }

    @Test
    fun the_rows_start_after_the_single_row_arrow_box() {
        for (box in listOf(30, 45, 60)) {
            val g = speedWindGeometry(bitmapHeightPx = 60, arrowBoxPx = box, density = 1.875f)
            assertEquals(box, g.boxPx)
            assertEquals(box + g.gapPx, g.textLeftPx)
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun settings_without_the_option_read_as_off() {
        val stored = json.encodeToString(HUDConfig()).replace(",\"windShowSpeed\":false", "")
        assertFalse(stored.contains("windShowSpeed"))
        val cfg = json.decodeFromString<HUDConfig>(stored)
        assertFalse(cfg.leftSlot.windShowSpeed)
    }

    @Test
    fun the_option_round_trips() {
        val on =
            HUDConfig(leftSlot = HUDSlotConfig(field = HUDSlotField.Wind, windShowSpeed = true))
        assertTrue(json.decodeFromString<HUDConfig>(json.encodeToString(on)).leftSlot.windShowSpeed)
    }
}
