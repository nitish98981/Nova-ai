package com.nova.ai.ui.customise

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.ScreenFrame
import com.nova.ai.ui.common.SectionLabel
import com.nova.ai.ui.theme.NovaColors

@Composable
fun WakeWordScreen(store: SettingsStore, onBack: () -> Unit) {
    ScreenFrame("Hey Nova", onBack) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\"Hey Nova\" bolkar NOVA ko bulao",
                color = NovaColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f)
            )
            Switch(checked = store.wakeWord, onCheckedChange = { store.wakeWord = it })
        }
        HintText("Jab ye chalu ho aur NOVA ki screen khuli ho, NOVA lagatar tumhari awaaz sunti rehti hai. \"Hey Nova\" sunte hi wo jawab ke liye taiyaar ho jaati hai. Aap seedha bhi bol sakte ho, jaise \"Hey Nova YouTube kholo\".")

        SectionLabel("Zaroori baatein")
        HintText("• Ye sirf tab chalta hai jab NOVA app screen par khuli ho. App band ya doosre app mein jaane par ye ruk jaata hai (background wala version abhi nahi bana).")
        HintText("• Sunne ke liye Google ka speech recognition use hota hai, isliye internet chahiye aur jab ye chalu hai tab tumhari awaaz Google ko jaati hai. Private baatein karte waqt isse band rakho.")
        HintText("• Lagatar sunne se battery zyada kharch hoti hai. Kuch phones par thodi thodi der mein chhoti beep bhi aa sakti hai.")
        HintText("• Mic permission chahiye (Customise → Accessibility → Mic aur notification permission).")
    }
}
