package ca.kieve.yomiyou.book

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipIndexTest {
    private fun zip(vararg entries: Pair<String, ByteArray>, stored: Set<String> = emptySet()): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                if (name in stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                }
                out.putNextEntry(entry)
                out.write(data)
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun `reads stored and deflated entries by name`() {
        val text = "あいうえお".repeat(200).encodeToByteArray()
        val audio = ByteArray(5000) { it.toByte() }
        val index = ZipIndex.open(
            BytesAccess(zip("OEBPS/本文.xhtml" to text, "audio/00001.opus" to audio, stored = setOf("audio/00001.opus")))
        )

        assertEquals(setOf("OEBPS/本文.xhtml", "audio/00001.opus"), index.names)
        assertArrayEquals(text, index.read("OEBPS/本文.xhtml"))
        assertArrayEquals(audio, index.read("audio/00001.opus"))
        assertTrue(index.has("audio/00001.opus"))
        assertFalse(index.has("audio/00002.opus"))
        assertThrows(IOException::class.java) { index.read("audio/00002.opus") }
    }

    @Test
    fun `reads a file through its channel`(@TempDir dir: Path) {
        val file = dir.resolve("book.epub")
        Files.write(file, zip("mimetype" to "application/epub+zip".encodeToByteArray(), stored = setOf("mimetype")))

        ZipIndex.open(ChannelAccess(FileChannel.open(file))).use { index ->
            assertEquals("application/epub+zip", index.text("mimetype"))
        }
    }

    @Test
    fun `finds a zip64 table of contents`() {
        // Past 65,535 entries the end record can't hold the count, and the zip64 one takes over.
        val names = (0..65_536).map { "t/$it" }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for (name in names) {
                out.putNextEntry(ZipEntry(name))
                out.write(name.encodeToByteArray())
                out.closeEntry()
            }
        }

        val index = ZipIndex.open(BytesAccess(bytes.toByteArray()))

        assertEquals(names.size, index.names.size)
        assertEquals("t/65536", index.text("t/65536"))
    }

    @Test
    fun `refuses what isn't a zip`() {
        assertThrows(IOException::class.java) { ZipIndex.open(BytesAccess("not a zip at all".encodeToByteArray())) }
    }
}
