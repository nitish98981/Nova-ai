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
import com.nova.ai.brain.Brain
import com.nova.ai.control.ActionPlan
import com.nova.ai.control.CommandRouter
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceLanguage
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NovaState { Idle, Listening, Thinking, Speaking }

/**
 * Phase 2 voice engine: Android SpeechRecognizer (listening) + Android TextToSpeech (speaking).
 * Must be created and used on the main thread.
 */
class VoiceController(context: Context, private val store: SettingsStore) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val brain = Brain(store)
    private val router = CommandRouter(appContext, store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var thinkingJob: Job? = null
    /** Bumped whenever a reply is interrupted or replaced, so late sentences from the old one are dropped. */
    private var generation = 0
    /** Number of sentences queued in TTS and not finished yet. */
    private var pendingSpeech = 0

    var state by mutableStateOf(NovaState.Idle); private set
    var heard by mutableStateOf(""); private set
    var message by mutableStateOf(GREETING); private set
    var level by mutableFloatStateOf(0f); private set
    var source by mutableStateOf(""); private set
    /** Last emotion tag from the AI (happy, playful...). Used by the avatar in Phase 4. */
    var emotion by mutableStateOf("neutral"); private set
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
                main.post { utteranceFinished() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { utteranceFinished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { utteranceFinished() }
            }
        })
        voices = (t.voices ?: emptySet())
            .filter { it.locale.language == "en" || it.locale.language == "hi" }
            .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
    }

    private fun utteranceFinished() {
        if (pendingSpeech > 0) pendingSpeech--
        if (pendingSpeech == 0 && state == NovaState.Speaking) state = NovaState.Idle
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
            NovaState.Speaking, NovaState.Thinking -> {
                generation++
                thinkingJob?.cancel()
                tts?.stop()
                pendingSpeech = 0
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
            // Stop waiting for more words sooner after the user finishes speaking.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }
        recognizer = r
        heard = ""
        level = 0f
        state = NovaState.Listening
        message = "Sun rahi hoon…"
        source = ""
        emotion = "neutral"
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

    /** Sends what the user said to the AI brain and speaks the answer. */
    private fun respondTo(text: String) {
        val plan = router.tryHandle(text)
        if (plan != null) {
            runPlan(plan)
            return
        }
        state = NovaState.Thinking
        message = "Soch rahi hoon…"
        source = ""
        val myGen = ++generation
        thinkingJob = scope.launch {
            try {
                var firstSentence = true
                val r = brain.reply(text) { emo, sentence ->
                    // Called from a background thread while the answer is still streaming in.
                    main.post {
                        if (myGen != generation) return@post
                        if (firstSentence) {
                            emotion = emo
                            message = sentence
                            speak(sentence, flush = true)
                            firstSentence = false
                        } else {
                            message = "$message $sentence"
                            speak(sentence, flush = false)
                        }
                    }
                }
                if (myGen != generation) return@launch
                source = r.source
                if (firstSentence) {
                    // Nothing was streamed (offline brain): speak the whole answer now.
                    emotion = r.emotion
                    message = r.text
                    speak(r.text)
                } else {
                    emotion = r.emotion
                }
                if (state == NovaState.Thinking) state = if (pendingSpeech > 0) NovaState.Speaking else NovaState.Idle
            } catch (e: Exception) {
                if (myGen != generation) return@launch
                state = NovaState.Idle
                message = "Kuch gadbad ho gayi: ${e.message}"
            }
        }
    }

    /** Phone-control command: speak first, then do the job (and maybe speak a follow-up). */
    private fun runPlan(plan: ActionPlan) {
        emotion = plan.emotion
        message = plan.reply
        source = "Phone control"
        state = NovaState.Idle
        speak(plan.reply)
        val job = plan.run ?: return
        thinkingJob = scope.launch {
            delay(900)
            val follow = try {
                job()
            } catch (e: Exception) {
                "Kaam karte waqt gadbad ho gayi: ${e.message}"
            }
            if (!follow.isNullOrBlank()) {
                message = follow
                speak(follow)
            }
        }
    }

    private fun hasDevanagari(text: String) = text.any { it in '\u0900'..'\u097F' }

    fun speak(text: String, flush: Boolean = true) {
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
        pendingSpeech++
        if (state == NovaState.Thinking) state = NovaState.Speaking
        t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "nova-" + System.nanoTime())
    }

    /** Plays a short sample so the user can compare voices and sliders. */
    fun preview(lang: String) {
        speak(if (lang == "hi") "नमस्ते, मैं नोवा हूँ। तुम्हारी मदद के लिए तैयार हूँ।" else "Hello, I am Nova. Ready to help you.")
    }

    fun release() {
        scope.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val GREETING =
            "Hey! Main NOVA hoon. Mic dabao aur mujhse baat karo."
    }
}
