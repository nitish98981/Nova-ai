package com.nova.ai.tools

import android.util.Base64
import com.nova.ai.brain.BrainException
import com.nova.ai.brain.Http
import com.nova.ai.data.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

/** Sends one photo plus a question to Gemini and returns its text answer. Only Gemini is used for photos. */
object Vision {

    const val PRODUCT = "Ye photo ek product ki hai. Hinglish mein, chhote points mein batao: " +
        "1) Ye kya hai (brand/model agar dikh raha ho; na dikhe to saaf likho ki ye sirf andaza hai). " +
        "2) Ye kis kaam aata hai aur mukhya features. " +
        "3) India mein lagbhag kitne ka milta hai (sirf andaza, 'approx' likho, live price tumhare paas nahi hai). " +
        "4) Kharidne se pehle kya check karna chahiye. " +
        "Agar label, barcode ya likha hua text dikhe to use padho. Jo photo se pata nahi chalta use mat gadho, bolo ki pata nahi. " +
        "Markdown, star ya emoji mat use karo."

    const val TEXT = "Is photo mein jo bhi text likha hai use poora padho (Hindi ya English), jaisa hai waisa. " +
        "Phir akhir mein 2 line mein Hinglish mein matlab batao. Markdown, star ya emoji mat use karo."

    const val GENERAL = "Is photo mein kya hai, Hinglish mein saaf aur chhote points mein batao. " +
        "Jo dikh nahi raha use mat gadho. Markdown, star ya emoji mat use karo."

    suspend fun ask(store: SettingsStore, prompt: String, jpeg: ByteArray): String {
        val key = store.geminiKey.trim()
        if (key.isEmpty()) throw BrainException("Photo samajhne ke liye Settings mein Gemini API key daalo")
        val model = store.geminiModel.trim().removePrefix("models/").ifEmpty { "gemini-3.5-flash" }

        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                )
            )
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1000).put("temperature", 0.3))

        val resp = Http.postJson(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            headers = mapOf("x-goog-api-key" to key),
            body = body.toString(),
            readTimeoutMs = 60000
        )
        val json = JSONObject(resp)
        val cand = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw BrainException("jawab blocked/khali (" + json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty() + ")")
        val ps = cand.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (ps != null) {
            for (i in 0 until ps.length()) {
                val p = ps.getJSONObject(i)
                if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
            }
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) throw BrainException("khali jawab (${cand.optString("finishReason")})")
        return text
    }
}
