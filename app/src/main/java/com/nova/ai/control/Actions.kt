package com.nova.ai.control

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.view.KeyEvent

/** Things NOVA can do with normal Android intents. None of these bypass any Android security. */
class Actions(private val ctx: Context) {

    private fun launch(intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    fun launchPackage(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return launch(i)
    }

    fun openYoutube(): Boolean =
        launchPackage(YOUTUBE) || launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))

    fun youtubeSearch(query: String): Boolean {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        return launch(Intent(Intent.ACTION_VIEW, uri).setPackage(YOUTUBE)) ||
            launch(Intent(Intent.ACTION_VIEW, uri))
    }

    fun googleSearch(query: String): Boolean =
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))

    /** Opens the official Play Store search. Installing is always done by the user. */
    fun playStoreSearch(query: String): Boolean {
        val q = Uri.encode(query)
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps"))) ||
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")))
    }

    fun openUrl(url: String): Boolean = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun setAlarm(hour: Int, minute: Int): Boolean = launch(
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "NOVA")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    )

    fun setTimer(seconds: Int): Boolean = launch(
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "NOVA")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    )

    fun mediaKey(code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun volume(up: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
    }

    companion object {
        const val YOUTUBE = "com.google.android.youtube"
    }
}
