package com.nova.ai.control

import android.content.Context
import android.view.KeyEvent
import com.nova.ai.data.SettingsStore
import java.util.Locale
import kotlinx.coroutines.delay

/** What NOVA says right away, and an optional follow-up job that runs after she starts speaking. */
class ActionPlan(
    val reply: String,
    val emotion: String = "happy",
    val run: (suspend () -> String?)? = null
)

private sealed class Command {
    data class OpenApp(val name: String) : Command()
    object OpenYoutube : Command()
    data class YoutubeSearch(val query: String, val playIndex: Int?) : Command()
    data class GoogleSearch(val query: String) : Command()
    data class PlayStore(val query: String) : Command()
    data class OpenUrl(val url: String) : Command()
    data class Alarm(val hour: Int, val minute: Int) : Command()
    data class Timer(val seconds: Int) : Command()
    data class Media(val key: Int, val word: String) : Command()
    data class Volume(val up: Boolean) : Command()
    object Back : Command()
    object Home : Command()
    object Recents : Command()
    object Notifications : Command()
    data class Scroll(val down: Boolean) : Command()
    object ReadScreen : Command()
    data class Tap(val target: String) : Command()
}

/**
 * Understands simple Hinglish/Hindi/English phone commands without any AI call.
 * Anything it does not recognise returns null and goes to the AI brain instead.
 */
class CommandRouter(private val ctx: Context, private val store: SettingsStore) {

    private val actions = Actions(ctx)
    private val apps = AppResolver(ctx)

    fun tryHandle(text: String): ActionPlan? {
        val command = parse(text) ?: return null
        if (!store.phoneControl) {
            return ActionPlan(
                "Phone control abhi band hai. Settings mein Phone control chalu karo, phir mujhe bolna.",
                "neutral"
            )
        }
        return execute(command)
    }

    // ------------------------------------------------------------------ parsing

    private fun parse(text: String): Command? {
        val lower = text.lowercase(Locale.ROOT)
        val orig = text.split(Regex("[^\\p{L}\\p{M}\\p{N}.]+")).map { it.trim('.') }.filter { it.isNotEmpty() }
        val low = orig.map { norm(it.lowercase(Locale.ROOT)) }
        if (low.isEmpty()) return null

        fun has(vararg k: String) = k.any { it in low }
        fun query(extraStop: Set<String> = emptySet()): String =
            orig.filterIndexed { i, _ -> low[i] !in STOP && low[i] !in extraStop }.joinToString(" ").trim()
        val short = low.size <= 4
        val hasVerb = low.any { it in OPEN || it in SEARCH }

        if (has("alarm")) parseAlarm(lower)?.let { return Command.Alarm(it.first, it.second) }
        if (has("timer")) parseTimer(lower)?.let { return Command.Timer(it) }

        if (has("volume")) {
            return when {
                has("up", "badhao", "badha", "increase", "zyada", "jyada", "tez", "बढ़ाओ", "बढ़ा") -> Command.Volume(true)
                has("down", "kam", "ghatao", "decrease", "low", "कम", "घटाओ") -> Command.Volume(false)
                else -> null
            }
        }

        if (has("youtube")) {
            val idx = low.firstNotNullOfOrNull { ORDINALS[it] }
            val q = query(if (idx != null) ORDINALS.keys + "video" + "वीडियो" else emptySet())
            if (hasVerb && q.isNotEmpty()) return Command.YoutubeSearch(q, idx)
            if (hasVerb) return Command.OpenYoutube
        }

        val playStoreWord = lower.contains("play store") || lower.contains("playstore") || lower.contains("प्ले स्टोर")
        if (playStoreWord || has("install", "download", "इंस्टॉल", "डाउनलोड")) {
            val q = query()
            if (q.isNotEmpty()) return Command.PlayStore(q)
            if (playStoreWord && hasVerb) return Command.OpenApp("play store")
        }

        DOMAIN.find(lower)?.let { m ->
            if (hasVerb || short) return Command.OpenUrl("https://" + m.groupValues[1])
        }

        if (has("google")) {
            val q = query()
            if (hasVerb && q.isNotEmpty()) return Command.GoogleSearch(q)
            if (hasVerb) return Command.OpenApp("google")
        }

        if (short) {
            when {
                has("pause", "पॉज", "पॉज़") -> return Command.Media(KeyEvent.KEYCODE_MEDIA_PAUSE, "pause")
                has("resume", "play", "चलाओ", "chalao") && low.size <= 2 ->
                    return Command.Media(KeyEvent.KEYCODE_MEDIA_PLAY, "play")
                has("next", "agla", "अगला") -> return Command.Media(KeyEvent.KEYCODE_MEDIA_NEXT, "next")
                has("previous", "prev", "pichla", "पिछला") -> return Command.Media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "previous")
                has("back", "wapas", "वापस", "पीछे", "piche") -> return Command.Back
                has("home", "होम") -> return Command.Home
                has("recent", "recents", "रीसेंट") -> return Command.Recents
                has("notification", "notifications", "नोटिफिकेशन") -> return Command.Notifications
            }
        }

        if (has("scroll", "स्क्रॉल")) {
            return Command.Scroll(!has("up", "upar", "ऊपर"))
        }
        if (has("screen", "स्क्रीन") && has("padho", "read", "पढ़ो", "पढो", "batao", "बताओ", "kya", "क्या")) {
            return Command.ReadScreen
        }
        if (has("tap", "click", "dabao", "दबाओ", "टैप", "क्लिक")) {
            val q = query()
            if (q.isNotEmpty()) return Command.Tap(q)
        }

        val q = query()
        if (low.any { it in OPEN } && q.isNotEmpty() && q.split(" ").size <= 3) return Command.OpenApp(q)
        if (low.any { it in SEARCH_STRICT } && q.isNotEmpty()) return Command.GoogleSearch(q)
        return null
    }

    private fun norm(t: String): String = NORMAL[t] ?: t

    private fun parseAlarm(lower: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(lower) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val pm = listOf("shaam", "sham", "शाम", "raat", "रात", "evening", "night", "pm", "dopahar", "दोपहर", "afternoon")
            .any { lower.contains(it) }
        val am = listOf("subah", "सुबह", "morning", " am").any { lower.contains(it) }
        if (pm && hour < 12) hour += 12
        if (am && hour == 12) hour = 0
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    private fun parseTimer(lower: String): Int? {
        var total = 0
        val re = Regex("(\\d+)\\s*(second|sec|सेकंड|सेकेंड|minute|min|मिनट|hour|ghanta|ghante|घंटे|घंटा)")
        for (m in re.findAll(lower)) {
            val n = m.groupValues[1].toInt()
            total += when (m.groupValues[2]) {
                "second", "sec", "सेकंड", "सेकेंड" -> n
                "minute", "min", "मिनट" -> n * 60
                else -> n * 3600
            }
        }
        return if (total in 1..86400) total else null
    }

    // ---------------------------------------------------------------- execution

    private fun pick(vararg options: String) = options.random()

    private fun execute(c: Command): ActionPlan = when (c) {
        is Command.OpenApp -> {
            val app = apps.find(c.name)
            if (app == null) {
                ActionPlan("${c.name} naam ka app mujhe nahi mila.", "sad")
            } else {
                ActionPlan(
                    pick("Okayyy! ${app.label} khol rahi hoon. Ek second!", "Ji, ${app.label} khol rahi hoon."),
                    "happy",
                    run = { actions.launchPackage(app.pkg); null }
                )
            }
        }
        Command.OpenYoutube -> ActionPlan(
            pick("Okayyy! YouTube khol rahi hoon. Ek second!", "YouTube aa raha hai!"),
            "happy",
            run = { actions.openYoutube(); null }
        )
        is Command.YoutubeSearch -> {
            val playing = c.playIndex != null
            val noA11y = playing && NovaAccessibilityService.instance == null
            val reply = when {
                noA11y -> "YouTube par ${c.query} search kar rahi hoon. Video chalane ke liye Accessibility chalu karni padegi, abhi sirf search khol rahi hoon."
                playing -> "YouTube par ${c.query} dhund kar ${c.playIndex} number ka video chalati hoon."
                else -> "Okayyy! YouTube par ${c.query} search kar rahi hoon."
            }
            ActionPlan(reply, "playful", run = {
                actions.youtubeSearch(c.query)
                if (playing && !noA11y) {
                    delay(5000)
                    val s = NovaAccessibilityService.instance
                    when {
                        s == null -> "Accessibility band ho gayi, video nahi chala paayi."
                        s.guard() != null -> s.guard()
                        s.clickNthByDescription("play video", c.playIndex!!) -> null
                        else -> "Video mujhe screen par nahi mila. Tum khud tap kar do."
                    }
                } else null
            })
        }
        is Command.GoogleSearch -> ActionPlan(
            pick("Google par ${c.query} search kar rahi hoon.", "Okayyy! ${c.query} dhundti hoon."),
            "happy",
            run = { actions.googleSearch(c.query); null }
        )
        is Command.PlayStore -> ActionPlan(
            "Play Store mein ${c.query} dhund rahi hoon. Install karna tumhare haath mein hai, main sirf page kholungi.",
            "happy",
            run = { actions.playStoreSearch(c.query); null }
        )
        is Command.OpenUrl -> ActionPlan(
            "Ji, ${c.url.removePrefix("https://")} khol rahi hoon.",
            "happy",
            run = { actions.openUrl(c.url); null }
        )
        is Command.Alarm -> ActionPlan(
            "Theek hai, ${"%d:%02d".format(c.hour, c.minute)} ka alarm laga rahi hoon.",
            "happy",
            run = { if (actions.setAlarm(c.hour, c.minute)) null else "Alarm nahi laga paayi." }
        )
        is Command.Timer -> ActionPlan(
            "Timer laga rahi hoon, ${c.seconds / 60} minute ${c.seconds % 60} second ka.",
            "happy",
            run = { if (actions.setTimer(c.seconds)) null else "Timer nahi laga paayi." }
        )
        is Command.Media -> ActionPlan("Ji, ${c.word}.", "happy", run = { actions.mediaKey(c.key); null })
        is Command.Volume -> ActionPlan(
            if (c.up) "Volume badha rahi hoon." else "Volume kam kar rahi hoon.",
            "happy",
            run = { actions.volume(c.up); null }
        )
        Command.Home -> withA11y("Ghar chalte hain!") { it.home(); null }
        Command.Recents -> withA11y("Recent apps dikha rahi hoon.") { it.recents(); null }
        Command.Notifications -> withA11y("Notifications khol rahi hoon.") { it.notifications(); null }
        Command.Back -> afterSwitch("Theek hai, ${SWITCH_SECONDS} second mein back dabaungi. Jis app mein chahiye wahan chale jao.") {
            it.back(); null
        }
        is Command.Scroll -> afterSwitch(
            "Theek hai, ${SWITCH_SECONDS} second mein ${if (c.down) "neeche" else "upar"} scroll karungi. Jis app mein chahiye wahan chale jao."
        ) { if (it.scroll(c.down)) null else "Scroll nahi ho paaya." }
        Command.ReadScreen -> afterSwitch(
            "Theek hai, ${SWITCH_SECONDS} second mein screen padhungi. Jis app ki screen chahiye wahan chale jao."
        ) {
            val t = it.readScreen()
            if (t.isBlank()) "Screen par mujhe padhne layak kuch nahi dikha."
            else "Screen par ye likha hai: " + t.take(350)
        }
        is Command.Tap -> afterSwitch(
            "Theek hai, ${SWITCH_SECONDS} second mein ${c.target} par tap karungi. Jis app mein chahiye wahan chale jao."
        ) { if (it.clickByText(c.target)) null else "${c.target} mujhe screen par nahi dikha." }
    }

    private fun needsA11y(): ActionPlan? =
        if (NovaAccessibilityService.instance == null) {
            ActionPlan("Accessibility service chalu nahi hai. Permissions screen se NOVA ko chalu karo.", "sad")
        } else null

    /** Runs an accessibility action immediately. */
    private fun withA11y(reply: String, block: (NovaAccessibilityService) -> String?): ActionPlan =
        needsA11y() ?: ActionPlan(reply, "happy", run = {
            val s = NovaAccessibilityService.instance
            if (s == null) "Accessibility band ho gayi." else block(s)
        })

    /** Waits so the user can switch to another app, then acts on whatever app is in front. */
    private fun afterSwitch(reply: String, block: (NovaAccessibilityService) -> String?): ActionPlan =
        needsA11y() ?: ActionPlan(reply, "happy", run = {
            delay(SWITCH_SECONDS * 1000L)
            val s = NovaAccessibilityService.instance
            when {
                s == null -> "Accessibility band ho gayi."
                s.foregroundPackage() == ctx.packageName -> "Abhi bhi NOVA hi khuli hai, isliye kuch nahi kiya."
                else -> s.guard() ?: block(s)
            }
        })

    companion object {
        private const val SWITCH_SECONDS = 6

        private val DOMAIN = Regex("\\b([a-z0-9-]+\\.(?:com|in|org|net|co|io|app|dev))\\b")

        private val OPEN = setOf(
            "open", "kholo", "khol", "kholna", "chalu", "start", "launch",
            "खोलो", "खोल", "खोलना", "चालू", "ओपन"
        )
        private val SEARCH_STRICT = setOf(
            "search", "khojo", "khoj", "dhundo", "dhoondo", "dhundh", "find",
            "सर्च", "खोजो", "खोज", "ढूंढो", "ढूँढो", "ढूंढ"
        )
        private val SEARCH = SEARCH_STRICT + setOf(
            "dikhao", "dekho", "chalao", "play", "sunao", "lagao",
            "दिखाओ", "देखो", "चलाओ", "प्ले", "सुनाओ", "लगाओ"
        )
        private val FILLER = setOf(
            "nova", "नोवा", "par", "pe", "pr", "me", "mein", "में", "पर", "पे", "ko", "को", "aur", "और", "and",
            "karo", "kar", "kro", "करो", "कर", "do", "दो", "de", "दे", "please", "plz", "zara", "jara", "ज़रा", "जरा",
            "ek", "एक", "mujhe", "मुझे", "ke", "ki", "ka", "का", "की", "के", "app", "ऐप", "एप", "application",
            "ji", "जी", "for", "on", "the", "to", "a", "is", "hai", "है", "bhai", "yaar", "ab", "अब", "phir", "fir", "फिर",
            "youtube", "google", "store", "स्टोर", "playstore", "install", "download", "इंस्टॉल", "डाउनलोड",
            "tap", "click", "dabao", "दबाओ", "टैप", "क्लिक", "screen", "स्क्रीन", "scroll", "स्क्रॉल"
        )
        private val STOP = OPEN + SEARCH + FILLER

        private val ORDINALS = mapOf(
            "pehla" to 1, "pehle" to 1, "first" to 1, "1st" to 1, "पहला" to 1, "पहले" to 1,
            "doosra" to 2, "dusra" to 2, "second" to 2, "2nd" to 2, "दूसरा" to 2,
            "teesra" to 3, "third" to 3, "3rd" to 3, "तीसरा" to 3,
            "chautha" to 4, "fourth" to 4, "4th" to 4, "चौथा" to 4,
            "panchva" to 5, "fifth" to 5, "5th" to 5, "पांचवां" to 5, "पाँचवाँ" to 5
        )

        private val NORMAL = mapOf(
            "यूट्यूब" to "youtube", "यूटयूब" to "youtube", "गूगल" to "google",
            "अलार्म" to "alarm", "टाइमर" to "timer", "वॉल्यूम" to "volume", "वोल्यूम" to "volume",
            "इंस्टाग्राम" to "instagram", "व्हाट्सएप" to "whatsapp", "वाट्सएप" to "whatsapp"
        )
    }
}
