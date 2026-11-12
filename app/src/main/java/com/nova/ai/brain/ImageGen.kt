package com.nova.ai.brain

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Text-to-image with Gemini's image models ("Nano Banana"). Uses the Gemini key. */
object ImageGen {
    const val DEFAULT_MODEL = "gemini-3.1-flash-lite-image"

    class Result(val bytes: ByteArray, val mime: String)

    suspend fun generate(key: String, model: String, prompt: String): Result {
        if (key.isBlank()) throw BrainException("Image banane ke liye pehle Gemini API key daalo (Customise → Add API)")
        val m = model.trim().removePrefix("models/").ifEmpty { DEFAULT_MODEL }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
            .toString()
        val resp = Http.postJson(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent",
            headers = mapOf("x-goog-api-key" to key.trim()),
            body = body,
            readTimeoutMs = 90000
        )
        val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw BrainException("Image nahi bani (jawab khali ya blocked)")
        var last: Result? = null
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.optBoolean("thought", false)) continue
            val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
            val data = inline.optString("data")
            if (data.isEmpty()) continue
            val mime = inline.optString("mimeType", inline.optString("mime_type", "image/png"))
            last = Result(Base64.decode(data, Base64.DEFAULT), mime.ifEmpty { "image/png" })
        }
        return last ?: throw BrainException("Image nahi bani. Prompt badal kar dobara try karo")
    }
}
