package ca.kieve.yomiyou.kavita

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

/** One EPUB in Kavita: a book the app can download. [file] is its path under its library's folder, as yomiage has it. */
data class KavitaBook(
    val libraryId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val chapterId: Int,
    val title: String,
    val file: String,
    val bytes: Long,
    /** Whether it's its series' only EPUB, so the series' cover is its own. */
    val alone: Boolean,
)

/** A book folder's `kavita.json`: the book came from Kavita, and this is where it is there. */
@Serializable
data class KavitaLink(val libraryId: Int, val seriesId: Int, val volumeId: Int, val chapterId: Int, val file: String) {
    fun encode(): String = json.encodeToString(this) + "\n"

    companion object {
        const val FILE = "kavita.json"

        fun of(book: KavitaBook) = KavitaLink(book.libraryId, book.seriesId, book.volumeId, book.chapterId, book.file)

        fun parse(text: String): KavitaLink = json.decodeFromString(text)

        private val json = Json { ignoreUnknownKeys = true }
    }
}

object KavitaCatalog {
    /** A library's EPUBs, series by series in Kavita's order; series with none (PDFs, comics) left out. */
    fun books(library: KavitaLibrary, series: List<KavitaSeries>, volumes: Map<Int, List<KavitaVolume>>): List<KavitaBook> =
        series.filter { it.libraryId == library.id }.flatMap { one ->
            val epubs = volumes[one.id].orEmpty().flatMap { volume ->
                volume.chapters.flatMap { chapter ->
                    chapter.files.filter { it.filePath.endsWith(".epub", ignoreCase = true) }.map { Triple(volume, chapter, it) }
                }
            }
            epubs.map { (volume, chapter, file) ->
                KavitaBook(
                    libraryId = library.id,
                    seriesId = one.id,
                    volumeId = volume.id,
                    chapterId = chapter.id,
                    title = if (epubs.size == 1) one.name else chapter.titleName.ifBlank { stem(file.filePath) },
                    file = relative(file.filePath, library.folders),
                    bytes = file.bytes,
                    alone = epubs.size == 1,
                )
            }
        }

    /** The path under whichever of the library's folders holds it, in one Unicode form so it matches yomiage's. */
    fun relative(path: String, folders: List<String>): String {
        val folder = folders.map { it.trimEnd('/') + "/" }.firstOrNull { path.startsWith(it) }
        return nfc(if (folder != null) path.removePrefix(folder) else path.substringAfterLast('/'))
    }

    fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    private fun stem(path: String) = path.substringAfterLast('/').substringBeforeLast('.')
}
