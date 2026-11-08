package ca.kieve.yomiyou.book

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A book folder's `book.json`: which book the folder holds, and where its reader is. */
@Serializable
data class BookInfo(
    val format: Int = FORMAT,
    val bookId: String,
    val title: String,
    val author: String = "",
    val phrases: Int? = null,
    /** The voices whose files are in the folder, in the book's order. */
    val voices: List<VoiceInfo> = emptyList(),
    val position: Position? = null,
) {
    fun encode(): String = json.encodeToString(this) + "\n"

    companion object {
        const val FORMAT = 1
        const val FILE = "book.json"

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun parse(text: String): BookInfo = json.decodeFromString(text)

        fun of(manifest: BookManifest, voices: List<VoiceInfo>): BookInfo =
            BookInfo(
                bookId = manifest.bookId,
                title = manifest.title,
                author = manifest.author,
                phrases = manifest.phrases,
                voices = voices,
            )
    }
}

/** A chapter by its place in the book's chapter list, and the phrase being read. */
@Serializable
data class Position(val chapter: Int, val span: Int)
