package ca.kieve.yomiyou.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingStyleTest {
    @Test
    fun `the style reaches the page as CSS variables`() {
        assertEquals(mapOf("--text-size" to "20px"), ReadingStyle().variables)
        assertEquals(mapOf("--text-size" to "26px"), ReadingStyle(26).variables)
    }

    @Test
    fun `text size steps by two within its bounds`() {
        assertEquals(22, ReadingStyle().larger().textSize)
        assertEquals(18, ReadingStyle().smaller().textSize)
        assertEquals(48, ReadingStyle(48).larger().textSize)
        assertEquals(14, ReadingStyle(14).smaller().textSize)
        assertEquals(20, ReadingStyle.of(null).textSize)
        assertEquals(48, ReadingStyle.of(100).textSize)
    }
}
