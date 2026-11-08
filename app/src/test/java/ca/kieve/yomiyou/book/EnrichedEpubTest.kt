package ca.kieve.yomiyou.book

import ca.kieve.yomiyou.reader.ReaderUrls
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class EnrichedEpubTest {
    /** 蜘蛛の糸 (public domain), enriched by yomiage's BuilderTest: a phrase per token. */
    private fun kumoNoIto(): EnrichedEpub {
        val bytes = javaClass.getResourceAsStream("/epub/kumo-no-ito.yomiage.epub")!!.use { it.readBytes() }
        return EnrichedEpub.open(ZipIndex.open(BytesAccess(bytes)))
    }

    private fun spans(chapter: String): List<Int> =
        Jsoup.parse(chapter, "", Parser.xmlParser()).select("[data-y]").map { it.attr("data-y").toInt() }

    @Test
    fun `reads the package and yomiage json`() {
        val book = kumoNoIto()

        assertEquals("蜘蛛の糸", book.title)
        assertTrue(book.vertical)
        assertEquals(listOf("蜘蛛の糸", "一", "二", "三"), book.chapters.take(4))
        assertEquals(5, book.chapters.size)
        assertEquals(1781, book.manifest.phrases)
    }

    @Test
    fun `the chapters hold every phrase once, in order`() {
        val book = kumoNoIto()

        // A phrase crossing an inline element is several spans with one number: count it once.
        val phrases = book.chapters.indices.flatMap { spans(book.chapter(it)) }.fold(mutableListOf<Int>()) { list, span ->
            if (list.lastOrNull() != span) list.add(span)
            list
        }

        assertEquals((0 until 1781).toList(), phrases)
        assertEquals(listOf(5, 482, 1432), listOf(1, 2, 3).map { spans(book.chapter(it)).first() })
    }

    @Test
    fun `a chapter is one well-formed XHTML element, with nothing that restyles it`() {
        val chapter = kumoNoIto().chapter(1)

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(chapter.byteInputStream()).documentElement
        assertEquals("http://www.w3.org/1999/xhtml", root.namespaceURI)
        assertEquals("div", root.localName)
        assertTrue("<ruby>" in chapter)
        assertFalse("<link" in chapter || "<style" in chapter || "style=" in chapter)
    }

    private fun epub(vararg files: Pair<String, String>, images: Map<String, ByteArray> = emptyMap()): EnrichedEpub {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for ((name, text) in files) {
                out.putNextEntry(ZipEntry(name))
                out.write(text.encodeToByteArray())
            }
            for ((name, data) in images) {
                out.putNextEntry(ZipEntry(name))
                out.write(data)
            }
        }
        return EnrichedEpub.open(ZipIndex.open(BytesAccess(bytes.toByteArray())))
    }

    private val container = "META-INF/container.xml" to """
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>"""

    private val opf = "OEBPS/content.opf" to """
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <manifest>
            <item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/>
            <item id="text" href="text/text.xhtml" media-type="application/xhtml+xml"/>
            <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
          </manifest>
          <spine><itemref idref="cover-page"/><itemref idref="text"/></spine>
        </package>"""

    private val yomiage = "OEBPS/yomiage.json" to """
        {"format":1,"bookId":"b1","title":"T","author":"A","phrases":3,"voices":[],
         "chapters":[{"number":1,"title":"一","href":"OEBPS/text/text.xhtml","firstSpan":0},
                     {"number":2,"title":"二","href":"OEBPS/text/text.xhtml","firstSpan":2}]}"""

    private val cover = "OEBPS/cover.xhtml" to """
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>c</title></head><body>
        <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"><image xlink:href="images/cover.jpg"/></svg>
        </body></html>"""

    private val text = "OEBPS/text/text.xhtml" to """
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        <head><title>t</title><link rel="stylesheet" href="../style.css"/></head><body><div class="main">
        <p><span data-y="0">一</span></p>
        <p style="color: red" onclick="go()"><span data-y="1">あ</span><img src="../images/pic%201.png" alt=""/><a id="here"/></p>
        <p epub:type="z"><span data-y="2">二</span></p><script>alert(1)</script>
        </div></body></html>"""

    private val images = mapOf("OEBPS/images/pic 1.png" to byteArrayOf(1, 2, 3), "OEBPS/images/cover.jpg" to byteArrayOf(4))

    @Test
    fun `a chapter is cut at the next chapter's block, keeping the elements around it`() {
        val book = epub(container, opf, yomiage, cover, text, images = images)

        val first = Jsoup.parse(book.chapter(0), "", Parser.xmlParser())
        val second = Jsoup.parse(book.chapter(1), "", Parser.xmlParser())

        assertEquals(listOf(0, 1), first.select("div.main [data-y]").map { it.attr("data-y").toInt() })
        assertEquals(listOf(2), second.select("div.main [data-y]").map { it.attr("data-y").toInt() })
        assertEquals("z", second.selectFirst("p")!!.attr("epub:type"))
    }

    @Test
    fun `images point at the book's files, and scripts and styles are gone`() {
        val book = epub(container, opf, yomiage, cover, text, images = images)

        val chapter = book.chapter(0)
        val page = Jsoup.parse(chapter, "", Parser.xmlParser())

        assertEquals(ReaderUrls.BOOK + "OEBPS/images/cover.jpg", page.selectFirst("image")!!.attr("xlink:href"))
        assertEquals(ReaderUrls.BOOK + "OEBPS/images/pic%201.png", page.selectFirst("img")!!.attr("src"))
        assertTrue(page.select("script, link").isEmpty())
        assertFalse("style=" in chapter || "onclick" in chapter)
        assertTrue(Jsoup.parse(book.chapter(1), "", Parser.xmlParser()).select("image").isEmpty())

        val image = book.resource("OEBPS/images/pic 1.png")!!
        assertArrayEquals(byteArrayOf(1, 2, 3), image.bytes)
        assertEquals("image/png", image.mediaType)
        assertNull(book.resource("OEBPS/images/missing.png"))
        assertArrayEquals(byteArrayOf(4), book.cover()!!.bytes)
        assertFalse(book.vertical)
    }

    @Test
    fun `refuses books it can't read, with a message`() {
        assertEquals("Not an EPUB.", assertThrows(BookError::class.java) { epub("a.txt" to "a") }.message)
        assertEquals(
            "No phrase data in this EPUB. Build it with yomiage first.",
            assertThrows(BookError::class.java) { epub(container, opf, text) }.message
        )
        val newer = "OEBPS/yomiage.json" to """{"format":2,"bookId":"b","title":"T"}"""
        assertEquals(
            "This EPUB was built by a different version of yomiage.",
            assertThrows(BookError::class.java) { epub(container, opf, newer) }.message
        )
    }
}
