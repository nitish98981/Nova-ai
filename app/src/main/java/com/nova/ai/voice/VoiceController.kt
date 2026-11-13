package com.nova.ai.voice

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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

/** One line of the Chat screen. [note] is small grey text under NOVA's answers (which AI answered). */
data class ChatLine(val fromUser: Boolean, val text: String, val note: String = "")

/** One entry of the Notifications screen (what NOVA did or what went wrong). */
data class ActivityItem(val time: String, val text: String)

/**
 * Phase 2 voice engine: Android SpeechRecognizer (listening) + Android TextToSpeech (speaking).
 * Must be created and used on the main thread.
 */
class VoiceController(context: Context, private val store: SettingsStore) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    val brain = Brain(store)
    private val router = CommandRouter(appContext, store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var thinkingJob: Job? = null
    /** True while the current reply is spoken by the Gemini (human) voice. */
    private var humanReply = false
    private val human = HumanVoice(store, scope, appContext.cacheDir, object : HumanVoice.Callbacks {
        override fun onPlaybackStart() {
            state = NovaState.Speaking
        }

        override fun onAllDone() {
            if (pendingSpeech == 0 && (state == NovaState.Speaking || state == NovaState.Thinking)) {
                state = NovaState.Idle
            }
        }

        override fun onFallback(text: String) {
            speakAndroid(text, flush = false)
        }

        override fun onNote(note: String) {
            humanNote = note
            if (note.isNotBlank()) addActivity("Human voice nahi chali, phone ki awaaz use hui: $note")
        }
    })
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
    /** Everything said in this session (kept in memory only; cleared when the app closes). */
    val chatLog = mutableStateListOf<ChatLine>()

    /** True while an AI answer is still being written/spoken and is not yet in [chatLog]. */
    var replying by mutableStateOf(false); private set

    val activity = mutableStateListOf<ActivityItem>()
    var unread by mutableIntStateOf(0); private set

    fun markRead() {
        unread = 0
    }

    fun clearActivity() {
        activity.clear()
        unread = 0
    }

    private fun addActivity(text: String) {
        val time = java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        activity.add(0, ActivityItem(time, text))
        while (activity.size > 50) activity.removeAt(activity.lastIndex)
        unread++
    }

    /** Last problem with the Gemini voice ("" = none). Shown in Settings. */
    var humanNote by mutableStateOf(""); private set

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
    }

    private fun utteranceFinished() {
        if (pendingSpeech > 0) pendingSpeech--
        if (pendingSpeech == 0 && state == NovaState.Speaking && !human.busy) state = NovaState.Idle
    }

    /** Runs typed/saved text exactly like something the user said (used by routines). */
    fun submitText(text: String) {
        generation++
        thinkingJob?.cancel()
        replying = false
        human.stop()
        tts?.stop()
        recognizer?.cancel()
        level = 0f
        state = NovaState.Idle
        heard = text
        emotion = "neutral"
        source = ""
        respondTo(text)
    }

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
                replying = false
                human.stop()
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
        stopWake()
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
        chatLog += ChatLine(true, text)
        val plan = router.tryHandle(text)
        if (plan != null) {
            runPlan(plan)
            return
        }
        replying = true
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
                            speak(sentence, flush = true, more = true)
                            firstSentence = false
                        } else {
                            message = "$message $sentence"
                            speak(sentence, flush = false, more = true)
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
                    finishReply(r.text, r.source)
                    if (state == NovaState.Thinking && !human.busy) {
                        state = if (pendingSpeech > 0) NovaState.Speaking else NovaState.Idle
                    }
                } else {
                    emotion = r.emotion
                    // Sentences are delivered through main.post, so finish in the same queue to keep order.
                    main.post {
                        if (myGen != generation) return@post
                        finishReply(message, r.source)
                        finishSpeech()
                        if (state == NovaState.Thinking && !human.busy) {
                            state = if (pendingSpeech > 0) NovaState.Speaking else NovaState.Idle
                        }
                    }
                }
            } catch (e: Exception) {
                if (myGen != generation) return@launch
                state = NovaState.Idle
                replying = false
                message = "Kuch gadbad ho gayi: ${e.message}"
                addActivity("AI se jawab nahi mila: ${e.message}")
            }
        }
    }

    /** Phone-control command: speak first, then do the job (and maybe speak a follow-up). */
    private fun runPlan(plan: ActionPlan) {
        emotion = plan.emotion
        message = plan.reply
        source = "Phone control"
        state = NovaState.Idle
        chatLog += ChatLine(false, plan.reply, "Phone control")
        addActivity("Phone control: " + plan.reply)
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
                chatLog += ChatLine(false, follow, "Phone control")
                addActivity(follow)
                speak(follow)
            }
        }
    }

    /** The AI answer is complete: store it in the chat and note any provider that failed on the way. */
    private fun finishReply(text: String, src: String) {
        replying = false
        if (text.isNotBlank()) chatLog += ChatLine(false, text, src)
        if (src.contains("fail") || src.startsWith("Offline")) addActivity("AI: $src")
    }

    private fun hasDevanagari(text: String) = text.any { it in '\u0900'..'\u097F' }

    /**
     * Speaks [text]. [flush] starts a new reply (stops the old one). [more] = true when more
     * sentences of the same reply will follow; call [finishSpeech] after the last one.
     */
    fun speak(text: String, flush: Boolean = true, more: Boolean = false) {
        if (flush) humanReply = human.available()
        if (humanReply) {
            if (flush) {
                tts?.stop()
                pendingSpeech = 0
                human.begin()
            }
            human.add(text, emotion)
            if (!more) human.end()
            return
        }
        speakAndroid(text, flush)
    }

    private fun finishSpeech() {
        if (humanReply) human.end()
    }

    /** The phone's own (Android) voice. Used when the human voice is off or fails. */
    private fun speakAndroid(text: String, flush: Boolean) {
        val t = tts ?: return
        val hindi = hasDevanagari(text)
        val result = t.setLanguage(if (hindi) Locale("hi", "IN") else Locale("en", "IN"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            message = "Is bhasha ki voice install nahi hai. Phone Settings → Text-to-speech se voice data download karo."
            return
        }
        t.setSpeechRate(store.speed)
        t.setPitch(store.pitch)
        pendingSpeech++
        if (state == NovaState.Thinking) state = NovaState.Speaking
        t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "nova-" + System.nanoTime())
    }

    /** Plays a short sample so the user can compare voices and sliders. */
    fun preview() {
        speak("नमस्ते, मैं नोवा हूँ।")
    }

    fun release() {
        stopWake()
        human.release()
        scope.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }


    // ------------------------------------------------------------------ "Hey Nova"

    /** True while NOVA is quietly waiting to hear her name. */
    var wakeListening by mutableStateOf(false); private set

    private var wakeRecognizer: SpeechRecognizer? = null
    private var foreground = true
    private var nextWakeAt = 0L

    // The Google recognizer plays a "tung" sound every time it starts. While NOVA waits for her name this
    // would repeat every few seconds, so the media volume is muted during wake listening and restored after.
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var beepMuted = false

    private fun muteBeep() {
        if (beepMuted) return
        try {
            if (!audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) {
                audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
                beepMuted = true
            }
        } catch (e: Exception) {
        }
    }

    private fun unmuteBeep() {
        if (!beepMuted) return
        beepMuted = false
        try {
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) {
        }
    }

    /** Wake-word listening only runs while the NOVA screen is open. */
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) stopWake()
    }

    private fun tickWake() {
        val micOk = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val want = store.wakeWord && foreground && micOk && state == NovaState.Idle && !human.busy &&
            pendingSpeech == 0 && !replying && thinkingJob?.isActive != true
        if (want && !wakeListening) {
            if (android.os.SystemClock.elapsedRealtime() >= nextWakeAt) startWake()
        } else if (!want && wakeListening) {
            stopWake()
        } else if (!want) {
            unmuteBeep()
        }
    }

    private fun startWake() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return
        wakeRecognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        r.setRecognitionListener(wakeListener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }
        wakeRecognizer = r
        wakeListening = true
        muteBeep()
        r.startListening(intent)
    }

    private fun stopWake() {
        wakeListening = false
        wakeRecognizer?.destroy()
        wakeRecognizer = null
        unmuteBeep()
    }

    /**
     * Returns null if the sentence is not addressed to NOVA, "" if it was only her name
     * ("Hey Nova"), otherwise the command that came after her name.
     */
    private fun wakeCommand(text: String): String? {
        val tokens = text.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        val idx = tokens.indexOfFirst { it.lowercase(Locale.ROOT) in WAKE_NAMES }
        if (idx < 0 || idx > 2) return null
        return tokens.drop(idx + 1).joinToString(" ")
    }

    private val wakeListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onResults(results: Bundle?) {
            wakeListening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            val command = wakeCommand(text)
            if (command == null) {
                nextWakeAt = android.os.SystemClock.elapsedRealtime() + 1000
                return
            }
            unmuteBeep()
            if (command.isBlank()) {
                startListening()
            } else {
                heard = command
                emotion = "neutral"
                source = ""
                respondTo(command)
            }
        }

        override fun onError(error: Int) {
            wakeListening = false
            // Back off a little so a silent room does not restart the recognizer non-stop.
            nextWakeAt = android.os.SystemClock.elapsedRealtime() + 1500
        }
    }

    init {
        scope.launch {
            while (true) {
                delay(700)
                tickWake()
            }
        }
    }

    companion object {
        private val WAKE_NAMES = setOf("nova", "novaa", "नोवा", "नॉवा", "नोवां")

        const val GREETING =
            "Hey! Main NOVA hoon. Mic dabao aur mujhse baat karo."
    }
}
