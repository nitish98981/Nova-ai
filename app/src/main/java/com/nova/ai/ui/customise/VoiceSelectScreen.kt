package com.nova.ai.ui.customise

import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.nova.ai.voice.NovaState
import androidx.compose.ui.unit.sp
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceEngine
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.OptionRow
import com.nova.ai.ui.common.ScreenFrame
import com.nova.ai.ui.common.SectionLabel
import com.nova.ai.ui.common.TextInput
import com.nova.ai.ui.common.WideButton
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.HumanVoice
import com.nova.ai.voice.VoiceController

@Composable
fun VoiceSelectScreen(store: SettingsStore, voice: VoiceController, onBack: () -> Unit) {
    var token by remember { mutableIntStateOf(0) }
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        if (token > 0) {
            waiting = true
            delay(8000)
            waiting = false
        }
    }

    ScreenFrame("Select voice", onBack) {
        SectionLabel("NOVA ki awaaz")
        VoiceEngine.values().forEach { engine ->
            OptionRow(engine.label, store.voiceEngine == engine) { store.voiceEngine = engine }
        }
        HintText("Human: Gemini ki natural awaaz (internet aur Gemini key chahiye, thodi der se bolti hai). Phone: Android ki awaaz, turant bolti hai par robotic lagti hai. Human na chale to NOVA khud phone ki awaaz par aa jaati hai.")

        if (store.voiceEngine == VoiceEngine.HUMAN) {
            SectionLabel("Human voice chuno")
            HintText("Google awaaz ka gender nahi batata. Har naam tap karke sun lo aur jo ladki jaisi pyari lage wahi rakho.")
            HumanVoice.VOICES.forEach { (name, desc) ->
                OptionRow("$name — $desc", store.humanVoice == name) {
                    store.humanVoice = name
                    token++
                    voice.preview()
                }
            }
            TextInput("Voice model", store.ttsModel) { store.ttsModel = it }
        }

        SectionLabel("Bolne ki speed: %.1fx".format(store.speed))
        Slider(
            value = store.speed,
            onValueChange = { store.speed = (Math.round(it * 10) / 10f) },
            valueRange = 0.5f..2f
        )

        WideButton("Test awaaz sunao", { token++; voice.preview() })
        if (waiting && voice.state != NovaState.Speaking) {
            Text(
                "Awaaz la rahi hoon… (pehli baar 1-2 second lagte hain, dobara usi voice par turant bajegi)",
                color = NovaColors.Lavender, fontSize = 13.sp
            )
        }
        if (voice.humanNote.isNotBlank()) {
            Text("Human voice abhi nahi chali: ${voice.humanNote}", color = NovaColors.Cyan, fontSize = 13.sp)
        }
    }
}
