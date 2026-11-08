package ca.kieve.yomiyou.reader

import java.io.Closeable

/** What the reader shows, whatever kind of book it is. */
interface ReaderBook : Closeable {
    val title: String

    /** Written top to bottom, lines right to left (tategaki). */
    val vertical: Boolean

    /** Each chapter's label, in order. */
    val chapters: List<String>

    /** A chapter's markup: one XHTML element whose links to the book's files start with [ReaderUrls.BOOK]. */
    fun chapter(index: Int): String

    /** A file the markup links to, by its path in the book. */
    fun resource(path: String): Resource?
}

class Resource(val bytes: ByteArray, val mediaType: String)

object ReaderUrls {
    const val ORIGIN = "https://appassets.androidplatform.net"
    const val PAGE = "$ORIGIN/assets/reader/reader.html"
    const val BOOK = "$ORIGIN/book/"
}
