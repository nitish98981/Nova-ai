package com.nova.ai.brain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Tiny built-in brain: works without internet or API keys. */
class OfflineBrain {

    private val calcRegex = Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/x×÷])\\s*(-?\\d+(?:\\.\\d+)?)")

    fun reply(input: String): String {
        val t = input.lowercase(Locale.ROOT).trim()
        val words = t.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
        fun has(vararg keys: String) = keys.any { if (it.contains(' ')) t.contains(it) else it in words }

        calc(t)?.let { return it }

        if (has("time", "samay", "टाइम", "समय", "kitne baje", "कितने बजे")) {
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            return "Abhi samay hai $now."
        }
        if (has("date", "tarikh", "tareekh", "तारीख", "aaj kaun sa din", "कौन सा दिन")) {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
            return "Aaj $today hai."
        }
        if (has("kaun ho", "who are you", "tum kaun", "तुम कौन")) {
            return "Main NOVA hoon, tumhari AI saathi. Abhi offline mode mein hoon, isliye zyada baatein nahi kar sakti."
        }
        if (has("hello", "hi", "hey", "hii", "namaste", "नमस्ते", "हैलो")) {
            return "Hey! Main NOVA hoon. Kaise ho?"
        }
        return "Abhi online AI nahi chal raha, isliye main sirf samay, date aur simple calculation kar sakti hoon."
    }

    private fun calc(t: String): String? {
        val m = calcRegex.find(t) ?: return null
        val a = m.groupValues[1].toDouble()
        val b = m.groupValues[3].toDouble()
        val r = when (m.groupValues[2]) {
            "+" -> a + b
            "-" -> a - b
            "*", "x", "×" -> a * b
            else -> {
                if (b == 0.0) return "Zero se divide nahi ho sakta."
                a / b
            }
        }
        val shown = if (r % 1.0 == 0.0 && abs(r) < 1e15) r.toLong().toString()
        else "%.4f".format(Locale.ENGLISH, r).trimEnd('0').trimEnd('.')
        return "Jawab hai $shown."
    }
}
