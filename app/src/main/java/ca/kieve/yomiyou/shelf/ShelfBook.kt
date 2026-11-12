package ca.kieve.yomiyou.shelf

import android.net.Uri
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.LibraryBook
import ca.kieve.yomiyou.book.Position
import kotlin.math.roundToInt

/** A book on the shelf, of any kind. */
interface ShelfBook {
    val id: String
    val title: String
    val cover: Uri?
    val narrated: Boolean

    /** How far through it the reader is, 0 to 100, or null when it can't say. */
    val progress: Int?
    val lastOpened: Long?

    suspend fun delete()
}

object Shelf {
    /** The latest opened or added first; books never opened by title, after them. */
    fun order(books: List<ShelfBook>): List<ShelfBook> =
        books.sortedWith(compareByDescending<ShelfBook> { it.lastOpened ?: Long.MIN_VALUE }.thenBy { it.title })

    fun progress(position: Position?, phrases: Int?): Int? {
        if (position == null || phrases == null || phrases <= 0) return null
        return ((position.span + 1) * 100.0 / phrases).roundToInt().coerceIn(0, 100)
    }
}

class YomiageShelfBook(private val book: LibraryBook, private val library: Library) : ShelfBook {
    override val id: String get() = book.info.bookId
    override val title: String get() = book.info.title
    override val cover: Uri? get() = book.cover
    override val narrated: Boolean get() = book.info.voices.isNotEmpty()
    override val progress: Int? get() = Shelf.progress(book.info.position, book.info.phrases)
    override val lastOpened: Long? get() = book.info.lastOpened

    override suspend fun delete() = library.delete(book.folder)
}
