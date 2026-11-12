package com.nova.ai.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.data.Routine
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.theme.NovaColors

@Composable
fun RoutinesScreen(store: SettingsStore, onRun: (String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ToolHeader("Routines", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Routine = kuch commands ek ke baad ek. Bolkar chalane ke liye bolo: \"<naam> routine chalao\". " +
                    "Iske liye Settings mein Phone control chalu hona chahiye. Naam chhota rakho aur usi tarah likho jaise bolte ho.",
                color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
            )

            for (r in store.routines) {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(NovaColors.Panel)
                        .border(1.dp, NovaColors.PanelEdge, shape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(r.name, color = NovaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        r.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
                        color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Chalao", { onRun(r.name) })
                        ToolButton("Edit", {
                            name = r.name
                            steps = r.steps.joinToString("\n")
                        })
                        ToolButton("Delete", { store.routines = store.routines.filter { it.name != r.name } })
                    }
                }
            }

            Text("Nayi routine", color = NovaColors.Lavender, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Naam (jaise: subah)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = steps,
                onValueChange = { steps = it },
                label = { Text("Har line mein ek command") },
                placeholder = { Text("YouTube kholo\nvolume badhao\n5 minute ka timer", color = NovaColors.Muted) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            val lines = steps.lines().map { it.trim() }.filter { it.isNotEmpty() }
            ToolButton(
                "Save karo",
                {
                    val n = name.trim()
                    store.routines = store.routines.filter { !it.name.equals(n, ignoreCase = true) } + Routine(n, lines)
                    name = ""
                    steps = ""
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && lines.isNotEmpty()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
