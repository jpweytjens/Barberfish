package com.jpweytjen.barberfish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.jpweytjen.barberfish.screens.TabLayout
import com.jpweytjen.barberfish.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                TabLayout()
            }
        }

    }
}