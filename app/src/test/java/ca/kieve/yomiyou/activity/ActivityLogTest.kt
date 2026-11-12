package ca.kieve.yomiyou.activity

import ca.kieve.yomiyou.browse.Importer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ActivityLogTest {
    @TempDir
    lateinit var dir: File

    private var now = 1000L
    private fun log() = ActivityLog(File(dir, "activity.json")) { now++ }

    @Test
    fun `entries are kept across restarts, newest first`() {
        val log = log()
        val first = log.start("Import")
        log.retitle(first, "夢")
        log.progress(first, "Copying 夢", 40)
        log.finish(first, listOf("Added 夢."), emptyList())
        val second = log.start("Import")
        log.finish(second, emptyList(), listOf("photo.jpg: Not an EPUB or a voice file."))

        val reopened = log().entries.value

        assertEquals(listOf("Import", "夢"), reopened.map { it.title })
        assertEquals(listOf("Added 夢."), reopened[1].added)
        assertEquals(ActivityEntry.State.Done, reopened[1].state)
        assertNull(reopened[1].percent)
        assertEquals(listOf("photo.jpg: Not an EPUB or a voice file."), reopened[0].problems)
    }

    @Test
    fun `work running when the app closed comes back interrupted`() {
        log().start("Import")

        val reopened = log().entries.value.single()

        assertEquals(ActivityEntry.State.Interrupted, reopened.state)
    }

    @Test
    fun `only the latest entries are kept, and clearing leaves running work`() {
        val log = log()
        val running = log.start("Running")
        repeat(ActivityLog.MAX + 5) { log.finish(log.start("Import $it"), emptyList(), emptyList()) }

        assertEquals(ActivityLog.MAX, log.entries.value.size)
        assertEquals("Import ${ActivityLog.MAX + 4}", log.entries.value.first().title)
        assertEquals(running, log.entries.value.last().id)

        log.clear()

        assertEquals(listOf("Running"), log.entries.value.map { it.title })
    }

    @Test
    fun `the toast says the one line, or counts and points to Activity`() {
        assertEquals("Added novel: 夢", Importer.summary(listOf("夢"), listOf("Added novel"), emptyList()))
        assertEquals("夢 is already on the shelf.", Importer.summary(emptyList(), emptyList(), listOf("夢 is already on the shelf.")))
        assertEquals("Added 1, 2 problems. See Activity.", Importer.summary(listOf("夢"), listOf("Added novel"), listOf("a", "b")))
        assertEquals("Nothing added, 2 problems. See Activity.", Importer.summary(emptyList(), emptyList(), listOf("a", "b")))
    }
}
