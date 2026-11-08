package ca.kieve.yomiyou.book

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpubPathsTest {
    @Test
    fun `resolves links against their document`() {
        assertEquals("OEBPS/images/a b.png", EpubPaths.resolve("OEBPS/text/ch1.xhtml", "../images/a%20b.png#top"))
        assertEquals("OEBPS/text/ch2.xhtml", EpubPaths.resolve("OEBPS/text/ch1.xhtml", "./ch2.xhtml"))
        assertEquals("images/画.png", EpubPaths.resolve("OEBPS/text/ch1.xhtml", "/images/%E7%94%BB.png"))
        assertEquals("OEBPS/content.opf", EpubPaths.resolve("META-INF/container.xml", "../OEBPS/content.opf"))
    }

    @Test
    fun `tells links that leave the book`() {
        assertTrue(EpubPaths.isExternal("https://www.aozora.gr.jp/"))
        assertTrue(EpubPaths.isExternal("mailto:someone@example.com"))
        assertFalse(EpubPaths.isExternal("../images/a.png"))
    }

    @Test
    fun `a document's first chapter takes the chapterless documents before it`() {
        val spine = listOf("cover.xhtml", "title.xhtml", "insert.xhtml", "text.xhtml")
        val chapters = listOf("title.xhtml", "text.xhtml", "text.xhtml")

        assertEquals(listOf("cover.xhtml"), EpubPaths.leadingDocuments(spine, chapters, 0))
        assertEquals(listOf("insert.xhtml"), EpubPaths.leadingDocuments(spine, chapters, 1))
        assertEquals(emptyList<String>(), EpubPaths.leadingDocuments(spine, chapters, 2))
    }

    @Test
    fun `encodes paths for a URL`() {
        assertEquals("OEBPS/%E7%94%BB%E5%83%8F%201.png", EpubPaths.percentEncode("OEBPS/画像 1.png"))
        assertEquals("image/png", EpubPaths.mediaType("OEBPS/a.PNG"))
        assertEquals("application/octet-stream", EpubPaths.mediaType("OEBPS/a"))
    }
}
