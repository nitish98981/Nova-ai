package com.nova.ai.ui.customise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.nova.ai.brain.AiProvider
import com.nova.ai.brain.ApiTester
import com.nova.ai.brain.GeminiProvider
import com.nova.ai.brain.OpenAiProvider
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.common.CenteredRow
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.ScreenFrame
import com.nova.ai.ui.common.SectionLabel
import com.nova.ai.ui.common.SecretField
import com.nova.ai.ui.common.TextInput
import com.nova.ai.ui.common.WideButton
import com.nova.ai.ui.theme.NovaColors
import kotlinx.coroutines.launch

private enum class ApiStep(val title: String, val help: String) {
    GEMINI(
        "Gemini",
        "Free key aistudio.google.com/apikey par banti hai. Ye NOVA ka main dimaag, photo samajhne aur Human awaaz ke kaam aati hai."
    ),
    NVIDIA(
        "NVIDIA",
        "build.nvidia.com par account bana kar API key lo. Backup AI ke liye. Model ka sahi naam wahin ke model page par milta hai."
    ),
    OPENROUTER(
        "OpenRouter",
        "openrouter.ai/keys par key banti hai. Coding ke sawaalon ke liye. Model nahi chale to openrouter.ai/models se koi :free model chuno."
    ),
    LLMSRELAY(
        "LLMsRelay",
        "Apne LLMsRelay dashboard se Base URL (aksar https://…/v1 jaisa), key aur model ka naam yahan daalo."
    )
}

/** Gemini -> NVIDIA -> OpenRouter -> LLMsRelay. On every step the user can add, go back or continue. */
@Composable
fun ApiWizardScreen(store: SettingsStore, onExit: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val step = ApiStep.values()[index]
    val scope = rememberCoroutineScope()

    var key by remember(index) { mutableStateOf(readKey(store, step)) }
    var model by remember(index) { mutableStateOf(readModel(store, step)) }
    var baseUrl by remember(index) { mutableStateOf(store.openAiBaseUrl) }
    var testing by remember(index) { mutableStateOf(false) }
    var result by remember(index) { mutableStateOf("") }
    var ok by remember(index) { mutableStateOf(false) }

    fun save() {
        writeKey(store, step, key.trim())
        writeModel(store, step, model.trim())
        if (step == ApiStep.LLMSRELAY) store.openAiBaseUrl = baseUrl.trim()
    }

    fun provider(): AiProvider? = when (step) {
        ApiStep.GEMINI -> GeminiProvider(key.trim(), model.trim())
        ApiStep.NVIDIA -> OpenAiProvider("NVIDIA", key.trim(), model.trim(), "https://integrate.api.nvidia.com/v1")
        ApiStep.OPENROUTER -> OpenAiProvider("OpenRouter", key.trim(), model.trim(), "https://openrouter.ai/api/v1")
        ApiStep.LLMSRELAY ->
            if (baseUrl.isBlank()) null else OpenAiProvider("LLMsRelay", key.trim(), model.trim(), baseUrl.trim())
    }

    ScreenFrame("API ${index + 1}/4", onBack = { save(); if (index == 0) onExit() else index-- }) {
        SectionLabel(step.title)
        HintText(step.help)

        SecretField("${step.title} API key", key) { key = it; result = "" }
        TextInput("Model (sirf zaroorat ho tabhi badlo)", model) { model = it; result = "" }
        if (step == ApiStep.LLMSRELAY) {
            TextInput("Base URL (jaise https://…/v1)", baseUrl) { baseUrl = it; result = "" }
        }

        WideButton(
            if (testing) "Jaanch rahi hoon…" else "Add",
            onClick = {
                save()
                val p = provider()
                if (key.isBlank()) {
                    ok = false
                    result = "Pehle key daalo"
                } else if (p == null) {
                    ok = false
                    result = "Base URL daalo"
                } else {
                    testing = true
                    result = ""
                    scope.launch {
                        val (good, msg) = ApiTester.test(p)
                        ok = good
                        result = msg
                        testing = false
                    }
                }
            },
            enabled = !testing
        )
        if (result.isNotBlank()) {
            Text(
                result,
                color = if (ok) NovaColors.Good else NovaColors.Cyan,
                fontSize = 13.sp
            )
        }

        HintText("Add dabane par key save hoti hai aur NOVA ek chhota message bhej kar jaanchti hai ki key chal rahi hai ya nahi. Key sirf is phone mein encrypted rehti hai.")

        CenteredRow {
            WideButton(
                "Back",
                onClick = { save(); if (index == 0) onExit() else index-- },
                modifier = androidx.compose.ui.Modifier.weight(1f)
            )
            WideButton(
                if (index == ApiStep.values().lastIndex) "Done" else "Continue",
                onClick = { save(); if (index == ApiStep.values().lastIndex) onExit() else index++ },
                modifier = androidx.compose.ui.Modifier.weight(1f)
            )
        }
    }
}

private fun readKey(s: SettingsStore, step: ApiStep): String = when (step) {
    ApiStep.GEMINI -> s.geminiKey
    ApiStep.NVIDIA -> s.nvidiaKey
    ApiStep.OPENROUTER -> s.openRouterKey
    ApiStep.LLMSRELAY -> s.openAiKey
}

private fun readModel(s: SettingsStore, step: ApiStep): String = when (step) {
    ApiStep.GEMINI -> s.geminiModel
    ApiStep.NVIDIA -> s.nvidiaModel
    ApiStep.OPENROUTER -> s.openRouterModel
    ApiStep.LLMSRELAY -> s.openAiModel
}

private fun writeKey(s: SettingsStore, step: ApiStep, v: String) {
    when (step) {
        ApiStep.GEMINI -> s.geminiKey = v
        ApiStep.NVIDIA -> s.nvidiaKey = v
        ApiStep.OPENROUTER -> s.openRouterKey = v
        ApiStep.LLMSRELAY -> s.openAiKey = v
    }
}

private fun writeModel(s: SettingsStore, step: ApiStep, v: String) {
    when (step) {
        ApiStep.GEMINI -> s.geminiModel = v
        ApiStep.NVIDIA -> s.nvidiaModel = v
        ApiStep.OPENROUTER -> s.openRouterModel = v
        ApiStep.LLMSRELAY -> s.openAiModel = v
    }
}
