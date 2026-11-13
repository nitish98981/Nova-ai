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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.chat.ChatScreen
import com.nova.ai.ui.customise.AccessibilityScreen
import com.nova.ai.ui.customise.ApiWizardScreen
import com.nova.ai.ui.customise.CustomiseScreen
import com.nova.ai.ui.customise.LanguageScreen
import com.nova.ai.ui.customise.VoiceSelectScreen
import com.nova.ai.ui.customise.WakeWordScreen
import com.nova.ai.ui.home.HomeScreen
import com.nova.ai.ui.imagegen.ImageGenScreen
import com.nova.ai.ui.more.MoreScreen
import com.nova.ai.ui.notifications.NotificationsScreen
import com.nova.ai.ui.permissions.PermissionsScreen
import com.nova.ai.ui.settings.SettingsScreen
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.ui.tools.CodeAssistantScreen
import com.nova.ai.ui.tools.RoutinesScreen
import com.nova.ai.ui.tools.ScannerScreen
import com.nova.ai.voice.VoiceController

private enum class Screen {
    Home, Customise, Api, SelectVoice, Accessibility, Language,
    Notifications, Chat, ImageGen, Code, More, Scanner, Routines, Permissions, Settings, WakeWord
}

@Composable
fun NovaApp(store: SettingsStore) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf(Screen.Home) }
    val screen = stack.last()
    val voice = remember { VoiceController(context, store) }

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { stack.clear(); stack.add(Screen.Home) }

    DisposableEffect(Unit) {
        onDispose { voice.release() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) voice.setForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE) voice.setForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voice.toggle()
        } else {
            voice.showNotice("Mic permission nahi mili. Permissions screen se baad mein de sakte ho.")
        }
    }

    val onMic = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) voice.toggle() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler(enabled = stack.size > 1) { back() }

    Box(Modifier.fillMaxSize().background(NovaColors.Void)) {
        when (screen) {
            Screen.Home -> HomeScreen(
                state = voice.state,
                level = voice.level,
                heard = voice.heard,
                message = voice.message,
                source = voice.source,
                emotion = voice.emotion,
                showAvatar = store.showAvatar,
                unread = voice.unread,
                wakeOn = voice.wakeListening,
                onMic = onMic,
                onCustomise = { go(Screen.Customise) },
                onNotifications = { go(Screen.Notifications) },
                onChat = { go(Screen.Chat) },
                onImage = { go(Screen.ImageGen) },
                onCode = { go(Screen.Code) },
                onMore = { go(Screen.More) }
            )
            Screen.Customise -> CustomiseScreen(
                store = store,
                onBack = { back() },
                onApi = { go(Screen.Api) },
                onVoice = { go(Screen.SelectVoice) },
                onAccessibility = { go(Screen.Accessibility) },
                onLanguage = { go(Screen.Language) },
                onWake = { go(Screen.WakeWord) }
            )
            Screen.Api -> ApiWizardScreen(store) { back() }
            Screen.SelectVoice -> VoiceSelectScreen(store, voice) { back() }
            Screen.Accessibility -> AccessibilityScreen(store, { back() }, { go(Screen.Permissions) })
            Screen.Language -> LanguageScreen(store) { back() }
            Screen.WakeWord -> WakeWordScreen(store) { back() }
            Screen.Notifications -> NotificationsScreen(voice) { back() }
            Screen.Chat -> ChatScreen(store, voice, { back() }, onMic)
            Screen.ImageGen -> ImageGenScreen(store) { back() }
            Screen.Code -> CodeAssistantScreen(voice) { back() }
            Screen.More -> MoreScreen(
                onBack = { back() },
                onScanner = { go(Screen.Scanner) },
                onRoutines = { go(Screen.Routines) },
                onPermissions = { go(Screen.Permissions) },
                onSettings = { go(Screen.Settings) }
            )
            Screen.Scanner -> ScannerScreen(store, voice) { back() }
            Screen.Routines -> RoutinesScreen(
                store = store,
                onRun = { name ->
                    home()
                    voice.submitText("$name routine chalao")
                },
                onBack = { back() }
            )
            Screen.Permissions -> PermissionsScreen { back() }
            Screen.Settings -> SettingsScreen(store, voice) { back() }
        }
    }
}
