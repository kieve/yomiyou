package ca.kieve.yomiyou.narration

import ca.kieve.yomiyou.book.BookError
import ca.kieve.yomiyou.book.VoiceManifest
import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.Closeable

/**
 * A voice file: an uncompressed zip of Ogg Opus segments and each chapter's timings. The audio is read from where it
 * lies in the file, a segment at a time.
 */
class VoiceFile private constructor(
    private val zip: ZipIndex,
    val manifest: VoiceManifest,
    val timeline: VoiceTimeline,
) : Closeable {
    /** Where segment [index]'s Ogg Opus bytes lie in the file. */
    fun audioRange(index: Int): LongRange = zip.storedRange(manifest.segments[index].file)

    fun readFully(position: Long, into: java.nio.ByteBuffer) = zip.readFully(position, into)

    override fun close() = zip.close()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * @param chapterNumbers the book's chapter numbers (`yomiage.json`), by place in its chapter list
         * @throws BookError when it isn't a voice file this reader can play
         */
        fun open(zip: ZipIndex, chapterNumbers: List<Int>): VoiceFile {
            val manifest = VoiceManifest.of(zip)
            try {
                val timings = manifest.chapters.associate { chapter ->
                    chapter.number to json.decodeFromString<List<List<Double>>>(zip.text(chapter.timings))
                }
                return VoiceFile(zip, manifest, VoiceTimeline.of(timings, chapterNumbers))
            } catch (e: SerializationException) {
                throw BookError("Damaged voice file.")
            }
        }
    }
}
