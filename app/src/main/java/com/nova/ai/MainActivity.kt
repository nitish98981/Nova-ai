package com.nova.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.NovaApp
import com.nova.ai.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(applicationContext)
        setContent {
            NovaTheme {
                NovaApp(store)
            }
        }
    }
}
