package com.nova.ai.ui.more

import androidx.compose.runtime.Composable
import com.nova.ai.ui.common.MenuCard
import com.nova.ai.ui.common.ScreenFrame

@Composable
fun MoreScreen(
    onBack: () -> Unit,
    onScanner: () -> Unit,
    onRoutines: () -> Unit,
    onPermissions: () -> Unit,
    onSettings: () -> Unit
) {
    ScreenFrame("More", onBack) {
        MenuCard("Scanner", "Photo khincho ya gallery se chuno: product, text ya koi bhi cheez", onScanner)
        MenuCard("Routines", "Kai commands ek saath, jaise \"good morning routine chalao\"", onRoutines)
        MenuCard("Permissions", "Mic, notification aur accessibility ki sthiti", onPermissions)
        MenuCard("Saari settings", "Speed, pitch, avatar, AI ka mode, model ke naam", onSettings)
    }
}
