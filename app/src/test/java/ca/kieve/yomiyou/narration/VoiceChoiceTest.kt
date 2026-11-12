package ca.kieve.yomiyou.narration

import ca.kieve.yomiyou.book.VoiceInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VoiceChoiceTest {
    private val ginga = VoiceInfo("ginga", "銀芽 (通常)", "AI声優-銀芽")
    private val mao = VoiceInfo("mao", "まお (ノーマル)", "AivisSpeech: まお")

    @Test
    fun `the voice last chosen, when the book has it`() {
        assertEquals(mao, VoiceChoice.of(listOf(ginga, mao), "mao"))
    }

    @Test
    fun `else the book's first`() {
        assertEquals(ginga, VoiceChoice.of(listOf(ginga, mao), null))
        assertEquals(ginga, VoiceChoice.of(listOf(ginga, mao), "voicevox"))
        assertEquals(mao, VoiceChoice.of(listOf(mao, ginga), null))
        assertNull(VoiceChoice.of(emptyList(), "mao"))
    }
}
