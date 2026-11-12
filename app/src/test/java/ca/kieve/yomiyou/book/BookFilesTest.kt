package ca.kieve.yomiyou.book

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookFilesTest {
    private fun identify(name: String, bytes: ByteArray) = BookFiles.identify(name, BytesAccess(bytes))

    private val epub = identify("夢.yomiage.epub", TestFiles.epub("b1", "夢"))
    private val ginga = identify("夢.ginga.voice.zip", TestFiles.voice("b1", "ginga", "銀芽 (通常)"))
    private val mao = identify("夢.mao.voice.zip", TestFiles.voice("b1", "mao", "まお (ノーマル)"))

    private fun shelved(vararg voices: String) = mapOf(
        "b1" to BookInfo(bookId = "b1", title = "夢", voices = voices.map { VoiceInfo(it, it) })
    )

    @Test
    fun `tells an enriched EPUB from a voice file by what's inside`() {
        assertEquals("b1", (epub as BookFiles.Book).manifest.bookId)
        assertEquals("夢", epub.manifest.title)
        assertEquals("mao", (mao as BookFiles.Voice).manifest.voice)
        assertEquals("b1", mao.manifest.bookId)

        val plain = identify("plain.epub", TestFiles.epub("b2", "Plain", phraseData = false))
        assertEquals("No phrase data in this EPUB. Build it with yomiage first.", (plain as BookFiles.Other).reason)
        val photo = identify("photo.jpg", byteArrayOf(1, 2, 3, 4))
        assertEquals("Not an EPUB or a voice file.", (photo as BookFiles.Other).reason)
    }

    @Test
    fun `reads the cover an enriched EPUB names`() {
        val cover = ZipIndex.open(BytesAccess(TestFiles.epub("b1", "夢"))).use { EnrichedEpub.open(it).cover() }!!

        assertArrayEquals(TestFiles.cover, cover.bytes)
        assertEquals("image/png", cover.mediaType)
        assertEquals("夢.cover.png", BookFiles.cover("夢", cover.mediaType))
        assertEquals("夢.cover.jpg", BookFiles.cover("夢", "image/jpeg"))
    }

    @Test
    fun `an EPUB picked with its voice files is a new book with them`() {
        val plan = BookFiles.plan(listOf(mao, epub, ginga), emptyMap())

        assertEquals(1, plan.books.size)
        assertEquals("b1", plan.books[0].epub.manifest.bookId)
        assertEquals(listOf("mao", "ginga"), plan.books[0].voices.map { it.manifest.voice })
        assertEquals(emptyList<String>(), plan.problems)
    }

    @Test
    fun `voice files alone join their book on the shelf, never twice`() {
        val plan = BookFiles.plan(listOf(ginga, mao), shelved("ginga"))

        assertEquals(emptyList<BookFiles.NewBook>(), plan.books)
        assertEquals(listOf("mao"), plan.moreVoices.single().voices.map { it.manifest.voice })
        assertEquals(listOf("夢 already has 銀芽 (通常)."), plan.problems)
    }

    @Test
    fun `says what it can't add`() {
        val other = identify("他.mao.voice.zip", TestFiles.voice("b9", "mao", "まお"))
        val photo = identify("photo.jpg", byteArrayOf(1))

        val plan = BookFiles.plan(listOf(epub, other, photo), shelved())

        assertEquals(emptyList<BookFiles.NewBook>(), plan.books)
        assertEquals(
            listOf(
                "photo.jpg: Not an EPUB or a voice file.",
                "夢 is already on the shelf.",
                "他.mao.voice.zip is for a book that isn't on the shelf. Pick its EPUB with it.",
            ),
            plan.problems
        )
    }

    @Test
    fun `a folder copied in by hand holds a book when it has one enriched EPUB not on the shelf`() {
        val notes = BookFiles.Other("notes.zip", "Not an EPUB or a voice file.")
        val book = BookFiles.recognize(listOf(ginga, epub, notes, mao), emptyMap())!!

        // yomiage's own names say what each file is: they stay.
        assertEquals(emptyList<Pair<String, String>>(), BookFiles.renames(book))
        assertNull(BookFiles.recognize(listOf(epub, ginga), shelved()))
        assertNull(BookFiles.recognize(listOf(ginga, mao), emptyMap()))
        val second = identify("二.yomiage.epub", TestFiles.epub("b2", "二"))
        assertNull(BookFiles.recognize(listOf(epub, second), emptyMap()))

        val unnamed = BookFiles.recognize(
            listOf(identify("download.epub", TestFiles.epub("b1", "夢")), identify("v.zip", TestFiles.voice("b1", "mao", "まお"))),
            emptyMap()
        )!!
        assertEquals(
            listOf("download.epub" to "夢.yomiage.epub", "v.zip" to "夢.mao.voice.zip"),
            BookFiles.renames(unnamed)
        )
    }

    @Test
    fun `a book's files are named after its title and found by their endings`() {
        assertEquals("夢.yomiage.epub", BookFiles.epub("夢"))
        assertEquals("夢.ginga.voice.zip", BookFiles.voice("夢", "ginga"))
        assertEquals("夢.cover.png", BookFiles.cover("夢", "image/png"))
        assertEquals("A_B.yomiage.epub", BookFiles.epub("A/B"))

        assertTrue(BookFiles.isEpub("また、同じ夢を見ていた - 住野よる.yomiage.epub"))
        assertEquals("ginga", BookFiles.voiceOf("また、同じ夢を見ていた - 住野よる.ginga.voice.zip"))
        assertNull(BookFiles.voiceOf("notes.zip"))
        assertTrue(BookFiles.isCover("夢.cover.jpg"))

        val long = "長".repeat(100)
        val name = BookFiles.voice(long, "ginga")
        assertTrue(name.encodeToByteArray().size <= 255)
        assertEquals("ginga", BookFiles.voiceOf(name))
    }

    @Test
    fun `files named the first builds' way get the names that say what they are`() {
        assertEquals(
            listOf(
                "book.epub" to "夢.yomiage.epub",
                "voice-mao.zip" to "夢.mao.voice.zip",
                "cover.jpg" to "夢.cover.jpg",
            ),
            BookFiles.oldNames("夢", listOf("book.json", "book.epub", "voice-mao.zip", "cover.jpg", "夢.ginga.voice.zip"))
        )
    }

    @Test
    fun `voices go in the book's order, ones it doesn't list last`() {
        val manifest = (epub as BookFiles.Book).manifest
        val extra = VoiceInfo("voicevox", "VOICEVOX")

        val ordered = BookFiles.inOrder(manifest, listOf(extra, VoiceInfo("mao", "まお"), VoiceInfo("ginga", "銀芽")))

        assertEquals(listOf("ginga", "mao", "voicevox"), ordered.map { it.id })
        assertEquals("銀芽 (通常)", ordered[0].label)
    }
}
