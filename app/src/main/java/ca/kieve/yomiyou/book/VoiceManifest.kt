package ca.kieve.yomiyou.book

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

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
) {
    companion object {
        const val FORMAT = 1
        const val FILE = "voice.json"
        private val json = Json { ignoreUnknownKeys = true }

        /** @throws BookError when it isn't a voice file this reader can play */
        fun of(zip: ZipIndex): VoiceManifest {
            if (!zip.has(FILE)) throw BookError("Not a voice file.")
            val manifest = try {
                json.decodeFromString<VoiceManifest>(zip.text(FILE))
            } catch (e: SerializationException) {
                throw BookError("Damaged voice file.")
            } catch (e: IOException) {
                throw BookError("Damaged voice file.")
            }
            if (manifest.format != FORMAT) {
                throw BookError("This voice file was built by a different version of yomiage.")
            }
            return manifest
        }
    }
}

@Serializable
data class SegmentInfo(val file: String, val seconds: Double)

@Serializable
data class ChapterTimings(val number: Int, val timings: String)
