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
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceLanguage
import com.nova.ai.ui.theme.NovaColors

@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
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
            Hint("Speed aur pitch Phase 2 mein NOVA ki awaaz par lagengi.")

            Spacer(Modifier.height(8.dp))
            SectionTitle("AI keys")
            Hint("Keys sirf is phone mein encrypted save hoti hain. Inhe kabhi GitHub par mat daalna. Phase 3 mein inka use hoga.")
            KeyField("Gemini API key", store.geminiKey) { store.geminiKey = it }
            KeyField("OpenAI API key (optional)", store.openAiKey) { store.openAiKey = it }

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
