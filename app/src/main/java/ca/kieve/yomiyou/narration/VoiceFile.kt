package ca.kieve.yomiyou.narration

import ca.kieve.yomiyou.book.BookError
import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable

/** A voice file's `voice.json`, written by yomiage's build. */
@Serializable
data class VoiceManifest(
    val format: Int,
    val bookId: String,
    val voice: String,
    val label: String,
    val credit: String = "",
    val segments: List<SegmentInfo>,
    val chapters: List<ChapterTimings>,
)

@Serializable
data class SegmentInfo(val file: String, val seconds: Double)

@Serializable
data class ChapterTimings(val number: Int, val timings: String)

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
        const val FORMAT = 1
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * @param chapterNumbers the book's chapter numbers (`yomiage.json`), by place in its chapter list
         * @throws BookError when it isn't a voice file this reader can play
         */
        fun open(zip: ZipIndex, chapterNumbers: List<Int>): VoiceFile {
            if (!zip.has("voice.json")) throw BookError("Not a voice file.")
            try {
                val manifest = json.decodeFromString<VoiceManifest>(zip.text("voice.json"))
                if (manifest.format != FORMAT) {
                    throw BookError("This voice file was built by a different version of yomiage.")
                }
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
