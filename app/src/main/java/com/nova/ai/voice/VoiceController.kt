package com.nova.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceLanguage
import java.util.Locale

enum class NovaState { Idle, Listening, Speaking }

/**
 * Phase 2 voice engine: Android SpeechRecognizer (listening) + Android TextToSpeech (speaking).
 * Must be created and used on the main thread.
 */
class VoiceController(context: Context, private val store: SettingsStore) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    var state by mutableStateOf(NovaState.Idle); private set
    var heard by mutableStateOf(""); private set
    var message by mutableStateOf(GREETING); private set
    var level by mutableFloatStateOf(0f); private set
    var voices by mutableStateOf<List<Voice>>(emptyList()); private set

    init {
        tts = TextToSpeech(appContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    onTtsReady()
                } else {
                    message = "Text-to-speech start nahi hua. Phone Settings mein Google TTS engine check karo."
                }
            }
        }
    }

    private fun onTtsReady() {
        val t = tts ?: return
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post { state = NovaState.Speaking }
            }

            override fun onDone(utteranceId: String?) {
                main.post { if (state == NovaState.Speaking) state = NovaState.Idle }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { if (state == NovaState.Speaking) state = NovaState.Idle }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { if (state == NovaState.Speaking) state = NovaState.Idle }
            }
        })
        voices = (t.voices ?: emptySet())
            .filter { it.locale.language == "en" || it.locale.language == "hi" }
            .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
    }

    /** Installed voices for "en" or "hi", Indian ones first. */
    fun voicesFor(lang: String): List<Voice> =
        voices.filter { it.locale.language == lang }
            .sortedWith(compareByDescending<Voice> { it.locale.country == "IN" }.thenBy { it.name })

    fun showNotice(text: String) {
        message = text
    }

    /** Mic button: start listening, stop listening, or interrupt NOVA and listen. */
    fun toggle() {
        when (state) {
            NovaState.Listening -> recognizer?.stopListening()
            NovaState.Speaking -> {
                tts?.stop()
                startListening()
            }
            NovaState.Idle -> startListening()
        }
    }

    private fun recognitionTag(): String = when (store.language) {
        VoiceLanguage.ENGLISH -> "en-IN"
        else -> "hi-IN" // Hinglish speakers are recognised best by the Hindi (India) model
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            message = "Is phone mein speech recognition nahi mila. Google app update/install karo."
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer = r
        heard = ""
        level = 0f
        state = NovaState.Listening
        message = "Sun rahi hoon…"
        r.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) heard = text
        }

        override fun onResults(results: Bundle?) {
            level = 0f
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                state = NovaState.Idle
                message = "Kuch sunai nahi diya. Phir se try karo."
            } else {
                heard = text
                state = NovaState.Idle
                respondTo(text)
            }
        }

        override fun onError(error: Int) {
            level = 0f
            state = NovaState.Idle
            message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Kuch sunai nahi diya. Mic ke paas bolo aur phir try karo."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Internet ki problem hai. Speech recognition ke liye net chahiye."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Mic permission nahi hai. Permissions screen se Allow karo."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Recognizer busy hai. Ek second ruk kar phir try karo."
                else -> "Sunne mein error aaya (code $error). Phir try karo."
            }
        }
    }

    /** Phase 2 reply: repeat what was heard. The real AI brain arrives in Phase 3. */
    private fun respondTo(text: String) {
        val reply = if (hasDevanagari(text)) {
            "तुमने कहा: $text। मेरा AI दिमाग़ अगले चरण में जुड़ेगा।"
        } else {
            "Tumne kaha: $text. Mera AI dimaag next phase mein judega."
        }
        message = reply
        speak(reply)
    }

    private fun hasDevanagari(text: String) = text.any { it in '\u0900'..'\u097F' }

    fun speak(text: String) {
        val t = tts ?: return
        val hindi = hasDevanagari(text)
        val savedName = if (hindi) store.voiceHi else store.voiceEn
        val chosen = if (savedName.isNotEmpty()) voices.firstOrNull { it.name == savedName } else null
        if (chosen != null) {
            t.setVoice(chosen)
        } else {
            val result = t.setLanguage(if (hindi) Locale("hi", "IN") else Locale("en", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                message = "Is bhasha ki voice install nahi hai. Phone Settings → Text-to-speech se voice data download karo."
                return
            }
        }
        t.setSpeechRate(store.speed)
        t.setPitch(store.pitch)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova-" + System.nanoTime())
    }

    /** Plays a short sample so the user can compare voices and sliders. */
    fun preview(lang: String) {
        speak(if (lang == "hi") "नमस्ते, मैं नोवा हूँ। तुम्हारी मदद के लिए तैयार हूँ।" else "Hello, I am Nova. Ready to help you.")
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val GREETING =
            "Hey! Main NOVA hoon. Mic dabao aur bolo — main sunkar dohra dungi. Asli AI dimaag Phase 3 mein judega."
    }
}
