package ca.kieve.yomiyou.narration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VoiceTimelineTest {
    // Chapter numbers 1, 2, 3 at places 0, 1, 2; chapter 2 has no audio. Phrases 0..2 in segment 0, 3..5 in segments 1
    // and 2, with a pause after phrase 1.
    private val timeline = VoiceTimeline.of(
        mapOf(
            1 to listOf(listOf(0.0, 0.0, 0.1, 0.5), listOf(1.0, 0.0, 0.5, 0.9), listOf(2.0, 0.0, 1.4, 2.0)),
            3 to listOf(listOf(4.0, 1.0, 0.2, 1.0), listOf(5.0, 1.0, 1.0, 1.8), listOf(6.0, 2.0, 0.1, 0.6)),
        ),
        listOf(1, 2, 3),
    )

    @Test
    fun `the phrase spoken is the last one started`() {
        assertNull(timeline.phraseAt(0, 0.0))
        assertEquals(0, timeline.phraseAt(0, 0.3)?.phrase)
        assertEquals(1, timeline.phraseAt(0, 0.6)?.phrase)
        // A pause belongs to the phrase before it.
        assertEquals(1, timeline.phraseAt(0, 1.2)?.phrase)
        assertEquals(2, timeline.phraseAt(0, 5.0)?.phrase)
        assertEquals(6, timeline.phraseAt(2, 0.3)?.phrase)
        assertNull(timeline.phraseAt(9, 0.3))
    }

    @Test
    fun `a seek landing a hair before a phrase still finds it`() {
        assertEquals(5, timeline.phraseAt(1, 0.99)?.phrase)
        assertEquals(4, timeline.phraseAt(1, 0.97)?.phrase)
    }

    @Test
    fun `knows when the next phrase starts, so the light changes on time`() {
        assertEquals(0.1, timeline.nextStart(0, 0.0))
        assertEquals(0.5, timeline.nextStart(0, 0.3))
        assertEquals(1.4, timeline.nextStart(0, 1.2))
        assertNull(timeline.nextStart(0, 1.5))
        assertNull(timeline.nextStart(9, 0.0))
    }

    @Test
    fun `finds where a phrase is spoken, or the next that is`() {
        assertEquals(Timing(4, 2, 1, 0.2, 1.0), timeline.from(4))
        // Phrase 3 has no audio (a heading the narrator skipped, say): the next phrase plays.
        assertEquals(4, timeline.from(3)?.phrase)
        assertNull(timeline.from(7))
    }

    @Test
    fun `a chapter starts at its first spoken phrase, or the next chapter with audio`() {
        assertEquals(0, timeline.chapterStart(0)?.phrase)
        assertEquals(4, timeline.chapterStart(1)?.phrase)
        assertEquals(4, timeline.chapterStart(2)?.phrase)
        assertEquals(mapOf(0 to 0, 1 to 2, 2 to 2), timeline.segmentChapters)
    }
}
