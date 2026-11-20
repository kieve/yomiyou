package ca.kieve.yomiyou.shelf

import android.net.Uri
import ca.kieve.yomiyou.book.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShelfTest {
    private class Book(override val title: String, override val lastOpened: Long?) : ShelfBook {
        override val id = title
        override val cover: Uri? = null
        override val narrated = false
        override val progress: Int? = null

        override suspend fun delete() {}
    }

    @Test
    fun `the latest opened or added comes first, then the rest by title`() {
        val books = listOf(Book("い", null), Book("A", 100), Book("B", 300), Book("あ", null), Book("C", 200))

        assertEquals(listOf("B", "C", "A", "あ", "い"), Shelf.order(books).map { it.title })
    }

    @Test
    fun `progress is the paragraph read out of the book's paragraphs`() {
        // A phrase's number is its paragraph times 1000 plus its place in it.
        assertEquals(50, Shelf.progress(Position(chapter = 3, span = 499_003), paragraphs = 1000))
        assertEquals(0, Shelf.progress(Position(chapter = 0, span = 0), paragraphs = 1000))
        assertEquals(100, Shelf.progress(Position(chapter = 9, span = 999_012), paragraphs = 1000))
        assertEquals(100, Shelf.progress(Position(chapter = 9, span = 5_000_000), paragraphs = 1000))
        assertNull(Shelf.progress(null, paragraphs = 1000))
        assertNull(Shelf.progress(Position(chapter = 3, span = 499), paragraphs = null))
    }
}
