package com.nova.ai.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.theme.NovaColors

@Composable
fun ToolHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Wapas", tint = NovaColors.Lavender)
        }
        Text(title, color = NovaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ToolButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (enabled) NovaColors.Violet.copy(alpha = 0.25f) else NovaColors.Panel)
            .border(1.dp, if (enabled) NovaColors.Violet else NovaColors.PanelEdge, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) NovaColors.Text else NovaColors.Muted, fontSize = 14.sp)
    }
}

@Composable
fun ToolChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) NovaColors.Violet.copy(alpha = 0.3f) else NovaColors.Panel)
            .border(1.dp, if (selected) NovaColors.Violet else NovaColors.PanelEdge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) NovaColors.Text else NovaColors.Muted, fontSize = 13.sp)
    }
}

/** Removes markdown symbols/emoji and keeps the text short enough for text-to-speech. */
fun spokenText(t: String): String =
    t.replace(Regex("[*_#`~\\p{So}\\uFE0F]"), "").replace(Regex("\\s*\\n+\\s*"), ". ").trim().take(700)
