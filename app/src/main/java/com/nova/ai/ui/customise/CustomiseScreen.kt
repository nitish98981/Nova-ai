package com.nova.ai.ui.customise

import android.content.ComponentName
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nova.ai.control.NovaAccessibilityService
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceEngine
import com.nova.ai.ui.common.MenuCard
import com.nova.ai.ui.common.ScreenFrame

/** Opened from the menu button on Home: Add API, Select voice, Accessibility, Language. */
@Composable
fun CustomiseScreen(
    store: SettingsStore,
    onBack: () -> Unit,
    onApi: () -> Unit,
    onVoice: () -> Unit,
    onAccessibility: () -> Unit,
    onLanguage: () -> Unit,
    onMoreSettings: () -> Unit
) {
    val context = LocalContext.current
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val component = ComponentName(context, NovaAccessibilityService::class.java)
    val a11yOn = enabled.contains(component.flattenToString(), ignoreCase = true) ||
        enabled.contains(component.flattenToShortString(), ignoreCase = true)

    fun mark(name: String, on: Boolean) = name + if (on) " ✓" else " ✗"
    val apiLine = listOf(
        mark("Gemini", store.geminiKey.isNotBlank()),
        mark("NVIDIA", store.nvidiaKey.isNotBlank()),
        mark("OpenRouter", store.openRouterKey.isNotBlank()),
        mark("LLMsRelay", store.openAiKey.isNotBlank())
    ).joinToString("   ")

    ScreenFrame("Customise", onBack) {
        MenuCard("Add API", apiLine, onApi)
        MenuCard(
            "Select voice",
            if (store.voiceEngine == VoiceEngine.HUMAN) "Human (Gemini) • ${store.humanVoice}" else "Phone (Android) ki awaaz",
            onVoice
        )
        MenuCard(
            "Accessibility",
            (if (a11yOn) "Chalu" else "Band") + " • Phone control " + (if (store.phoneControl) "On" else "Off"),
            onAccessibility
        )
        MenuCard("Language", store.language.label, onLanguage)
        MenuCard("Aur settings", "Speed, pitch, avatar, AI ka mode aur model ke naam", onMoreSettings)
    }
}
