package com.nova.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.nova.ai.brain.ApiTester
import com.nova.ai.brain.ModelCatalog
import com.nova.ai.ui.common.ModelListDialog
import com.nova.ai.data.BrainChoice
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.TextInput
import com.nova.ai.ui.tools.ToolChip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.ui.tools.ToolButton
import com.nova.ai.ui.tools.ToolHeader
import com.nova.ai.voice.ChatLine
import com.nova.ai.voice.VoiceController

/** Type to NOVA (same brain and voice as the mic) and see the whole conversation. */
@Composable
fun ChatScreen(store: SettingsStore, voice: VoiceController, onBack: () -> Unit, onMic: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val liveText = if (voice.replying) voice.message else ""
    val count = voice.chatLog.size + (if (voice.replying) 1 else 0)

    LaunchedEffect(count, liveText.length) {
        if (count > 0) listState.scrollToItem(count - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ToolHeader("Chat", onBack)
        ModelPicker(store)

        if (count == 0) {
            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                HintText("NOVA se likh kar baat karo. Jo bhi tum bolte ho ya likhte ho wo yahan dikhta hai. Ye baat sirf tab tak yaad rehti hai jab tak app khuli hai.")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(voice.chatLog.toList()) { line -> Bubble(line) }
                if (voice.replying) {
                    item { Bubble(ChatLine(false, liveText.ifBlank { "NOVA soch rahi hai…" })) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("NOVA se kuch bhi pucho…", color = NovaColors.Muted) },
                maxLines = 4
            )
            ToolButton("Mic", onMic)
            ToolButton(
                "Bhejo",
                {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        input = ""
                        voice.submitText(t)
                    }
                },
                enabled = input.isNotBlank()
            )
        }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(0.85f)
                .clip(shape)
                .background(if (line.fromUser) NovaColors.Violet.copy(alpha = 0.25f) else NovaColors.Panel)
                .border(1.dp, if (line.fromUser) NovaColors.Violet else NovaColors.PanelEdge, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(line.text, color = NovaColors.Text, fontSize = 15.sp, lineHeight = 21.sp)
            if (line.note.isNotBlank()) {
                Text(line.note, color = NovaColors.Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
    }
}

private fun choiceLabel(c: BrainChoice) = if (c == BrainChoice.CUSTOM) "LLMsRelay" else c.label

private fun modelOf(store: SettingsStore, c: BrainChoice): String = when (c) {
    BrainChoice.GEMINI -> store.geminiModel
    BrainChoice.OPENROUTER -> store.openRouterModel
    BrainChoice.NVIDIA -> store.nvidiaModel
    BrainChoice.CUSTOM -> store.openAiModel
    else -> ""
}

private fun setModel(store: SettingsStore, c: BrainChoice, v: String) {
    when (c) {
        BrainChoice.GEMINI -> store.geminiModel = v
        BrainChoice.OPENROUTER -> store.openRouterModel = v
        BrainChoice.NVIDIA -> store.nvidiaModel = v
        BrainChoice.CUSTOM -> store.openAiModel = v
        else -> {}
    }
}

private fun hasKey(store: SettingsStore, c: BrainChoice): Boolean = when (c) {
    BrainChoice.GEMINI -> store.geminiKey.isNotBlank()
    BrainChoice.OPENROUTER -> store.openRouterKey.isNotBlank()
    BrainChoice.NVIDIA -> store.nvidiaKey.isNotBlank()
    BrainChoice.CUSTOM -> store.openAiKey.isNotBlank()
    else -> true
}

/** One line "Model: Auto ▾" that opens the list of AI providers and the model name of the chosen one. */
@Composable
private fun ModelPicker(store: SettingsStore) {
    var open by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val current = store.brain
    val model = modelOf(store, current)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Model: " + choiceLabel(current) + (if (model.isNotBlank()) " • $model" else "") + if (open) "  ▴" else "  ▾",
            color = NovaColors.Lavender,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NovaColors.Panel)
                .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
        if (open) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrainChoice.values().forEach { c ->
                    ToolChip(choiceLabel(c), current == c, { store.brain = c })
                }
            }
            when {
                current == BrainChoice.AUTO ->
                    HintText("Auto: NOVA kaam dekh kar khud chunti hai. Aam baatein Gemini se, coding OpenRouter/LLMsRelay se, aur ek na chale to agla.")
                current == BrainChoice.OFFLINE ->
                    HintText("Offline: koi AI nahi, sirf samay, date aur calculation.")
                else -> {
                    TextInput("Model ka naam", model) { setModel(store, current, it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Sab models dekho", { showList = true }, Modifier.weight(1f))
                        ToolButton(
                            if (testing) "Test ho raha…" else "Test karo",
                            {
                                scope.launch {
                                    testing = true
                                    val p = ModelCatalog.providerFor(current, store)
                                    testResult = if (p == null) "Pehle is provider ki key daalo." else ApiTester.test(p).second
                                    testing = false
                                }
                            },
                            Modifier.weight(1f),
                            enabled = !testing
                        )
                    }
                    if (testResult.isNotBlank()) HintText(testResult)
                    if (!hasKey(store, current)) {
                        HintText("Is provider ki key nahi hai. Customise → Add API mein daalo, nahi to NOVA dusra provider chalayegi.")
                    } else {
                        HintText("Ye pehle chalega; fail hua to NOVA khud agla provider try karegi.")
                    }
                }
            }
        }
    }
    if (showList && current != BrainChoice.AUTO && current != BrainChoice.OFFLINE) {
        ModelListDialog(
            choice = current,
            title = choiceLabel(current),
            store = store,
            current = model,
            onPick = { picked ->
                setModel(store, current, picked)
                testResult = ""
                showList = false
            },
            onDismiss = { showList = false }
        )
    }
}
