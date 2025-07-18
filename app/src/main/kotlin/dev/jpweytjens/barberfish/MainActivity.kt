package dev.jpweytjens.barberfish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.hammerhead.karooext.KarooSystemService
import dev.jpweytjens.barberfish.screens.MainScreen
import dev.jpweytjens.barberfish.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
