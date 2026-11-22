package ca.kieve.yomiyou.kavita

import ca.kieve.yomiyou.book.SpinePlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KavitaOutboxTest {
    @TempDir
    lateinit var folder: File

    private val link = KavitaLink(2, 10, 100, 1000, "夢/夢.epub")

    private fun at(path: String, at: Long) = QueuedPlace(link, SpinePlace(4, path), at)

    @Test
    fun `a book's newer place replaces its queued one, and waits out a restart`() {
        val outbox = KavitaOutbox(File(folder, "outbox.json"))
        assertTrue(outbox.queue("a", at("//body/p[1]", 1)))
        assertTrue(outbox.queue("a", at("//body/p[2]", 2)))
        assertFalse(outbox.queue("a", at("//body/p[2]", 3)), "already queued")

        val again = KavitaOutbox(File(folder, "outbox.json"))
        assertEquals(mapOf("a" to at("//body/p[2]", 2)), again.queued())
    }

    @Test
    fun `a place Kavita has isn't sent again`() {
        val outbox = KavitaOutbox(File(folder, "outbox.json"))
        val first = at("//body/p[1]", 1)
        outbox.queue("a", first)
        outbox.sent("a", first)
        assertEquals(emptyMap<String, QueuedPlace>(), outbox.queued())
        assertFalse(outbox.queue("a", at("//body/p[1]", 5)))

        outbox.queue("a", at("//body/p[2]", 6))
        assertFalse(outbox.queue("a", at("//body/p[1]", 7)), "back where Kavita is: nothing to send")
        assertNull(outbox.queued()["a"], "and the move away is dropped")
    }

    @Test
    fun `a place queued while one was sending stays queued`() {
        val outbox = KavitaOutbox(File(folder, "outbox.json"))
        val sending = at("//body/p[1]", 1)
        outbox.queue("a", sending)
        outbox.queue("a", at("//body/p[2]", 2))
        outbox.sent("a", sending)
        assertEquals(at("//body/p[2]", 2), outbox.queued()["a"])
    }

    @Test
    fun `Kavita's newer place drops what was queued here`() {
        val outbox = KavitaOutbox(File(folder, "outbox.json"))
        outbox.queue("a", at("//body/p[1]", 1))
        outbox.took("a", SpinePlace(4, "//body/p[9]"))
        assertEquals(emptyMap<String, QueuedPlace>(), outbox.queued())
        assertFalse(outbox.queue("a", QueuedPlace(link, SpinePlace(4, "//body/p[9]"), 2)))
    }
}
