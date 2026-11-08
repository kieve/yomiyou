package ca.kieve.yomiyou.book

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FolderNamesTest {
    @Test
    fun `a folder is named after its book`() {
        assertEquals("また、同じ夢を見ていた", FolderNames.of("また、同じ夢を見ていた", emptySet()))
        assertEquals("Re_Zero _ 1_", FolderNames.of("Re:Zero / 1?", emptySet()))
        assertEquals("Title", FolderNames.of(" Title. ", emptySet()))
        assertEquals("Book", FolderNames.of("..", emptySet()))
    }

    @Test
    fun `a second book of the same title gets a number`() {
        assertEquals("蜘蛛の糸 (2)", FolderNames.of("蜘蛛の糸", setOf("蜘蛛の糸")))
        assertEquals("蜘蛛の糸 (3)", FolderNames.of("蜘蛛の糸", setOf("蜘蛛の糸", "蜘蛛の糸 (2)")))
        assertEquals("ABC (2)", FolderNames.of("ABC", setOf("abc")))
    }

    @Test
    fun `a long title is cut to fit, whole characters only`() {
        val title = "あ".repeat(100)

        val name = FolderNames.of(title, emptySet())
        val numbered = FolderNames.of(title, setOf(name))

        assertEquals("あ".repeat(85), name)
        assertTrue(numbered.endsWith(" (2)"))
        assertTrue(numbered.encodeToByteArray().size <= 255)
        assertTrue(numbered.removeSuffix(" (2)").all { it == 'あ' })
    }
}
