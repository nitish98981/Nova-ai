package com.nova.ai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.data.BrainChoice
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceEngine
import com.nova.ai.data.VoiceLanguage
import com.nova.ai.voice.HumanVoice
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.VoiceController
import androidx.compose.material3.TextButton

@Composable
fun SettingsScreen(store: SettingsStore, voice: VoiceController, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Wapas", tint = NovaColors.Lavender)
            }
            Text("Settings", color = NovaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("Avatar")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOVA ka chehra dikhao (band karne par sirf core dikhega)",
                    color = NovaColors.Text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = store.showAvatar, onCheckedChange = { store.showAvatar = it })
            }

            Spacer(Modifier.height(4.dp))
            SectionTitle("Phone control")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOVA ko apps kholne, search, alarm aur timer ki ijazat do",
                    color = NovaColors.Text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = store.phoneControl, onCheckedChange = { store.phoneControl = it })
            }
            Hint("Try karo: \"YouTube kholo\", \"YouTube par Arijit Singh search karo\", \"Google par mausam search karo\", \"WhatsApp kholo\", \"7 baje ka alarm\", \"5 minute ka timer\". Back, scroll, tap aur screen padhne ke liye Permissions mein Accessibility bhi chalu karni hogi. Payment, bank aur password wali screens par NOVA kaam nahi karti.")

            Spacer(Modifier.height(4.dp))
            SectionTitle("Awaaz ki bhasha")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLanguage.values().forEach { lang ->
                    val selected = store.language == lang
                    Text(
                        lang.label,
                        color = if (selected) NovaColors.Void else NovaColors.Text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) NovaColors.Lavender else NovaColors.Panel)
                            .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(50))
                            .clickable { store.language = lang }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            SectionTitle("Bolne ki speed: %.1fx".format(store.speed))
            Slider(
                value = store.speed,
                onValueChange = { store.speed = (Math.round(it * 10) / 10f) },
                valueRange = 0.5f..2f
            )

            SectionTitle("Phone voice ki pitch: %.1f".format(store.pitch))
            Slider(
                value = store.pitch,
                onValueChange = { store.pitch = (Math.round(it * 10) / 10f) },
                valueRange = 0.5f..2f
            )
            Hint("Speed dono awaazon par lagti hai. Pitch sirf phone ki awaaz par lagti hai.")

            Spacer(Modifier.height(8.dp))
            SectionTitle("NOVA ki awaaz")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceEngine.values().forEach { engine ->
                    val selected = store.voiceEngine == engine
                    Text(
                        engine.label,
                        color = if (selected) NovaColors.Void else NovaColors.Text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) NovaColors.Lavender else NovaColors.Panel)
                            .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(50))
                            .clickable { store.voiceEngine = engine }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Hint("Human: Gemini ki natural insaani awaaz. Internet aur Gemini key chahiye, aur jawab ke baad thodi der se bolti hai. Phone: Android ki awaaz, turant bolti hai par robotic lagti hai. Human na chale to NOVA khud phone ki awaaz par aa jaati hai.")
            if (store.voiceEngine == VoiceEngine.HUMAN) {
                SectionTitle("Human voice chuno")
                Hint("Google awaaz ka gender nahi batata. Har naam tap karke sun lo aur jo ladki jaisi pyari lage wahi rakho.")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HumanVoice.VOICES.forEach { (name, desc) ->
                        VoiceRow("$name — $desc", store.humanVoice == name) {
                            store.humanVoice = name
                            voice.preview()
                        }
                    }
                }
                PlainField("Voice model", store.ttsModel) { store.ttsModel = it }
            }
            TextButton(onClick = { voice.preview() }) {
                Text("Test awaaz sunao", color = NovaColors.Cyan)
            }
            if (voice.humanNote.isNotBlank()) {
                Hint("Human voice abhi nahi chali: " + voice.humanNote)
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("AI dimaag")
            Hint("Auto mein NOVA kaam dekh kar chunti hai: aam baatein Gemini, NVIDIA, Custom, OpenRouter ke order mein; coding ke sawaal OpenRouter, Custom, Gemini, NVIDIA ke order mein. Kuch na chale to Offline. Jo pill choose karoge wo sabse pehle chalega.")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrainChoice.values().forEach { choice ->
                    val selected = store.brain == choice
                    Text(
                        choice.label,
                        color = if (selected) NovaColors.Void else NovaColors.Text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) NovaColors.Lavender else NovaColors.Panel)
                            .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(50))
                            .clickable { store.brain = choice }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Hint("Keys sirf is phone mein encrypted save hoti hain. Inhe kabhi GitHub ya chat mein mat daalna.")

            Spacer(Modifier.height(4.dp))
            SectionTitle("Gemini")
            Hint("Free tier ka data Google apne products sudharne mein use kar sakta hai, isliye private baatein mat bolna.")
            KeyField("Gemini API key", store.geminiKey) { store.geminiKey = it }
            PlainField("Gemini model", store.geminiModel) { store.geminiModel = it }

            Spacer(Modifier.height(4.dp))
            SectionTitle("OpenRouter")
            KeyField("OpenRouter API key", store.openRouterKey) { store.openRouterKey = it }
            PlainField("OpenRouter model", store.openRouterModel) { store.openRouterModel = it }
            Hint("Model nahi chale to openrouter.ai/models par free model ka naam dekh kar yahan daalo (aksar :free se khatam hota hai).")

            Spacer(Modifier.height(4.dp))
            SectionTitle("NVIDIA")
            KeyField("NVIDIA API key", store.nvidiaKey) { store.nvidiaKey = it }
            PlainField("NVIDIA model", store.nvidiaModel) { store.nvidiaModel = it }
            Hint("Model ka sahi naam build.nvidia.com ke model page par milta hai.")

            Spacer(Modifier.height(4.dp))
            SectionTitle("Custom (llmsrelay ya koi bhi OpenAI-compatible)")
            KeyField("Custom API key", store.openAiKey) { store.openAiKey = it }
            PlainField("Custom base URL (jaise https://.../v1)", store.openAiBaseUrl) { store.openAiBaseUrl = it }
            PlainField("Custom model", store.openAiModel) { store.openAiModel = it }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = NovaColors.Lavender, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Hint(text: String) {
    Text(text, color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) NovaColors.Void else NovaColors.Text,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NovaColors.Lavender else NovaColors.Panel)
            .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    )
}

@Composable
private fun PlainField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
