package ca.kieve.yomiyou.kavita

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

@Serializable
data class KavitaLibrary(val id: Int, val name: String, val type: Int = 0, val folders: List<String> = emptyList()) {
    /** Book or light novel libraries: the kinds that hold EPUBs. */
    val holdsBooks: Boolean get() = type == 2 || type == 4
}

@Serializable
data class KavitaSeries(val id: Int, val name: String, val libraryId: Int)

@Serializable
data class KavitaFile(val filePath: String, val bytes: Long = 0)

@Serializable
data class KavitaChapter(
    val id: Int,
    val range: String = "",
    val titleName: String = "",
    val files: List<KavitaFile> = emptyList(),
)

/**
 * A user's place in a chapter: `pageNum` the spine document, `bookScrollId` the block's path, `lastModifiedUtc` when
 * Kavita saved it (UTC, no zone). A chapter never read comes back with zero IDs and the year 1.
 */
@Serializable
data class KavitaProgress(
    val volumeId: Int,
    val chapterId: Int,
    val pageNum: Int,
    val seriesId: Int,
    val libraryId: Int,
    val bookScrollId: String? = null,
    val lastModifiedUtc: String? = null,
) {
    /** When it was saved, in epoch milliseconds; null for a chapter never read. */
    val savedAt: Long?
        get() = lastModifiedUtc?.takeIf { volumeId != 0 }?.let {
            runCatching { java.time.LocalDateTime.parse(it).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        }?.takeIf { it > 0 }
}

@Serializable
data class KavitaVolume(val id: Int, val name: String = "", val chapters: List<KavitaChapter> = emptyList())

/** Kavita turned the request down: sending it again won't help. */
class Rejected(message: String) : IOException(message)

/** Kavita's API at [address] (such as `https://lit.kieve.dev`), as the user whose API key is [key]. */
class Kavita(address: String, private val key: String) {
    val base = address.trim().trimEnd('/')

    fun libraries(): List<KavitaLibrary> = json.decodeFromString(get("/api/library/libraries"))

    /** Every series the user can see, in every library. */
    fun series(): List<KavitaSeries> =
        json.decodeFromString(text(open("/api/series/v2?pageNumber=1&pageSize=0", "{}")))

    fun volumes(seriesId: Int): List<KavitaVolume> = json.decodeFromString(get("/api/series/volumes?seriesId=$seriesId"))

    fun progress(chapterId: Int): KavitaProgress = json.decodeFromString(get("/api/reader/get-progress?chapterId=$chapterId"))

    /** Saves a place; Kavita stamps its own time on it. */
    fun saveProgress(progress: KavitaProgress) {
        text(open("/api/reader/progress", json.encodeToString(progress.copy(lastModifiedUtc = null))))
    }

    /** A chapter's file, as stored; [read] gets the stream and the length Kavita gave, or -1. */
    fun <T> download(chapterId: Int, read: (InputStream, Long) -> T): T {
        val connection = open("/api/download/chapter?chapterId=$chapterId", null)
        try {
            return connection.inputStream.use { read(it, connection.contentLengthLong) }
        } finally {
            connection.disconnect()
        }
    }

    private fun get(path: String): String = text(open(path, null))

    private fun text(connection: HttpURLConnection): String =
        try {
            connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }

    /** A connection whose answer was a success; else an error saying what went wrong. */
    private fun open(path: String, body: String?): HttpURLConnection {
        val connection = URI.create(base + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.useCaches = false
            connection.setRequestProperty(KEY_HEADER, key)
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.encodeToByteArray()) }
            }
            when (val code = connection.responseCode) {
                in 200..299 -> return connection
                401, 403 -> throw IOException("Kavita refused the API key.")
                in 400..499 -> throw Rejected("Kavita answered $code.")
                else -> throw IOException("Kavita answered $code.")
            }
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }

    companion object {
        const val KEY_HEADER = "x-api-key"

        /** A book's cover: its series' when it's the series' only EPUB, else its chapter's. Asked for with the key. */
        fun cover(address: String, book: KavitaBook): String =
            if (book.alone) {
                "$address/api/image/series-cover?seriesId=${book.seriesId}"
            } else {
                "$address/api/image/chapter-cover?chapterId=${book.chapterId}"
            }

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
