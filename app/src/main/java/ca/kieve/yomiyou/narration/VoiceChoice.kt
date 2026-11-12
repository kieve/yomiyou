package ca.kieve.yomiyou.narration

import ca.kieve.yomiyou.book.VoiceInfo

object VoiceChoice {
    fun of(voices: List<VoiceInfo>, remembered: String?): VoiceInfo? =
        voices.firstOrNull { it.id == remembered } ?: voices.firstOrNull()
}
