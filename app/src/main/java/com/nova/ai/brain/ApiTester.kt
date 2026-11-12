package com.nova.ai.brain

/** Sends one tiny message to check that an API key and model really work. */
object ApiTester {

    /** Returns a short Hinglish result line. Never throws. */
    suspend fun test(provider: AiProvider): Pair<Boolean, String> = try {
        provider.stream("Reply with only the word OK.", listOf(ChatTurn(true, "hi")), 40) { }
        Pair(true, "${provider.label} chal raha hai ✓")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: BrainException) {
        // The service answered but with no text: the key itself is fine.
        if (e.message.orEmpty().contains("khali jawab")) {
            Pair(true, "${provider.label} ki key sahi lag rahi hai ✓")
        } else {
            Pair(false, "${provider.label}: ${e.message}".take(180))
        }
    } catch (e: Exception) {
        Pair(false, "${provider.label}: ${e.message}".take(180))
    }
}
