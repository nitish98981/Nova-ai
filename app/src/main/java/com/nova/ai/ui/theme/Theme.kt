package com.nova.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NovaColors {
    val Void = Color(0xFF0A0612)
    val Panel = Color(0xFF150E24)
    val PanelEdge = Color(0xFF2A1F45)
    val Violet = Color(0xFF8B5CF6)
    val Lavender = Color(0xFFC4B5FD)
    val Cyan = Color(0xFF22D3EE)
    val Text = Color(0xFFEDE9FE)
    val Muted = Color(0xFF9A8FB8)
    val Good = Color(0xFF4ADE80)
}

private val Scheme = darkColorScheme(
    primary = NovaColors.Violet,
    onPrimary = NovaColors.Text,
    secondary = NovaColors.Cyan,
    background = NovaColors.Void,
    onBackground = NovaColors.Text,
    surface = NovaColors.Panel,
    onSurface = NovaColors.Text,
    onSurfaceVariant = NovaColors.Muted,
    outline = NovaColors.PanelEdge
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
