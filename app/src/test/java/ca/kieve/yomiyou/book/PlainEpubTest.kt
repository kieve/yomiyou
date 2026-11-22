package ca.kieve.yomiyou.book

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class PlainEpubTest {
    private val container = """<?xml version="1.0"?><container version="1.0"
        xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""

    private fun xhtml(body: String) =
        """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head>
        <body>$body</body></html>"""

    private val one = xhtml("<h1>One</h1><p>a</p><div class=\"box\"><p>b</p><p>c</p></div><div>d</div>")
    private val two = xhtml("<p id=\"start\">e</p>")
    private val three = xhtml("<p>f</p>")

    /** Three documents: the first two in the table of contents (EPUB 3's nav, or with [ncx] EPUB 2's NCX). */
    private fun epub(ncx: Boolean = false): ByteArray {
        val navItem = if (ncx) {
            """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
        } else {
            """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
        }
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Coiling Dragon</dc:title>
            <dc:creator>I Eat Tomatoes</dc:creator></metadata><manifest>$navItem
            <item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/>
            <item id="two" href="text/two.xhtml" media-type="application/xhtml+xml"/>
            <item id="three" href="text/three.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine${if (ncx) " toc=\"ncx\"" else ""}><itemref idref="one"/><itemref idref="two"/><itemref idref="three"/>
            </spine></package>"""
        val nav = xhtml(
            """<nav xmlns:epub="http://www.idpf.org/2007/ops" epub:type="toc"><ol>
            <li><a href="text/one.xhtml">Book One</a></li><li><a href="text/two.xhtml#start">Book Two</a></li>
            </ol></nav>"""
        )
        val toc = """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
            <navPoint id="a"><navLabel><text>Book One</text></navLabel><content src="text/one.xhtml"/></navPoint>
            <navPoint id="b"><navLabel><text>Book Two</text></navLabel><content src="text/two.xhtml#start"/></navPoint>
            </navMap></ncx>"""
        return TestFiles.zip(
            "mimetype" to "application/epub+zip".encodeToByteArray(),
            "META-INF/container.xml" to container.encodeToByteArray(),
            "OEBPS/content.opf" to opf.encodeToByteArray(),
            (if (ncx) "OEBPS/toc.ncx" to toc else "OEBPS/nav.xhtml" to nav).let { it.first to it.second.encodeToByteArray() },
            "OEBPS/text/one.xhtml" to one.encodeToByteArray(),
            "OEBPS/text/two.xhtml" to two.encodeToByteArray(),
            "OEBPS/text/three.xhtml" to three.encodeToByteArray(),
        )
    }

    private fun open(bytes: ByteArray) = PlainEpub.open(ZipIndex.open(BytesAccess(bytes)))

    @Test
    fun `each spine document is a chapter, labelled from the table of contents`() {
        for (ncx in listOf(false, true)) {
            open(epub(ncx)).use { book ->
                assertEquals("Coiling Dragon", book.title)
                assertEquals("I Eat Tomatoes", book.author)
                assertTrue(book.plain)
                assertFalse(book.vertical)
                assertEquals(listOf("Book One", "Book Two", "3"), book.chapters, "ncx: $ncx")
            }
        }
    }

    @Test
    fun `a block with no block inside it carries its path from the body`() {
        val html = open(epub()).use { it.chapter(0) }
        val chapter = Jsoup.parse(html, "", Parser.xmlParser())
        val places = chapter.select("[data-x]").map { it.text() to it.attr("data-x") }

        assertEquals(
            listOf(
                "One" to "//body/h1[1]",
                "a" to "//body/p[1]",
                "b" to "//body/div[1]/p[1]",
                "c" to "//body/div[1]/p[2]",
                "d" to "//body/div[2]",
            ),
            places
        )
        assertFalse(chapter.selectFirst("div.box")!!.hasAttr("data-x"), "a container isn't a place; its blocks are")
    }

    @Test
    fun `a path counts the element among siblings of its own name`() {
        val body = Jsoup.parse("<body><p>1</p><div><p>2</p></div><p>3</p></body>", "", Parser.xmlParser())
            .selectFirst("body")!!
        val third = body.children()[2]

        assertEquals("//body/p[2]", PlainEpub.pathOf(third, body))
        assertEquals("//body/div[1]/p[1]", PlainEpub.pathOf(body.selectFirst("div > p")!!, body))
    }

    @Test
    fun `a plain EPUB is a book of its own, its ID the file's hash`() {
        val bytes = epub()
        val found = BookFiles.identify("Coiling Dragon.epub", BytesAccess(bytes)) as BookFiles.Plain
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        assertEquals(sha.take(32), found.bookId)
        assertEquals("Coiling Dragon", found.title)
        val plan = BookFiles.plan(listOf(found), emptyMap())
        assertEquals(listOf(found.bookId), plan.plain.map { it.bookId })
        assertEquals(emptyList<String>(), plan.problems)

        val again = BookFiles.plan(listOf(found), mapOf(found.bookId to BookInfo(bookId = found.bookId, title = "x")))
        assertEquals(emptyList<BookFiles.Plain>(), again.plain)
        assertEquals(listOf("Coiling Dragon is already on the shelf."), again.problems)
    }

    @Test
    fun `its original file is named apart from a narrated one`() {
        assertEquals("夢.epub", BookFiles.original("夢"))
        assertTrue(BookFiles.isOriginal("夢.epub"))
        assertFalse(BookFiles.isOriginal("夢.yomiage.epub"))
        assertFalse(BookFiles.isEpub("夢.epub"))
    }
}
