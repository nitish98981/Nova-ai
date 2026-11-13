package com.nova.ai.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nova.ai.brain.ModelCatalog
import com.nova.ai.brain.ModelItem
import com.nova.ai.data.BrainChoice
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.ui.tools.ToolButton
import com.nova.ai.ui.tools.ToolChip
import kotlin.coroutines.cancellation.CancellationException

/** Full-screen list of every model the provider offers, with search. Tap one to use it. */
@Composable
fun ModelListDialog(
    choice: BrainChoice,
    title: String,
    store: SettingsStore,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var items by remember { mutableStateOf<List<ModelItem>?>(null) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var onlyChat by remember { mutableStateOf(false) }

    LaunchedEffect(choice) {
        try {
            items = ModelCatalog.list(choice, store)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Models ki list nahi aayi"
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(NovaColors.Void)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$title ke models",
                    color = NovaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                ToolButton("Band karo", onDismiss)
            }
            val list = items
            when {
                error.isNotEmpty() -> HintText(error)
                list == null -> HintText("List la rahi hoon…")
                else -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Naam se dhundo (jaise llama, nemotron)", color = NovaColors.Muted) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip("Sab", !onlyChat, { onlyChat = false })
                        ToolChip("Sirf chat wale", onlyChat, { onlyChat = true })
                    }
                    val shown = list.filter {
                        (!onlyChat || it.chat) && it.id.contains(query.trim(), ignoreCase = true)
                    }
                    HintText("${shown.size} models • abhi: ${current.ifBlank { "koi nahi" }}")
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(shown, key = { it.id }) { m ->
                            val selected = m.id == current
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (selected) NovaColors.Violet.copy(alpha = 0.25f) else NovaColors.Panel)
                                    .clickable { onPick(m.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(m.id, color = NovaColors.Text, fontSize = 14.sp)
                                val sub = listOfNotNull(
                                    m.note.takeIf { it.isNotBlank() },
                                    if (!m.chat) "chat ke liye nahi" else null
                                ).joinToString(" • ")
                                if (sub.isNotEmpty()) Text(sub, color = NovaColors.Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
