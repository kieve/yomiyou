package ca.kieve.yomiyou.book

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookInfoTest {
    private val manifest = BookManifest(
        format = 1,
        bookId = "4b3e",
        title = "蜘蛛の糸",
        author = "芥川龍之介",
        phrases = 1781,
        voices = listOf(VoiceInfo("ginga", "銀芽", "AI声優-銀芽"), VoiceInfo("mao", "まお")),
    )

    @Test
    fun `book json keeps the book and its position`() {
        val info = BookInfo.of(manifest, manifest.voices.take(1)).copy(position = Position(chapter = 2, span = 482))

        val parsed = BookInfo.parse(info.encode())

        assertEquals(info, parsed)
        assertEquals("4b3e", parsed.bookId)
        assertEquals(listOf("ginga"), parsed.voices.map { it.id })
    }

    @Test
    fun `a book never opened has no position, and unknown keys are skipped`() {
        val info = BookInfo.of(manifest, emptyList())

        assertFalse("position" in info.encode())
        assertTrue("\"format\": 1" in info.encode())
        assertNull(BookInfo.parse(info.encode()).position)
        assertEquals("T", BookInfo.parse("""{"bookId":"b","title":"T","shelvedAt":12}""").title)
    }
}
