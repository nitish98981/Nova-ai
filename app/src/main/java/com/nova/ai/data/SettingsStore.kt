package com.nova.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class VoiceLanguage(val label: String) {
    HINGLISH("Hinglish"),
    HINDI("Hindi"),
    ENGLISH("English")
}

/** Settings are saved encrypted on the device. API keys never leave the phone. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "nova_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var _language by mutableStateOf(
        runCatching { VoiceLanguage.valueOf(prefs.getString("language", "HINGLISH")!!) }
            .getOrDefault(VoiceLanguage.HINGLISH)
    )
    private var _speed by mutableFloatStateOf(prefs.getFloat("speed", 1f))
    private var _pitch by mutableFloatStateOf(prefs.getFloat("pitch", 1.1f))
    private var _geminiKey by mutableStateOf(prefs.getString("gemini_key", "") ?: "")
    private var _openAiKey by mutableStateOf(prefs.getString("openai_key", "") ?: "")
    private var _voiceEn by mutableStateOf(prefs.getString("voice_en", "") ?: "")
    private var _voiceHi by mutableStateOf(prefs.getString("voice_hi", "") ?: "")

    var language: VoiceLanguage
        get() = _language
        set(v) { _language = v; prefs.edit().putString("language", v.name).apply() }

    var speed: Float
        get() = _speed
        set(v) { _speed = v; prefs.edit().putFloat("speed", v).apply() }

    var pitch: Float
        get() = _pitch
        set(v) { _pitch = v; prefs.edit().putFloat("pitch", v).apply() }

    var geminiKey: String
        get() = _geminiKey
        set(v) { _geminiKey = v; prefs.edit().putString("gemini_key", v).apply() }

    var openAiKey: String
        get() = _openAiKey
        set(v) { _openAiKey = v; prefs.edit().putString("openai_key", v).apply() }

    /** Name of the chosen English/Hinglish TTS voice ("" = phone default). */
    var voiceEn: String
        get() = _voiceEn
        set(v) { _voiceEn = v; prefs.edit().putString("voice_en", v).apply() }

    /** Name of the chosen Hindi TTS voice ("" = phone default). */
    var voiceHi: String
        get() = _voiceHi
        set(v) { _voiceHi = v; prefs.edit().putString("voice_hi", v).apply() }
}
