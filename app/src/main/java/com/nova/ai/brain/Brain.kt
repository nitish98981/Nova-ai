package com.nova.ai.brain

import com.nova.ai.data.BrainChoice
import com.nova.ai.data.SettingsStore
import kotlin.coroutines.cancellation.CancellationException

data class BrainReply(val text: String, val emotion: String, val source: String)

/**
 * Picks an AI provider, falls back automatically, and always ends with the offline brain.
 * Conversation memory lives only in RAM for now (cleared when the app closes).
 */
class Brain(private val store: SettingsStore) {

    private val history = ArrayList<ChatTurn>()
    private val offline = OfflineBrain()
    private val emotionTag = Regex("^\\s*\\[(\\w+)\\]\\s*")
    private val codingWords = Regex(
        "(?i)(\\b(code|coding|kotlin|java|python|javascript|html|css|bug|debug|error|exception|function|class|" +
            "compile|compiler|gradle|script|program|programming|algorithm|api|sql|json|github|git)\\b)|" +
            "c\\+\\+|c#|कोड|कोडिंग|प्रोग्राम|डीबग|एरर|फंक्शन"
    )
    private val unspeakable = Regex("[*_#`~\\p{So}\\uFE0F]")
    private val sentenceEnd = Regex("[.!?\u0964]+\\s")
    private val softBreak = Regex("[,;:]\\s")

    /**
     * Streams the answer. [onSentence] is called (possibly from a background thread) with the emotion and
     * each finished sentence as soon as it is ready, so NOVA can start speaking before the full answer exists.
     */
    suspend fun reply(userText: String, onSentence: (emotion: String, sentence: String) -> Unit = { _, _ -> }): BrainReply {
        val turns = history.takeLast(MAX_HISTORY) + ChatTurn(true, userText)
        val notes = ArrayList<String>()
        var result: BrainReply? = null

        val providers = providers(notes, userText)
        for (p in providers) {
            val streamer = SentenceStreamer(onSentence)
            try {
                p.stream(SYSTEM_PROMPT, turns) { streamer.push(it) }
                streamer.finish()
                if (streamer.spoken.isBlank()) throw BrainException("khali jawab")
                result = BrainReply(streamer.spoken.trim(), streamer.emotion, p.label)
                if (notes.isNotEmpty()) {
                    result = result.copy(source = result.source + " • pehle fail: " + notes.joinToString(" | "))
                }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (streamer.emittedCount > 0) {
                    // Some sentences were already spoken; keep them instead of restarting with another provider.
                    streamer.finish()
                    result = BrainReply(streamer.spoken.trim(), streamer.emotion, p.label + " (adhura)")
                    break
                }
                notes += "${p.label}: ${e.message}".take(100)
            }
        }

        val out = result ?: BrainReply(
            text = offline.reply(userText),
            emotion = "neutral",
            source = if (notes.isEmpty()) "Offline" else "Offline • " + notes.joinToString(" | ")
        )
        history += ChatTurn(true, userText)
        history += ChatTurn(false, out.text)
        while (history.size > MAX_HISTORY) history.removeAt(0)
        return out
    }

    /**
     * Long, text-only answer (coding assistant). Streams into [onDelta]; returns the full text and provider name.
     * Uses the same provider order and fallback as voice chat, but allows much longer answers.
     */
    suspend fun answerLong(
        system: String,
        turns: List<ChatTurn>,
        userText: String,
        onDelta: (String) -> Unit
    ): Pair<String, String> {
        val notes = ArrayList<String>()
        val list = providers(notes, userText)
        if (list.isEmpty()) {
            throw BrainException(notes.joinToString(" | ").ifEmpty { "koi AI provider ready nahi (Settings mein key daalo)" })
        }
        for (p in list) {
            var started = false
            try {
                val text = p.stream(system, turns, 2000) { started = true; onDelta(it) }
                return text to p.label
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (started) throw BrainException("${p.label} beech mein ruk gaya: ${e.message}")
                notes += "${p.label}: ${e.message}".take(100)
            }
        }
        throw BrainException(notes.joinToString(" | "))
    }

    /** Cuts a streamed answer into speakable sentences and pulls the [emotion] tag off the front. */
    private inner class SentenceStreamer(private val onSentence: (String, String) -> Unit) {
        private val buf = StringBuilder()
        private val full = StringBuilder()
        private var tagDone = false
        var emotion = "neutral"
            private set
        var emittedCount = 0
            private set
        val spoken: String get() = full.toString()

        fun push(delta: String) {
            buf.append(delta)
            if (!tagDone) {
                val s = buf.toString().trimStart()
                if (s.isEmpty()) return
                if (s.startsWith("[")) {
                    val m = emotionTag.find(buf.toString())
                    if (m != null) {
                        emotion = m.groupValues[1].lowercase()
                        buf.delete(0, m.range.last + 1)
                        tagDone = true
                    } else if (s.length <= 24) {
                        return // tag may still be arriving
                    } else {
                        tagDone = true
                    }
                } else {
                    tagDone = true
                }
            }
            drain()
        }

        fun finish() {
            if (!tagDone) {
                val m = emotionTag.find(buf.toString())
                if (m != null) {
                    emotion = m.groupValues[1].lowercase()
                    buf.delete(0, m.range.last + 1)
                }
                tagDone = true
            }
            drain()
            if (buf.isNotBlank()) emit(buf.toString())
            buf.setLength(0)
        }

        private fun drain() {
            while (true) {
                val text = buf.toString()
                var m = sentenceEnd.find(text)
                // First sentence: allow a comma break so speech starts sooner.
                if (m == null && emittedCount == 0 && text.length >= 40) {
                    m = softBreak.find(text, 20)
                }
                if (m == null) return
                emit(text.substring(0, m.range.last + 1))
                buf.delete(0, m.range.last + 1)
            }
        }

        private fun emit(raw: String) {
            val clean = raw.replace(unspeakable, "").trim()
            if (clean.isEmpty()) return
            emittedCount++
            full.append(clean).append(' ')
            onSentence(emotion, clean)
        }
    }

    private fun providers(notes: MutableList<String>, userText: String): List<AiProvider> {
        if (store.brain == BrainChoice.OFFLINE) return emptyList()

        fun key(v: String) = v.trim().takeIf { it.isNotEmpty() }

        val gemini = key(store.geminiKey)?.let { GeminiProvider(it, store.geminiModel) }
        val openRouter = key(store.openRouterKey)?.let {
            OpenAiProvider("OpenRouter", it, store.openRouterModel, "https://openrouter.ai/api/v1")
        }
        val nvidia = key(store.nvidiaKey)?.let {
            OpenAiProvider("NVIDIA", it, store.nvidiaModel, "https://integrate.api.nvidia.com/v1")
        }
        val custom = key(store.openAiKey)?.let {
            if (store.openAiBaseUrl.isBlank()) {
                notes += "Custom: Base URL nahi hai"
                null
            } else {
                OpenAiProvider("Custom", it, store.openAiModel, store.openAiBaseUrl)
            }
        }

        // Smart router (Auto mode): general chat -> Gemini first, coding -> OpenRouter / Custom first.
        val coding = codingWords.containsMatchIn(userText)
        val all = if (coding) {
            listOfNotNull(openRouter, custom, gemini, nvidia)
        } else {
            listOfNotNull(gemini, nvidia, custom, openRouter)
        }
        if (all.isEmpty() && notes.isEmpty()) {
            notes += "koi API key nahi hai (Settings mein daalo)"
        }

        val preferred = when (store.brain) {
            BrainChoice.GEMINI -> gemini
            BrainChoice.OPENROUTER -> openRouter
            BrainChoice.NVIDIA -> nvidia
            BrainChoice.CUSTOM -> custom
            else -> null
        }
        if (preferred == null && store.brain != BrainChoice.AUTO && all.isNotEmpty()) {
            notes += "${store.brain.label} ki key nahi hai"
        }
        return listOfNotNull(preferred) + all.filter { it !== preferred }
    }

    companion object {
        private const val MAX_HISTORY = 12

        private val SYSTEM_PROMPT = """
            Tum NOVA ho: ek friendly, caring aur playful anime-style AI saathi, jo Android phone par voice assistant ki tarah kaam karti hai.
            Niyam:
            - User ki bhasha follow karo. Agar user Hindi (Devanagari) mein bole to Devanagari mein jawab do, warna Roman Hinglish (Hindi aur English ka mix) mein.
            - Jawab chhote rakho: 1 se 3 chhote vakya, kyunki tumhara jawab awaaz mein bola jayega.
            - Markdown, bullet list, emoji ya special symbols bilkul mat use karo.
            - Meethi, caring aur thodi chhedne wali (friendly teasing) bano, par kabhi robotic ya formal nahi.
            - Padhai, coding aur rozmarra ke kaamon mein supportive raho. Coding samjhate waqt chhote steps mein bolo.
            - Agar tumhe kuch pata nahi to seedha bol do, andaza mat lagao.
            - Phone control, camera aur app kholna abhi tumhare paas nahi hai (baad mein aayega). Unka dikhawa mat karo.
            - Har jawab ki shuruaat ek emotion tag se karo, in mein se ek: [happy] [playful] [love] [thinking] [surprised] [sad] [shy] [neutral]. Tag ke baad normal jawab likho.
        """.trimIndent()
    }
}
