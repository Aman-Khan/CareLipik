package com.carelipik.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.carelipik.app.ui.navigation.CareLipikApp
import com.carelipik.app.ui.theme.CareLipikTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CareLipikTheme {
                CareLipikApp()
            }
        }
    }
}
