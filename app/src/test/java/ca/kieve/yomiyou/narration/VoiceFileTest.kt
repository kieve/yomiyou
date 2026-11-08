package ca.kieve.yomiyou.narration

import ca.kieve.yomiyou.book.BookError
import ca.kieve.yomiyou.book.BytesAccess
import ca.kieve.yomiyou.book.ZipIndex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoiceFileTest {
    private val audio = listOf(ByteArray(3000) { (it % 251).toByte() }, ByteArray(1200) { (it % 13).toByte() })

    private val voiceJson = """
        {"format":1,"bookId":"b1","voice":"ginga","label":"銀芽 (通常)","credit":"AI声優-銀芽","kbps":64,
         "segments":[{"file":"audio/00000.opus","seconds":3.4},{"file":"audio/00001.opus","seconds":1.2}],
         "chapters":[{"number":1,"timings":"timings/0001.json"},{"number":2,"timings":"timings/0002.json"}]}"""

    /** Made as yomiage makes them: every entry stored, uncompressed. */
    private fun voiceFile(json: String = voiceJson): ZipIndex {
        val entries = listOf(
            "voice.json" to json.encodeToByteArray(),
            "timings/0001.json" to "[[0,0,0.1,1.5],[1,0,1.6,3.3]]".encodeToByteArray(),
            "audio/00000.opus" to audio[0],
            "timings/0002.json" to "[[2,1,0.05,1.1]]".encodeToByteArray(),
            "audio/00001.opus" to audio[1],
        )
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for ((name, data) in entries) {
                out.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    crc = CRC32().apply { update(data) }.value
                })
                out.write(data)
                out.closeEntry()
            }
        }
        return ZipIndex.open(BytesAccess(bytes.toByteArray()))
    }

    @Test
    fun `reads the manifest and every chapter's timings`() {
        val voice = VoiceFile.open(voiceFile(), listOf(1, 2))

        assertEquals("ginga", voice.manifest.voice)
        assertEquals("b1", voice.manifest.bookId)
        assertEquals(1, voice.timeline.phraseAt(0, 2.0)?.phrase)
        assertEquals(Timing(2, 1, 1, 0.05, 1.1), voice.timeline.from(2))
    }

    @Test
    fun `a segment's audio is read from where it lies in the file`() {
        val voice = VoiceFile.open(voiceFile(), listOf(1, 2))

        for (segment in audio.indices) {
            val range = voice.audioRange(segment)
            val bytes = ByteArray((range.last - range.first + 1).toInt())
            voice.readFully(range.first, ByteBuffer.wrap(bytes))
            assertArrayEquals(audio[segment], bytes)
        }
    }

    @Test
    fun `refuses what it can't play, with a message`() {
        val newer = voiceJson.replace("\"format\":1", "\"format\":2")
        assertEquals(
            "This voice file was built by a different version of yomiage.",
            assertThrows(BookError::class.java) { VoiceFile.open(voiceFile(newer), listOf(1, 2)) }.message
        )
        assertEquals(
            "Damaged voice file.",
            assertThrows(BookError::class.java) { VoiceFile.open(voiceFile("{\"format\":1}"), listOf(1, 2)) }.message
        )
    }
}
