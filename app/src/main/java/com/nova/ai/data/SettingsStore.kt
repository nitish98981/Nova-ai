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

enum class VoiceEngine(val label: String) {
    HUMAN("Human (Gemini)"),
    PHONE("Phone (Android)")
}

enum class BrainChoice(val label: String) {
    AUTO("Auto"),
    GEMINI("Gemini"),
    OPENROUTER("OpenRouter"),
    NVIDIA("NVIDIA"),
    CUSTOM("Custom"),
    OFFLINE("Offline")
}

/** A saved list of voice commands NOVA runs one after another ("good morning routine chalao"). */
data class Routine(val name: String, val steps: List<String>)

private fun loadRoutines(raw: String): List<Routine> = try {
    val arr = org.json.JSONArray(raw)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val steps = o.getJSONArray("steps")
        Routine(o.getString("name"), (0 until steps.length()).map { steps.getString(it) })
    }
} catch (e: Exception) {
    emptyList()
}

private fun routinesToJson(list: List<Routine>): String {
    val arr = org.json.JSONArray()
    for (r in list) {
        arr.put(
            org.json.JSONObject().put("name", r.name).put("steps", org.json.JSONArray(r.steps))
        )
    }
    return arr.toString()
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
    private var _voiceEngine by mutableStateOf(
        runCatching { VoiceEngine.valueOf(prefs.getString("voice_engine", "HUMAN")!!) }
            .getOrDefault(VoiceEngine.HUMAN)
    )
    private var _humanVoice by mutableStateOf(prefs.getString("human_voice", "Leda") ?: "Leda")
    private var _ttsModel by mutableStateOf(
        prefs.getString("tts_model", "gemini-3.8-flash-lite-tts") ?: "gemini-3.8-flash-lite-tts"
    )
    private var _showAvatar by mutableStateOf(prefs.getBoolean("show_avatar", true))
    private var _phoneControl by mutableStateOf(prefs.getBoolean("phone_control", false))
    private var _brain by mutableStateOf(
        runCatching { BrainChoice.valueOf(prefs.getString("brain", "AUTO")!!) }
            .getOrDefault(BrainChoice.AUTO)
    )
    private var _geminiModel by mutableStateOf(prefs.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash")
    private var _openAiModel by mutableStateOf(prefs.getString("openai_model", "") ?: "")
    private var _openAiBaseUrl by mutableStateOf(prefs.getString("openai_base_url", "") ?: "")
    private var _openRouterKey by mutableStateOf(prefs.getString("openrouter_key", "") ?: "")
    private var _openRouterModel by mutableStateOf(prefs.getString("openrouter_model", "openrouter/free") ?: "openrouter/free")
    private var _nvidiaKey by mutableStateOf(prefs.getString("nvidia_key", "") ?: "")
    private var _nvidiaModel by mutableStateOf(
        prefs.getString("nvidia_model", "meta/llama-3.3-70b-instruct") ?: "meta/llama-3.3-70b-instruct"
    )

    private var _routines by mutableStateOf(loadRoutines(prefs.getString("routines", "[]") ?: "[]"))

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

    /** HUMAN = natural Gemini voice, PHONE = Android's own voice. */
    var voiceEngine: VoiceEngine
        get() = _voiceEngine
        set(v) { _voiceEngine = v; prefs.edit().putString("voice_engine", v.name).apply() }

    /** Name of the Gemini studio voice (Leda, Aoede, Kore...). */
    var humanVoice: String
        get() = _humanVoice
        set(v) { _humanVoice = v; prefs.edit().putString("human_voice", v).apply() }

    var ttsModel: String
        get() = _ttsModel
        set(v) { _ttsModel = v; prefs.edit().putString("tts_model", v).apply() }

    /** Which AI brain to try first. Others are used automatically if it fails. */
    var brain: BrainChoice
        get() = _brain
        set(v) { _brain = v; prefs.edit().putString("brain", v.name).apply() }

    var geminiModel: String
        get() = _geminiModel
        set(v) { _geminiModel = v; prefs.edit().putString("gemini_model", v).apply() }

    var openAiModel: String
        get() = _openAiModel
        set(v) { _openAiModel = v; prefs.edit().putString("openai_model", v).apply() }

    /** Custom OpenAI-compatible endpoint (llmsrelay, Groq, ...). Base URL, key and model. */
    var openAiBaseUrl: String
        get() = _openAiBaseUrl
        set(v) { _openAiBaseUrl = v; prefs.edit().putString("openai_base_url", v).apply() }

    var openRouterKey: String
        get() = _openRouterKey
        set(v) { _openRouterKey = v; prefs.edit().putString("openrouter_key", v).apply() }

    var openRouterModel: String
        get() = _openRouterModel
        set(v) { _openRouterModel = v; prefs.edit().putString("openrouter_model", v).apply() }

    var nvidiaKey: String
        get() = _nvidiaKey
        set(v) { _nvidiaKey = v; prefs.edit().putString("nvidia_key", v).apply() }

    var nvidiaModel: String
        get() = _nvidiaModel
        set(v) { _nvidiaModel = v; prefs.edit().putString("nvidia_model", v).apply() }

    var routines: List<Routine>
        get() = _routines
        set(v) { _routines = v; prefs.edit().putString("routines", routinesToJson(v)).apply() }

    var showAvatar: Boolean
        get() = _showAvatar
        set(v) { _showAvatar = v; prefs.edit().putBoolean("show_avatar", v).apply() }

    /** Master switch: NOVA may open apps, search, set alarms and (with Accessibility) tap/scroll. Off by default. */
    var phoneControl: Boolean
        get() = _phoneControl
        set(v) { _phoneControl = v; prefs.edit().putBoolean("phone_control", v).apply() }
}
