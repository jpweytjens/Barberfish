package com.jpweytjens.barberfish

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jpweytjens.barberfish.datatype.shared.Grey100
import com.jpweytjens.barberfish.datatype.shared.Grey200
import com.jpweytjens.barberfish.datatype.shared.OceanBlue
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.screens.CollapsibleSection
import com.jpweytjens.barberfish.screens.ConfigSection
import com.jpweytjens.barberfish.screens.DataFieldDesignSectionContent
import com.jpweytjens.barberfish.screens.HUDConfigSection
import com.jpweytjens.barberfish.screens.LocalDataFieldDesign
import com.jpweytjens.barberfish.screens.LocalScreenshotMode
import com.jpweytjens.barberfish.screens.PalettesSectionContent
import io.hammerhead.karooext.models.UserProfile
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the config-screen shots as isolated compositions and writes one PNG per shot to the app's
 * external files dir for adb pull:
 *
 * adb pull /sdcard/Android/data/com.jpweytjens.barberfish/files/config_shots
 *
 * Drive via `scripts/render_config_shots.sh` (manual `am instrument` flow — never
 * `connectedDebugAndroidTest`, which uninstalls the app and wipes DataStore).
 */
@RunWith(AndroidJUnit4::class)
class ConfigShotsRenderTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Config screen content width: the full 480-px K3 screen at density 1.875.
    private val shotWidth = 256.dp
    // Full window height (800 px); shots taller than the window are clipped to it,
    // matching what the screen shows.
    private val windowHeight = 426.dp

    private val shotProfile =
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

    private fun setShotContent(
        fixedHeight: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        // Hide the status bar to reclaim its height for the capture; the shot is a synthetic
        // composition, not real chrome, so there is no status bar to preserve space for.
        composeRule.activity.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView)
                .hide(WindowInsetsCompat.Type.statusBars())
        }
        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = OceanBlue, onPrimary = Color.White)
            ) {
                CompositionLocalProvider(
                    LocalScreenshotMode provides true,
                    LocalDataFieldDesign provides DataFieldDesignConfig(),
                ) {
                    Column(
                        modifier =
                            Modifier.testTag(SHOT_TAG)
                                .width(shotWidth)
                                .then(
                                    if (fixedHeight) Modifier.height(windowHeight).clipToBounds()
                                    else Modifier
                                )
                                .background(Grey100)
                                .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content,
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(SHOT_TAG).captureToImage().asAndroidBitmap()
        val outDir =
            File(composeRule.activity.getExternalFilesDir(null), "config_shots").apply { mkdirs() }
        FileOutputStream(File(outDir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun configOverview() {
        setShotContent(fixedHeight = true) {
            for (section in ConfigSection.entries) {
                CollapsibleSection(section = section, expanded = false, onToggle = {}) {}
            }
        }
        capture("config")
    }

    @Test
    fun hudConfig() {
        setShotContent(fixedHeight = true) {
            var hudConfig by remember {
                mutableStateOf(
                    HUDConfig(
                        columns = 4,
                        leftSlot =
                            HUDSlotConfig(
                                field = HUDSlotField.Speed,
                                colorMode = ZoneColorMode.BACKGROUND,
                            ),
                        middleSlot =
                            HUDSlotConfig(
                                field = HUDSlotField.HR,
                                colorMode = ZoneColorMode.BACKGROUND,
                            ),
                        rightSlot =
                            HUDSlotConfig(
                                field = HUDSlotField.Power,
                                colorMode = ZoneColorMode.BACKGROUND,
                            ),
                        fourthSlot =
                            HUDSlotConfig(
                                field = HUDSlotField.Grade,
                                colorMode = ZoneColorMode.BACKGROUND,
                            ),
                    )
                )
            }
            var sparklineConfig by remember {
                mutableStateOf(SparklineConfig(mode = SparklineMode.ON))
            }
            CollapsibleSection(section = ConfigSection.HUD, expanded = true, onToggle = {}) {
                HUDConfigSection(
                    hudConfig = hudConfig,
                    sparklineConfig = sparklineConfig,
                    zoneConfig = ZoneConfig(),
                    timeCfg = TimeConfig(),
                    profile = shotProfile,
                    onUpdate = { hudConfig = it },
                    onSparklineUpdate = { sparklineConfig = it },
                )
            }
        }
        capture("hud_config")
    }

    @Test
    fun paletteConfig() {
        // No CollapsibleSection header here: reclaims the ~header's-worth of height for the
        // palette previews (esp. Grade, which is taller than the two zone previews above it).
        // Mirrors the card look CollapsibleSection renders around its expanded content, minus
        // the header row.
        setShotContent {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Grey200, RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PalettesSectionContent(zoneConfig = ZoneConfig(), onUpdate = {})
            }
        }
        capture("palette_config")
    }

    @Test
    fun designBarberfish() {
        setShotContent {
            CollapsibleSection(
                section = ConfigSection.DATA_FIELD_DESIGN,
                expanded = true,
                onToggle = {},
            ) {
                DataFieldDesignSectionContent(config = DataFieldDesignConfig(), onUpdate = {})
            }
        }
        capture("design_barberfish")
    }

    private companion object {
        const val SHOT_TAG = "bf:shot"
    }
}
