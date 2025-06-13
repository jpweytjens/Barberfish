package com.jpweytjens.barberfish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.jpweytjens.barberfish.screens.TabLayout
import com.jpweytjens.barberfish.theme.AppTheme

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