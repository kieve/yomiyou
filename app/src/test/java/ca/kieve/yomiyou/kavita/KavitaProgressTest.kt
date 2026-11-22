package ca.kieve.yomiyou.kavita

import ca.kieve.yomiyou.book.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class KavitaProgressTest {
    @Test
    fun `Kavita's time is UTC without a zone, and a chapter never read has none`() {
        val read = KavitaProgress(2951, 11457, 4, 2924, 2, "//body/p[11]", "2026-11-22T05:35:12.7959018")
        assertEquals(Instant.parse("2026-11-22T05:35:12.795Z").toEpochMilli(), read.savedAt)

        val never = KavitaProgress(0, 9632, 0, 0, 0, null, "0001-01-01T00:00:00")
        assertNull(never.savedAt)
    }

    @Test
    fun `a place keeps its time until the reader moves`() {
        val before = Position(3, 150000, at = 100)
        assertEquals(100L, Position(3, 150000).since(before, now = 500).at)
        assertEquals(500L, Position(3, 150001).since(before, now = 500).at)
        assertEquals(500L, Position(3, -1, "//body/p[2]").since(null, now = 500).at)
    }
}
