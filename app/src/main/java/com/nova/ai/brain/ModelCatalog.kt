package com.nova.ai.brain

import com.nova.ai.data.BrainChoice
import com.nova.ai.data.SettingsStore
import org.json.JSONObject

data class ModelItem(val id: String, val note: String = "", val chat: Boolean = true)

/** Asks a provider which models it offers right now, so the user can pick one from a list. */
object ModelCatalog {

    // Words that mark models which are not plain text chat (images, safety filters, embeddings, ...).
    private val notChat = listOf(
        "vision", "-vl", "vila", "neva", "kosmos", "fuyu", "deplot", "guard", "safety", "reward", "embed",
        "rerank", "parse", "translate", "clip", "diffusion", "cosmos", "calibration", "retriev", "imagen",
        "veo", "tts", "aqa", "image", "audio", "whisper", "parakeet", "canary", "riva"
    )

    private fun isChat(id: String): Boolean {
        val l = id.lowercase()
        return notChat.none { l.contains(it) }
    }

    fun providerFor(choice: BrainChoice, store: SettingsStore): AiProvider? {
        fun key(v: String) = v.trim().takeIf { it.isNotEmpty() }
        return when (choice) {
            BrainChoice.GEMINI -> key(store.geminiKey)?.let { GeminiProvider(it, store.geminiModel) }
            BrainChoice.OPENROUTER -> key(store.openRouterKey)?.let {
                OpenAiProvider("OpenRouter", it, store.openRouterModel, "https://openrouter.ai/api/v1")
            }
            BrainChoice.NVIDIA -> key(store.nvidiaKey)?.let {
                OpenAiProvider("NVIDIA", it, store.nvidiaModel, "https://integrate.api.nvidia.com/v1")
            }
            BrainChoice.CUSTOM -> key(store.openAiKey)?.takeIf { store.openAiBaseUrl.isNotBlank() }?.let {
                OpenAiProvider("Custom", it, store.openAiModel, store.openAiBaseUrl)
            }
            else -> null
        }
    }

    /** Throws BrainException with a readable reason if the list cannot be fetched. */
    suspend fun list(choice: BrainChoice, store: SettingsStore): List<ModelItem> {
        val items: List<ModelItem> = when (choice) {
            BrainChoice.GEMINI -> {
                val key = store.geminiKey.trim()
                if (key.isEmpty()) throw BrainException("Pehle Gemini ki key daalo (Customise → Add API)")
                val raw = Http.getText(
                    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                    mapOf("x-goog-api-key" to key)
                )
                val arr = JSONObject(raw).optJSONArray("models")
                val out = ArrayList<ModelItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val m = arr.getJSONObject(i)
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        var canChat = false
                        if (methods != null) for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") canChat = true
                        }
                        if (!canChat) continue
                        val id = m.optString("name").removePrefix("models/")
                        if (id.isNotEmpty()) out += ModelItem(id, m.optString("displayName"), isChat(id))
                    }
                }
                out
            }
            BrainChoice.OPENROUTER -> parseOpenAiList(Http.getText("https://openrouter.ai/api/v1/models"), true)
            BrainChoice.NVIDIA -> {
                val key = store.nvidiaKey.trim()
                val headers = if (key.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $key")
                parseOpenAiList(Http.getText("https://integrate.api.nvidia.com/v1/models", headers), false)
            }
            BrainChoice.CUSTOM -> {
                val base = store.openAiBaseUrl.trim().trimEnd('/')
                if (base.isEmpty()) throw BrainException("Pehle Base URL daalo (Customise → Add API)")
                val key = store.openAiKey.trim()
                val headers = if (key.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $key")
                parseOpenAiList(Http.getText("$base/models", headers), false)
            }
            else -> emptyList()
        }
        return items.distinctBy { it.id }
            .sortedWith(
                compareByDescending<ModelItem> { it.chat }
                    .thenByDescending { it.note == "free" }
                    .thenBy { it.id.lowercase() }
            )
    }

    private fun parseOpenAiList(raw: String, markFree: Boolean): List<ModelItem> {
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<ModelItem>()
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).optString("id")
            if (id.isEmpty()) continue
            val free = markFree && (id.endsWith(":free") || id == "openrouter/free")
            out += ModelItem(id, if (free) "free" else "", isChat(id))
        }
        return out
    }
}
