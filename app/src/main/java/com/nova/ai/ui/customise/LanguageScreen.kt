package com.nova.ai.ui.customise

import androidx.compose.runtime.Composable
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceLanguage
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.OptionRow
import com.nova.ai.ui.common.ScreenFrame
import com.nova.ai.ui.common.SectionLabel

@Composable
fun LanguageScreen(store: SettingsStore, onBack: () -> Unit) {
    ScreenFrame("Language", onBack) {
        SectionLabel("Tum kis bhasha mein bologe?")
        VoiceLanguage.values().forEach { lang ->
            OptionRow(lang.label, store.language == lang) { store.language = lang }
        }
        HintText("Hinglish aur Hindi mein NOVA tumhari awaaz Hindi (India) se samajhti hai. English mein English (India) se. NOVA tumhari bhasha mein hi jawab deti hai.")
    }
}
