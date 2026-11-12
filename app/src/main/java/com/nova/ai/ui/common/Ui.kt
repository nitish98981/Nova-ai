package com.nova.ai.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.ui.tools.ToolButton
import com.nova.ai.ui.tools.ToolHeader

/** Standard screen: back arrow + title on top, scrolling body below. */
@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ToolHeader(title, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = NovaColors.Lavender, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun HintText(text: String) {
    Text(text, color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
}

/** Big tappable card: title, optional second line, optional status on the right. */
@Composable
fun MenuCard(title: String, subtitle: String = "", onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NovaColors.Panel)
            .border(1.dp, NovaColors.PanelEdge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = NovaColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        color = if (selected) NovaColors.Void else NovaColors.Text,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) NovaColors.Lavender else NovaColors.Panel)
            .border(1.dp, NovaColors.PanelEdge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

@Composable
fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
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
fun TextInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Wide button used for Add / Continue / Back rows. */
@Composable
fun WideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ToolButton(text, onClick, modifier.fillMaxWidth(), enabled)
}

@Composable
fun CenteredRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
