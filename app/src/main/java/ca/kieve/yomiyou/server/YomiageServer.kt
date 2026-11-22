package ca.kieve.yomiyou.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/** An update on the server: "none", "queued", a stage such as "making clips: 銀芽 (通常)", "done" or "failed". */
@Serializable
data class UpdateStatus(val stage: String, val running: Boolean = false, val error: String? = null)

@Serializable
data class ServerBook(
    val bookId: String,
    val title: String,
    val author: String = "",
    /** The current build's version; null when the book hasn't been built. */
    val version: String? = null,
    val update: UpdateStatus = UpdateStatus("none"),
    /** Its path under the server's books folder, Kavita's library folder too. */
    val file: String? = null,
)

@Serializable
data class Part(val sha256: String, val size: Long)

@Serializable
data class EpubPart(val file: String, val sha256: String, val size: Long)

@Serializable
data class VoicePart(val path: String, val sha256: String, val size: Long)

@Serializable
data class ServerVoice(val id: String, val file: String, val size: Long, val entries: List<VoicePart>)

/** A build's manifest: the EPUB and each voice file's entries, by content hash. Kept in the book's folder. */
@Serializable
data class ServerManifest(
    val format: Int,
    val bookId: String,
    val version: String,
    val epub: EpubPart,
    val voices: List<ServerVoice>,
) {
    fun encode(): String = json.encodeToString(this) + "\n"

    companion object {
        /** Its name in a book folder: the book came from the server, and this is the build it has. */
        const val FILE = "server.json"

        fun parse(text: String): ServerManifest = json.decodeFromString(text)
    }
}

@Serializable
data class Correction(val form: String, val reading: String, val accent: Int)

internal val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** The server has no such book, or no build of it. */
class NotFound(message: String) : IOException(message)

/** The yomiage server at [address] (such as `https://yomiage.kieve.dev`): its books, files and corrections. */
class YomiageServer(address: String) {
    private val base = address.trim().trimEnd('/')

    @Serializable
    private class Corrected(val update: UpdateStatus)

    @Serializable
    private class Failure(val error: String? = null)

    fun books(): List<ServerBook> = json.decodeFromString(get("/api/books").decodeToString())

    fun manifest(bookId: String): ServerManifest = json.decodeFromString(get("/books/$bookId/manifest.json").decodeToString())

    /** A file by its hash, checked against it. */
    fun part(sha256: String): ByteArray {
        val bytes = get("/files/$sha256")
        if (sha256(bytes) != sha256) throw IOException("A file arrived damaged; try again.")
        return bytes
    }

    fun correct(bookId: String, correction: Correction): UpdateStatus =
        json.decodeFromString<Corrected>(
            request("/api/books/$bookId/corrections", json.encodeToString(correction)).decodeToString()
        ).update

    /** Asks the server to narrate a book: clips for every voice, encoding, the build. */
    fun narrate(bookId: String): UpdateStatus =
        json.decodeFromString(request("/api/books/$bookId/update", "{}").decodeToString())

    fun status(bookId: String): UpdateStatus = json.decodeFromString(get("/api/books/$bookId/update").decodeToString())

    private fun get(path: String): ByteArray = request(path, null)

    private fun request(path: String, body: String?): ByteArray {
        val connection = URI.create(base + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.useCaches = false
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.encodeToByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                val message = runCatching { json.decodeFromString<Failure>(text).error }.getOrNull()
                if (code == 404) throw NotFound(message ?: "Not on the server.")
                throw IOException(message ?: "The server answered $code.")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
