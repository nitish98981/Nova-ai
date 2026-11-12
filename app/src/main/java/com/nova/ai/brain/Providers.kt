package com.nova.ai.brain

import org.json.JSONArray
import org.json.JSONObject

data class ChatTurn(val fromUser: Boolean, val text: String)

interface AiProvider {
    val label: String

    /** Streams the answer: [onDelta] gets each piece of text as it arrives. Returns the full text. */
    suspend fun stream(
        system: String,
        turns: List<ChatTurn>,
        maxTokens: Int = 300,
        onDelta: (String) -> Unit
    ): String
}

class GeminiProvider(private val key: String, model: String) : AiProvider {
    private val primaryModel = model.trim().removePrefix("models/").ifEmpty { "gemini-3.5-flash" }
    override val label = "Gemini"

    override suspend fun stream(
        system: String,
        turns: List<ChatTurn>,
        maxTokens: Int,
        onDelta: (String) -> Unit
    ): String {
        val errors = ArrayList<String>()
        var started = false
        val guarded: (String) -> Unit = { started = true; onDelta(it) }
        for (m in listOf(primaryModel, "gemini-3.5-flash-lite").distinct()) {
            try {
                return callWithThinkingFallback(m, system, turns, maxTokens, guarded)
            } catch (e: BrainException) {
                errors += "$m: ${e.message}"
                // Once words have been spoken we cannot restart with another model.
                if (started || !e.tryOtherModel) throw BrainException(errors.joinToString(" / "), e.code)
            }
        }
        throw BrainException(errors.joinToString(" / "))
    }

    /** Thinking makes Gemini 3.x slow for chat. Ask for minimal thinking; if the model rejects it, remember and retry. */
    private suspend fun callWithThinkingFallback(
        model: String, system: String, turns: List<ChatTurn>, maxTokens: Int, onDelta: (String) -> Unit
    ): String {
        // Short voice answers: minimal thinking for speed. Long (coding) answers keep default thinking for quality.
        val fast = maxTokens <= 400
        if (!fast || model in thinkingRejected) return call(model, system, turns, maxTokens, false, onDelta)
        return try {
            call(model, system, turns, maxTokens, true, onDelta)
        } catch (e: BrainException) {
            if (e.code == 400) {
                thinkingRejected += model
                call(model, system, turns, maxTokens, false, onDelta)
            } else throw e
        }
    }

    private suspend fun call(
        model: String, system: String, turns: List<ChatTurn>, maxTokens: Int, minimalThinking: Boolean,
        onDelta: (String) -> Unit
    ): String {
        val contents = JSONArray()
        for (t in turns) {
            contents.put(
                JSONObject()
                    .put("role", if (t.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        val gen = JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.9)
        if (minimalThinking) {
            val thinking = if (model.contains("2.5")) JSONObject().put("thinkingBudget", 0)
            else JSONObject().put("thinkingLevel", "minimal")
            gen.put("thinkingConfig", thinking)
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", gen)

        val full = StringBuilder()
        var blocked = ""
        var finish = ""
        Http.postStream(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse",
            headers = mapOf("x-goog-api-key" to key),
            body = body.toString()
        ) { line ->
            if (!line.startsWith("data:")) return@postStream
            val json = try { JSONObject(line.removePrefix("data:").trim()) } catch (e: Exception) { return@postStream }
            val cand = json.optJSONArray("candidates")?.optJSONObject(0)
            if (cand == null) {
                blocked = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                return@postStream
            }
            cand.optString("finishReason").takeIf { it.isNotEmpty() }?.let { finish = it }
            val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: return@postStream
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                val t = p.optString("text")
                if (t.isNotEmpty()) {
                    full.append(t)
                    onDelta(t)
                }
            }
        }
        val text = full.toString().trim()
        if (text.isEmpty()) throw BrainException("khali/blocked jawab ($blocked$finish) [$model]")
        return text
    }

    companion object {
        private val thinkingRejected = java.util.Collections.synchronizedSet(HashSet<String>())
    }
}

/** Works with any OpenAI-compatible service: OpenRouter, NVIDIA, llmsrelay, Groq, OpenAI... */
class OpenAiProvider(
    override val label: String,
    private val key: String,
    private val model: String,
    baseUrl: String
) : AiProvider {
    private val base = baseUrl.trim().trimEnd('/')

    override suspend fun stream(
        system: String,
        turns: List<ChatTurn>,
        maxTokens: Int,
        onDelta: (String) -> Unit
    ): String {
        val modelName = model.trim()
        if (modelName.isEmpty()) throw BrainException("model ka naam Settings mein daalo")
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (t in turns) {
            messages.put(
                JSONObject().put("role", if (t.fromUser) "user" else "assistant").put("content", t.text)
            )
        }
        // api.openai.com wants max_completion_tokens; most other services still expect max_tokens.
        val tokensField = if (base.contains("api.openai.com")) "max_completion_tokens" else "max_tokens"
        val body = JSONObject()
            .put("model", modelName)
            .put("messages", messages)
            .put("stream", true)
            .put(tokensField, maxTokens)

        val full = StringBuilder()
        Http.postStream(
            url = "$base/chat/completions",
            headers = mapOf("Authorization" to "Bearer $key"),
            body = body.toString()
        ) { line ->
            if (!line.startsWith("data:")) return@postStream
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]" || data.isEmpty()) return@postStream
            val json = try { JSONObject(data) } catch (e: Exception) { return@postStream }
            val t = json.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("delta")?.optString("content").orEmpty()
            if (t.isNotEmpty() && t != "null") {
                full.append(t)
                onDelta(t)
            }
        }
        val text = full.toString().trim()
        if (text.isEmpty()) throw BrainException("khali jawab [$modelName]")
        return text
    }
}
