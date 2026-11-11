package com.nova.ai.brain

import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class ChatTurn(val fromUser: Boolean, val text: String)

interface AiProvider {
    val label: String
    suspend fun generate(system: String, turns: List<ChatTurn>): String
}

class GeminiProvider(private val key: String, model: String) : AiProvider {
    private val primaryModel = model.trim().removePrefix("models/").ifEmpty { "gemini-2.5-flash" }
    override val label = "Gemini"

    override suspend fun generate(system: String, turns: List<ChatTurn>): String {
        var last: BrainException? = null
        for (m in listOf(primaryModel, "gemini-2.5-flash-lite").distinct()) {
            try {
                return call(m, system, turns)
            } catch (e: BrainException) {
                last = e
                if (!e.tryOtherModel) throw e
            }
        }
        throw last ?: BrainException("Gemini ne jawab nahi diya")
    }

    private suspend fun call(model: String, system: String, turns: List<ChatTurn>): String {
        val contents = JSONArray()
        for (t in turns) {
            contents.put(
                JSONObject()
                    .put("role", if (t.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1024).put("temperature", 0.9))

        val resp = Http.postJson(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            headers = mapOf("x-goog-api-key" to key),
            body = body.toString()
        )
        val json = JSONObject(resp)
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw BrainException(
                "jawab blocked/khali (" + json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty() + ")"
            )
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
            }
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) throw BrainException("khali jawab (${candidate.optString("finishReason")}) [$model]")
        return text
    }
}

/** Works with OpenAI and any OpenAI-compatible service (Groq, OpenRouter, ...). */
class OpenAiProvider(private val key: String, private val model: String, baseUrl: String) : AiProvider {
    private val base = baseUrl.trim().trimEnd('/').ifEmpty { "https://api.openai.com/v1" }
    override val label: String = runCatching { URL(base).host }.getOrDefault("OpenAI")
        .let { if (it == "api.openai.com") "OpenAI" else it }

    override suspend fun generate(system: String, turns: List<ChatTurn>): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (t in turns) {
            messages.put(
                JSONObject().put("role", if (t.fromUser) "user" else "assistant").put("content", t.text)
            )
        }
        val body = JSONObject()
            .put("model", model.trim().ifEmpty { "gpt-4.1-mini" })
            .put("messages", messages)
            .put("max_completion_tokens", 400)

        val resp = Http.postJson(
            url = "$base/chat/completions",
            headers = mapOf("Authorization" to "Bearer $key"),
            body = body.toString()
        )
        val text = JSONObject(resp)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty().trim()
        if (text.isEmpty()) throw BrainException("khali jawab")
        return text
    }
}
