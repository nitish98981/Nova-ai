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
    private val isNvidia get() = base.contains("nvidia.com")

    override suspend fun stream(
        system: String,
        turns: List<ChatTurn>,
        maxTokens: Int,
        onDelta: (String) -> Unit
    ): String {
        val configured = model.trim()
        if (configured.isEmpty()) throw BrainException("model ka naam Settings mein daalo")
        var started = false
        val guarded: (String) -> Unit = { started = true; onDelta(it) }
        val overrideKey = "$base|$configured"
        val first = modelOverride[overrideKey] ?: configured
        try {
            return run(first, system, turns, maxTokens, guarded, true)
        } catch (e: BrainException) {
            val gone = e.code == 404 || e.code == 410
            if (!gone || started || !isNvidia) throw e
            // NVIDIA retires models often. Ask the server what exists now and try the best few.
            val alternatives = discoverNvidia().filter { it != first }.take(3)
            val tried = ArrayList<String>()
            for (alt in alternatives) {
                try {
                    val text = run(alt, system, turns, maxTokens, guarded, true)
                    modelOverride[overrideKey] = alt
                    return text
                } catch (e2: BrainException) {
                    tried += "$alt: ${e2.message}".take(60)
                    if (started || e2.code !in listOf(400, 403, 404, 410)) throw e2
                }
            }
            throw BrainException(
                "model '$first' hat gaya (HTTP ${e.code}). " +
                    if (alternatives.isEmpty()) "Naya model nahi mila, Settings mein model naam badlo."
                    else "Maine ${alternatives.size} naye try kiye par nahi chale: " + tried.joinToString("; "),
                e.code
            )
        }
    }

    private suspend fun run(
        modelName: String,
        system: String,
        turns: List<ChatTurn>,
        maxTokens: Int,
        onDelta: (String) -> Unit,
        allowRetry: Boolean
    ): String {
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
        var sawReasoning = false
        var finish = ""
        Http.postStream(
            url = "$base/chat/completions",
            headers = mapOf("Authorization" to "Bearer $key"),
            body = body.toString()
        ) { line ->
            if (!line.startsWith("data:")) return@postStream
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]" || data.isEmpty()) return@postStream
            val json = try { JSONObject(data) } catch (e: Exception) { return@postStream }
            val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return@postStream
            choice.optString("finish_reason").takeIf { it.isNotEmpty() && it != "null" }?.let { finish = it }
            val delta = choice.optJSONObject("delta") ?: return@postStream
            if (!delta.optString("reasoning_content").isNullOrEmpty() || !delta.optString("reasoning").isNullOrEmpty()) {
                sawReasoning = true
            }
            val t = delta.optString("content")
            if (t.isNotEmpty() && t != "null") {
                full.append(t)
                onDelta(t)
            }
        }
        val text = full.toString().trim()
        if (text.isEmpty()) {
            // Thinking models can spend every token on thinking. Give them more room once.
            if (allowRetry && sawReasoning && maxTokens < 1200) {
                return run(modelName, system, turns, 1500, onDelta, false)
            }
            throw BrainException(
                "khali jawab [$modelName]" + (if (finish.isNotEmpty()) " (finish=$finish)" else "") +
                    (if (sawReasoning) " model sirf soch raha tha, doosra model chuno" else "")
            )
        }
        return text
    }

    /** Asks NVIDIA which models exist right now and ranks plain chat models for voice use. */
    private suspend fun discoverNvidia(): List<String> {
        val raw = Http.getJson("$base/models", mapOf("Authorization" to "Bearer $key")) ?: return emptyList()
        val ids = try {
            val arr = JSONObject(raw).getJSONArray("data")
            (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        } catch (e: Exception) {
            return emptyList()
        }
        val bad = listOf(
            "vision", "-vl", "vila", "neva", "kosmos", "fuyu", "deplot", "guard", "safety", "reward", "embed",
            "rerank", "parse", "translate", "clip", "code", "coder", "reasoning", "content", "retriev",
            "diffusion", "cosmos", "calibration", "palmyra", "llama2", "gemma-2b", "recurrent", "omni"
        )
        val usable = ids.filter { id ->
            val l = id.lowercase()
            bad.none { l.contains(it) } && (l.contains("instruct") || l.endsWith("-it") || l.contains("nemotron") ||
                l.contains("flash") || l.contains("mistral") || l.contains("gemma") || l.contains("llama"))
        }
        val preferred = listOf(
            "mistral-nemo-12b-instruct", "llama-3.1-8b-instruct", "llama-3.1-nemotron-70b-instruct", "mistral-7b-instruct",
            "gemma-4", "nemotron-3.5-lightning", "nemotron-nano", "llama-3.3", "flash", "mistral-large", "llama"
        )
        return usable.sortedBy { id ->
            val l = id.lowercase()
            val rank = preferred.indexOfFirst { l.contains(it) }
            if (rank < 0) preferred.size else rank
        }
    }

    companion object {
        /** Remembers which replacement model worked, per endpoint and configured name. */
        private val modelOverride = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
