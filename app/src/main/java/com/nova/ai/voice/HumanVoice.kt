package com.nova.ai.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Base64
import com.nova.ai.brain.BrainException
import com.nova.ai.brain.Http
import com.nova.ai.data.SettingsStore
import com.nova.ai.data.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Natural, human-sounding voice using Gemini text-to-speech (same Gemini key as the AI brain).
 *
 * The first sentence of a reply is sent right away; the rest is joined into one second request,
 * so a normal reply costs at most two calls. If a call fails, the remaining text of that reply is
 * spoken by the phone's own voice and Gemini voice is skipped for one minute.
 */
class HumanVoice(
    private val store: SettingsStore,
    private val scope: CoroutineScope,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** First audio is about to play. */
        fun onPlaybackStart()

        /** Everything of this reply has been played. */
        fun onAllDone()

        /** Gemini voice failed: speak this text with the phone voice instead. */
        fun onFallback(text: String)

        /** Short status for the Settings screen ("" = fine). */
        fun onNote(note: String)
    }

    private class Pcm(val bytes: ByteArray, val rate: Int)
    private class Pending(val text: String, val audio: Deferred<Pcm?>)

    @Volatile
    var busy = false
        private set

    @Volatile
    private var blockedUntil = 0L

    @Volatile
    private var track: AudioTrack? = null

    private var channel: Channel<Pending>? = null
    private var player: Job? = null
    private val inflight = ArrayList<Deferred<Pcm?>>()
    private var firstSent = false
    private val tail = StringBuilder()
    private var emotion = "neutral"

    fun available(): Boolean =
        store.voiceEngine == VoiceEngine.HUMAN &&
            store.geminiKey.isNotBlank() &&
            System.currentTimeMillis() >= blockedUntil

    /** Starts a new spoken reply (stops whatever was playing). */
    fun begin() {
        stop()
        val ch = Channel<Pending>(Channel.UNLIMITED)
        channel = ch
        firstSent = false
        tail.setLength(0)
        busy = true
        startPlayer(ch)
    }

    /** Adds text to the current reply. The first piece goes out immediately, the rest is joined. */
    fun add(text: String, emo: String) {
        val ch = channel ?: return
        if (text.isBlank()) return
        emotion = emo
        if (!firstSent) {
            firstSent = true
            submit(ch, text.trim())
        } else {
            tail.append(text.trim()).append(' ')
        }
    }

    /** No more text will come for this reply. */
    fun end() {
        val ch = channel ?: return
        if (tail.isNotBlank()) {
            submit(ch, tail.toString().trim())
            tail.setLength(0)
        }
        ch.close()
    }

    fun stop() {
        player?.cancel()
        player = null
        inflight.forEach { it.cancel() }
        inflight.clear()
        channel?.close()
        channel = null
        releaseTrack()
        busy = false
    }

    fun release() = stop()

    // ------------------------------------------------------------------ synthesis

    private fun submit(ch: Channel<Pending>, text: String) {
        val emo = emotion
        val key = store.geminiKey.trim()
        val model = store.ttsModel.trim().ifEmpty { DEFAULT_MODEL }
        val voice = store.humanVoice.ifEmpty { "Leda" }
        val job = scope.async(Dispatchers.IO) { synth(key, model, voice, text, emo) }
        inflight += job
        ch.trySend(Pending(text, job))
    }

    private suspend fun synth(key: String, model: String, voice: String, text: String, emo: String): Pcm? {
        val models = listOf(model, if (model == DEFAULT_MODEL) "gemini-3.8-flash-tts" else DEFAULT_MODEL).distinct()
        var lastError = ""
        for (m in models) {
            try {
                val resp = Http.postJson(
                    url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent",
                    headers = mapOf("x-goog-api-key" to key),
                    body = requestBody(voice, text, emo),
                    readTimeoutMs = 25000
                )
                val pcm = parse(resp)
                if (pcm != null) {
                    withContext(Dispatchers.Main.immediate) { callbacks.onNote("") }
                    return pcm
                }
                lastError = "$m: audio nahi mila"
            } catch (e: CancellationException) {
                throw e
            } catch (e: BrainException) {
                lastError = "$m: ${e.message}"
                if (!e.tryOtherModel) break
            } catch (e: Exception) {
                lastError = "$m: ${e.message}"
                break
            }
        }
        withContext(Dispatchers.Main.immediate) { callbacks.onNote(lastError.take(160)) }
        return null
    }

    private fun requestBody(voice: String, text: String, emo: String): String {
        val part = JSONObject().put("text", text)
        val style = styleFor(emo)
        if (style.isNotEmpty()) part.put("speech_metadata", JSONObject().put("style", style))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(part))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("voice", voice)))
            )
            .toString()
    }

    private fun styleFor(emo: String): String = when (emo) {
        "happy" -> "cheerful and friendly"
        "playful" -> "playful and teasing"
        "love" -> "warm and affectionate"
        "thinking" -> "thoughtful, a little slower"
        "surprised" -> "surprised and excited"
        "sad" -> "soft and sad"
        "shy" -> "shy and soft-spoken"
        else -> ""
    }

    private fun parse(resp: String): Pcm? {
        val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
            val data = inline.optString("data")
            if (data.isEmpty()) continue
            val mime = inline.optString("mimeType", inline.optString("mime_type", ""))
            return toPcm(Base64.decode(data, Base64.DEFAULT), mime)
        }
        return null
    }

    /** The API returns either a WAV file (24 kHz, mono, 16-bit) or raw PCM. Handle both. */
    private fun toPcm(bytes: ByteArray, mime: String): Pcm {
        var rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
        val isWav = bytes.size > 44 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        if (!isWav) return Pcm(bytes.copyOf(bytes.size and 1.inv()), rate)

        val wavRate = le32(bytes, 24)
        if (wavRate in 8000..48000) rate = wavRate
        var i = 12
        while (i + 8 <= bytes.size) {
            val id = String(bytes, i, 4, Charsets.US_ASCII)
            val size = le32(bytes, i + 4)
            if (id == "data") {
                val start = i + 8
                val len = if (size < 0 || start + size > bytes.size) bytes.size - start else size
                return Pcm(bytes.copyOfRange(start, start + (len and 1.inv())), rate)
            }
            i += 8 + size + (size and 1)
        }
        return Pcm(bytes.copyOfRange(44, bytes.size - ((bytes.size - 44) and 1)), rate)
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    // ------------------------------------------------------------------ playback

    private fun startPlayer(ch: Channel<Pending>) {
        player = scope.launch(Dispatchers.IO) {
            var started = false
            var degraded = false
            try {
                for (item in ch) {
                    if (degraded) {
                        item.audio.cancel()
                        withContext(Dispatchers.Main.immediate) { callbacks.onFallback(item.text) }
                        continue
                    }
                    val pcm = try {
                        item.audio.await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (pcm == null) {
                        degraded = true
                        blockedUntil = System.currentTimeMillis() + 60_000
                        withContext(Dispatchers.Main.immediate) { callbacks.onFallback(item.text) }
                        continue
                    }
                    if (!started) {
                        started = true
                        withContext(Dispatchers.Main.immediate) { callbacks.onPlaybackStart() }
                    }
                    play(pcm)
                }
            } finally {
                releaseTrack()
            }
            if (isActive) {
                withContext(Dispatchers.Main.immediate) {
                    busy = false
                    callbacks.onAllDone()
                }
            }
        }
    }

    private suspend fun play(pcm: Pcm) {
        val rate = pcm.rate
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 16384))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        try {
            t.play()
            val speed = store.speed
            if (speed != 1f) {
                try {
                    t.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
                } catch (e: Exception) {
                    // some phones do not support changing speed; play at normal speed
                }
            }
            var off = 0
            while (off < pcm.bytes.size) {
                currentCoroutineContext().ensureActive()
                val n = t.write(pcm.bytes, off, minOf(4096, pcm.bytes.size - off))
                if (n <= 0) break
                off += n
            }
            val frames = pcm.bytes.size / 2
            val deadline = System.currentTimeMillis() +
                (frames * 1000L / rate / maxOf(speed, 0.5f)).toLong() + 1500
            while (t.playbackHeadPosition < frames && System.currentTimeMillis() < deadline) {
                delay(25)
            }
        } finally {
            if (track === t) track = null
            try {
                t.pause()
                t.flush()
                t.release()
            } catch (e: Exception) {
                // already released
            }
        }
    }

    private fun releaseTrack() {
        val t = track ?: return
        track = null
        try {
            t.pause()
            t.flush()
            t.release()
        } catch (e: Exception) {
            // already released
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash-lite-tts"

        /** Gemini studio voices that usually sound female. Google does not label gender, so audition them. */
        val VOICES = listOf(
            "Leda" to "Youthful",
            "Aoede" to "Breezy",
            "Zephyr" to "Bright",
            "Kore" to "Firm",
            "Sulafat" to "Warm",
            "Achernar" to "Soft",
            "Vindemiatrix" to "Gentle",
            "Laomedeia" to "Upbeat",
            "Callirrhoe" to "Easy-going",
            "Despina" to "Smooth"
        )
    }
}
