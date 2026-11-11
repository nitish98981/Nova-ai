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
    private val unspeakable = Regex("[*_#`~\\p{So}\\uFE0F]")

    suspend fun reply(userText: String): BrainReply {
        val turns = history.takeLast(MAX_HISTORY) + ChatTurn(true, userText)
        val notes = ArrayList<String>()
        var result: BrainReply? = null

        val providers = providers(notes)
        for (p in providers) {
            try {
                val raw = p.generate(SYSTEM_PROMPT, turns)
                result = finish(raw, p.label)
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notes += "${p.label}: ${e.message}"
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

    private fun providers(notes: MutableList<String>): List<AiProvider> {
        if (store.brain == BrainChoice.OFFLINE) return emptyList()
        val gemini = store.geminiKey.trim().takeIf { it.isNotEmpty() }
            ?.let { GeminiProvider(it, store.geminiModel) }
        val openAi = store.openAiKey.trim().takeIf { it.isNotEmpty() }
            ?.let { OpenAiProvider(it, store.openAiModel, store.openAiBaseUrl) }

        if (gemini == null && openAi == null) {
            notes += "koi API key nahi hai (Settings mein daalo)"
        }
        return when (store.brain) {
            BrainChoice.OPENAI -> listOfNotNull(openAi, gemini)
            else -> listOfNotNull(gemini, openAi)
        }
    }

    private fun finish(raw: String, label: String): BrainReply {
        val tag = emotionTag.find(raw)
        val emotion = tag?.groupValues?.get(1)?.lowercase() ?: "neutral"
        val text = raw.replace(emotionTag, "").replace(unspeakable, "").trim()
        if (text.isEmpty()) throw BrainException("khali jawab")
        return BrainReply(text, emotion, label)
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
