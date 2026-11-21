package ca.kieve.yomiyou.server

import ca.kieve.yomiyou.book.ZipIndex
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A voice file put together from parts, as yomiage's web reader does it: each entry from the copy already here when
 * its content is the same (checked by hash), else downloaded. So an update after a correction downloads a few
 * segments, not the whole file.
 */
object VoiceAssembly {
    /** Every entry's time, as the server's: a fixed one. */
    private const val TIME = 315_532_800_000L

    private fun parts(manifest: ServerManifest): List<Part> =
        listOf(Part(manifest.epub.sha256, manifest.epub.size)) +
            manifest.voices.flatMap { voice -> voice.entries.map { Part(it.sha256, it.size) } }

    /** What a reader with [before] downloads to have [now]: each content it lacks, once. With nothing before, all of it. */
    fun bytesToFetch(now: ServerManifest, before: ServerManifest?): Long {
        val have = before?.let { parts(it).mapTo(HashSet()) { part -> part.sha256 } }.orEmpty()
        return parts(now).filter { it.sha256 !in have }.distinctBy { it.sha256 }.sumOf { it.size }
    }

    /**
     * Writes a voice file's [entries] to [out] (left open); returns the bytes downloaded.
     *
     * @param have the voice file already here, if any
     * @param before the entries it came with, if known: finds one by its content even where its path changed
     * @param fetched bytes downloaded so far
     */
    fun write(
        entries: List<VoicePart>,
        have: ZipIndex?,
        before: List<VoicePart>,
        fetch: (VoicePart) -> ByteArray,
        out: OutputStream,
        fetched: (Long) -> Unit = {},
    ): Long {
        val pathByHash = before.associate { it.sha256 to it.path }
        var downloaded = 0L
        val zip = ZipOutputStream(out)
        for (entry in entries) {
            val local = listOfNotNull(pathByHash[entry.sha256], entry.path).distinct()
                .filter { have?.has(it) == true }
                .firstNotNullOfOrNull { path -> have!!.read(path).takeIf { sha256(it) == entry.sha256 } }
            val bytes = local ?: fetch(entry).also {
                downloaded += it.size
                fetched(downloaded)
            }
            zip.putNextEntry(stored(entry.path, bytes))
            zip.write(bytes)
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
        return downloaded
    }

    private fun stored(name: String, bytes: ByteArray) = ZipEntry(name).apply {
        time = TIME
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        crc = CRC32().apply { update(bytes) }.value
    }
}
