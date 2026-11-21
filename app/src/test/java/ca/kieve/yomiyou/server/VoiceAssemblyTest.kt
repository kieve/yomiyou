package ca.kieve.yomiyou.server

import ca.kieve.yomiyou.book.BytesAccess
import ca.kieve.yomiyou.book.ZipIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class VoiceAssemblyTest {
    private fun part(path: String, text: String) = VoicePart(path, sha256(text.encodeToByteArray()), text.length.toLong())

    /** A voice file of these entries, as the assembly writes it, opened for reading. */
    private fun zip(entries: List<Pair<VoicePart, String>>): ZipIndex {
        val out = ByteArrayOutputStream()
        val texts = entries.associate { it.first.sha256 to it.second }
        VoiceAssembly.write(entries.map { it.first }, null, emptyList(), { texts.getValue(it.sha256).encodeToByteArray() }, out)
        return ZipIndex.open(BytesAccess(out.toByteArray()))
    }

    @Test
    fun `an update takes unchanged parts from the copy here and downloads the rest`() {
        val before = listOf(part("voice.json", "v1") to "v1", part("audio/00000.opus", "A") to "A", part("audio/00001.opus", "B") to "B")
        val have = zip(before)
        val now = listOf(part("voice.json", "v2") to "v2", part("audio/00000.opus", "A") to "A", part("audio/00001.opus", "B2") to "B2")
        val asked = mutableListOf<String>()
        val out = ByteArrayOutputStream()
        val downloaded = VoiceAssembly.write(now.map { it.first }, have, before.map { it.first }, { wanted ->
            asked += wanted.path
            now.first { it.first.sha256 == wanted.sha256 }.second.encodeToByteArray()
        }, out)
        assertEquals(listOf("voice.json", "audio/00001.opus"), asked)
        assertEquals(4L, downloaded)
        val result = ZipIndex.open(BytesAccess(out.toByteArray()))
        for ((entry, text) in now) assertEquals(text, result.text(entry.path))
    }

    @Test
    fun `a part is found by its content where its path moved, and a damaged copy is downloaded again`() {
        val before = listOf(part("audio/00000.opus", "A"), part("audio/00001.opus", "B"))
        // The copy here holds something else at 00001 than its manifest says.
        val have = zip(listOf(part("audio/00000.opus", "A") to "A", part("audio/00001.opus", "damaged") to "damaged"))
        val now = listOf(part("audio/00005.opus", "A") to "A", part("audio/00006.opus", "B") to "B")
        val asked = mutableListOf<String>()
        VoiceAssembly.write(now.map { it.first }, have, before, { wanted ->
            asked += wanted.path
            now.first { it.first.sha256 == wanted.sha256 }.second.encodeToByteArray()
        }, ByteArrayOutputStream())
        assertEquals(listOf("audio/00006.opus"), asked)
    }

    private fun manifest(version: String, epub: String, entries: List<Triple<String, String, Long>>) = ServerManifest(
        format = 2,
        bookId = "b",
        version = version,
        epub = EpubPart("book.epub", epub, 1000),
        voices = listOf(ServerVoice("ginga", "g.zip", 0, entries.map { (path, sha, size) -> VoicePart(path, sha, size) })),
    )

    @Test
    fun `an update's size counts each new content once`() {
        val before = manifest("1", "e1", listOf(Triple("voice.json", "v1", 10L), Triple("audio/0", "a", 500L), Triple("audio/1", "b", 600L)))
        val now = manifest("2", "e2", listOf(Triple("voice.json", "v2", 11L), Triple("audio/0", "a", 500L), Triple("audio/1", "c", 700L)))
        assertEquals(1000L + 11 + 700, VoiceAssembly.bytesToFetch(now, before))
        assertEquals(1000L + 11 + 500 + 700, VoiceAssembly.bytesToFetch(now, null))
        assertEquals(0L, VoiceAssembly.bytesToFetch(now, now))
    }

    @Test
    fun `a manifest from the server reads back`() {
        val text = """{"format":2,"bookId":"077868f1eb737e0cbbeeda45ff0037a6","version":"2745f618d71dbb21",
            "epub":{"file":"a.yomiage.epub","sha256":"7e","size":1595513},
            "voices":[{"id":"ginga","file":"a.ginga.voice.zip","size":194823725,
            "entries":[{"path":"audio/00000.opus","sha256":"27","size":28883}]}],
            "since":{"version":"x","bytes":1,"parts":["a"]}}"""
        val manifest = ServerManifest.parse(text)
        assertEquals("2745f618d71dbb21", manifest.version)
        assertEquals(28883L, manifest.voices.single().entries.single().size)
        assertEquals(manifest, ServerManifest.parse(manifest.encode()))
    }
}
