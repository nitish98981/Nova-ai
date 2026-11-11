package com.nova.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.home.HomeScreen
import com.nova.ai.ui.permissions.PermissionsScreen
import com.nova.ai.ui.settings.SettingsScreen
import com.nova.ai.ui.theme.NovaColors

private enum class Screen { Home, Settings, Permissions }

@Composable
fun NovaApp(store: SettingsStore) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Home) }
    var previewListening by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf("Hey! Main NOVA hoon. Abhi meri neev ban rahi hai — voice aur brain aage ke phases mein aayenge.")
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        message = if (granted) {
            "Mic permission mil gayi. Voice recognition Phase 2 mein add hogi."
        } else {
            "Mic permission nahi mili. Permissions screen se baad mein de sakte ho."
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    Box(Modifier.fillMaxSize().background(NovaColors.Void)) {
        when (screen) {
            Screen.Home -> HomeScreen(
                listening = previewListening,
                message = message,
                onMic = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        previewListening = !previewListening
                        message = if (previewListening) {
                            "Ye sirf animation preview hai. Asli sunna Phase 2 mein shuru hoga."
                        } else {
                            "Preview band. Phase 2 mein asli voice aayegi."
                        }
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onOpenSettings = { screen = Screen.Settings },
                onOpenPermissions = { screen = Screen.Permissions }
            )
            Screen.Settings -> SettingsScreen(store) { screen = Screen.Home }
            Screen.Permissions -> PermissionsScreen { screen = Screen.Home }
        }
    }
}
