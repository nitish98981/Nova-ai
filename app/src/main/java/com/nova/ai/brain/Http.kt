package com.nova.ai.brain

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BrainException(message: String, val code: Int = 0) : Exception(message) {
    /** True when trying a different model of the same provider may help. */
    val tryOtherModel: Boolean get() = code == 404 || code == 429 || code == 500 || code == 503
}

internal object Http {
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        readTimeoutMs: Int = 20000
    ): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 8000
                c.readTimeout = readTimeoutMs
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    throw BrainException("HTTP $code ${errorMessage(text)}".trim(), code)
                }
                text
            } catch (e: IOException) {
                throw BrainException("internet/connection problem")
            } finally {
                c.disconnect()
            }
        }

    /**
     * Streaming POST (Server-Sent Events). [onLine] gets every non-empty line as it arrives.
     * readTimeout is also the "first word" timeout: if the service sends nothing for 10 s we give up
     * and the next provider is tried, instead of making the user wait.
     */
    suspend fun postStream(url: String, headers: Map<String, String>, body: String, onLine: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val ctx = currentCoroutineContext()
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 6000
                c.readTimeout = 10000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setRequestProperty("Accept", "text/event-stream")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code !in 200..299) {
                    val text = c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    throw BrainException("HTTP $code ${errorMessage(text)}".trim(), code)
                }
                c.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                    while (true) {
                        ctx.ensureActive()
                        val line = r.readLine() ?: break
                        if (line.isNotEmpty()) onLine(line)
                    }
                }
            } catch (e: IOException) {
                throw BrainException("internet/connection problem")
            } finally {
                c.disconnect()
            }
        }
    }

    private fun errorMessage(body: String): String = try {
        val m = JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        if (m.length > 140) m.take(140) + "…" else m
    } catch (e: Exception) {
        ""
    }
}
