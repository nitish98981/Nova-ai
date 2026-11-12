package com.nova.ai.control

import android.content.Context
import android.content.Intent
import java.util.Locale

data class AppInfo(val label: String, val pkg: String)

/** Finds installed, launchable apps by (spoken) name. */
class AppResolver(private val ctx: Context) {

    private val spokenAliases = mapOf(
        "insta" to "instagram",
        "fb" to "facebook",
        "whatsapp" to "whatsapp",
        "wa" to "whatsapp",
        "setting" to "settings",
        "playstore" to "play store",
        "yt" to "youtube",
        "maps" to "maps",
        "map" to "maps",
        "mail" to "gmail",
        "photos" to "photos",
        "gallery" to "gallery"
    )

    private fun launchable(): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    fun find(spoken: String): AppInfo? {
        val raw = spoken.lowercase(Locale.ROOT).trim()
        if (raw.length < 2) return null
        val q = spokenAliases[raw] ?: raw
        val apps = launchable()
        return apps.firstOrNull { it.label.lowercase(Locale.ROOT) == q }
            ?: apps.firstOrNull { it.label.lowercase(Locale.ROOT).startsWith(q) }
            ?: apps.firstOrNull { it.label.lowercase(Locale.ROOT).contains(q) }
            ?: apps.firstOrNull { q.length >= 4 && q.contains(it.label.lowercase(Locale.ROOT)) && it.label.length >= 4 }
    }
}
