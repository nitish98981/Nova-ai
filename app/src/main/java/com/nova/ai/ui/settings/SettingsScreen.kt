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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.nova.ai.data.VoiceLanguage
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.VoiceController
import android.speech.tts.Voice
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

            SectionTitle("Awaaz ki pitch: %.1f".format(store.pitch))
            Slider(
                value = store.pitch,
                onValueChange = { store.pitch = (Math.round(it * 10) / 10f) },
                valueRange = 0.5f..2f
            )
            TextButton(onClick = { voice.preview("en") }) {
                Text("Test awaaz sunao", color = NovaColors.Cyan)
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("English / Hinglish voice")
            Hint("Android voice ka gender nahi batata, isliye har voice tap karke sun lo aur jo sabse achhi lage wahi chuno.")
            VoicePicker(
                options = voice.voicesFor("en"),
                selected = store.voiceEn,
                onPick = { store.voiceEn = it; voice.preview("en") }
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("Hindi voice")
            VoicePicker(
                options = voice.voicesFor("hi"),
                selected = store.voiceHi,
                onPick = { store.voiceHi = it; voice.preview("hi") }
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("AI dimaag")
            Hint("Auto mein NOVA pehle Gemini try karti hai, phir OpenAI, aur kuch na chale to Offline. Jo choose karoge wo pehle chalega.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Spacer(Modifier.height(4.dp))
            SectionTitle("Gemini (free key mil sakti hai)")
            Hint("Key banane ke liye aistudio.google.com/apikey kholo. Free tier ki limits hoti hain, aur Google free tier ka data apne products sudharne mein use kar sakta hai, isliye private baatein mat bolna.")
            KeyField("Gemini API key", store.geminiKey) { store.geminiKey = it }
            PlainField("Gemini model", store.geminiModel) { store.geminiModel = it }

            Spacer(Modifier.height(4.dp))
            SectionTitle("OpenAI ya compatible (optional)")
            Hint("Groq, OpenRouter jaise OpenAI-compatible service ki key aur base URL bhi chalegi.")
            KeyField("OpenAI API key", store.openAiKey) { store.openAiKey = it }
            PlainField("OpenAI model", store.openAiModel) { store.openAiModel = it }
            PlainField("Base URL", store.openAiBaseUrl) { store.openAiBaseUrl = it }
            Hint("Keys sirf is phone mein encrypted save hoti hain. Inhe kabhi GitHub par mat daalna.")

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
private fun VoicePicker(options: List<Voice>, selected: String, onPick: (String) -> Unit) {
    if (options.isEmpty()) {
        Hint("Koi voice nahi mili. Phone Settings → Text-to-speech se Google voice data download karo.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VoiceRow("Phone ki default voice", selected.isEmpty()) { onPick("") }
        options.take(10).forEachIndexed { index, v ->
            val online = if (v.isNetworkConnectionRequired) " (online)" else ""
            VoiceRow("Voice ${index + 1}: ${v.locale.displayName}$online", selected == v.name) { onPick(v.name) }
        }
    }
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
