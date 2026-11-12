package ca.kieve.yomiyou.browse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.activity.ActivityLog
import ca.kieve.yomiyou.book.BookFiles
import ca.kieve.yomiyou.book.BookInfo
import ca.kieve.yomiyou.book.EnrichedEpub
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.IdentityHashMap

/**
 * Copies picked EPUBs and voice files into the library: a new folder per book, voice files into their book's. Each
 * import is an entry in the [log], and a toast says how it went.
 */
class Importer(
    private val context: Context,
    private val library: Library,
    private val log: ActivityLog,
    private val scope: CoroutineScope,
) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private class Picked(val uri: Uri, val name: String, val size: Long)

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || _busy.value) return
        _busy.value = true
        scope.launch {
            val id = log.start("Import")
            val kept = uris.filter {
                runCatching { context.contentResolver.takePersistableUriPermission(it, READ) }.isSuccess
            }
            val titles = mutableListOf<String>()
            val (added, problems) = try {
                run(uris, id, titles)
            } catch (e: Exception) {
                emptyList<String>() to listOf(e.message ?: e.toString())
            } finally {
                kept.forEach { runCatching { context.contentResolver.releasePersistableUriPermission(it, READ) } }
            }
            log.finish(id, added, problems)
            _busy.value = false
            withContext(Dispatchers.Main) {
                Toast.makeText(context, summary(titles, added, problems), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun run(uris: List<Uri>, id: Long, titles: MutableList<String>): Pair<List<String>, List<String>> =
        withContext(Dispatchers.IO) {
            log.progress(id, "Checking the files")
            val sources = IdentityHashMap<BookFiles.Found, Picked>()
            val found = uris.map { uri ->
                val picked = describe(uri)
                val what = try {
                    BookFiles.identify(picked.name, library.open(uri))
                } catch (e: IOException) {
                    BookFiles.Other(picked.name, e.message ?: "Can't read it.")
                }
                sources[what] = picked
                what
            }
            val shelf = library.books().associateBy { it.info.bookId }
            val plan = BookFiles.plan(found, shelf.mapValues { it.value.info })
            titles += plan.books.map { it.epub.manifest.title } + plan.moreVoices.map { shelf.getValue(it.bookId).info.title }
            if (titles.isNotEmpty()) log.retitle(id, titles.joinToString())
            val copied = plan.books.flatMap { listOf(it.epub) + it.voices } + plan.moreVoices.flatMap { it.voices }
            val total = copied.sumOf { sources.getValue(it).size }.coerceAtLeast(1)
            var done = 0L
            suspend fun copy(file: BookFiles.Found, folder: Uri, name: String, title: String): Uri {
                val picked = sources.getValue(file)
                val input = context.contentResolver.openInputStream(picked.uri) ?: throw IOException("Can't read ${picked.name}.")
                val before = done
                val uri = input.use {
                    library.write(folder, name, it) { bytes ->
                        log.progress(id, "Copying $title", ((before + bytes) * 100 / total).toInt().coerceAtMost(100))
                    }
                }
                done = before + picked.size
                return uri
            }

            val added = mutableListOf<Pair<String, String>>()
            val problems = plan.problems.toMutableList()
            for (book in plan.books) {
                val manifest = book.epub.manifest
                val folder = library.createFolder(manifest.title)
                try {
                    val epub = copy(book.epub, folder, BookFiles.epub(manifest.title), manifest.title)
                    for (voice in book.voices) {
                        copy(voice, folder, BookFiles.voice(manifest.title, voice.manifest.voice), manifest.title)
                    }
                    runCatching { ZipIndex.open(library.open(epub)).use { EnrichedEpub.open(it).cover() } }.getOrNull()?.let {
                        library.write(folder, BookFiles.cover(manifest.title, it.mediaType), it.bytes.inputStream())
                    }
                    val voices = BookFiles.inOrder(manifest, book.voices.map { BookFiles.infoOf(it.manifest) })
                    library.writeInfo(folder, BookInfo.of(manifest, voices).copy(lastOpened = System.currentTimeMillis()))
                    added += manifest.title to context.getString(R.string.activity_addedNovel)
                    if (voices.isNotEmpty()) added += manifest.title to voiceovers(voices.map { it.label })
                } catch (e: Exception) {
                    runCatching { library.delete(folder) }
                    problems += "${manifest.title}: ${e.message ?: e}"
                }
            }
            for (more in plan.moreVoices) {
                val entry = shelf.getValue(more.bookId)
                val title = entry.info.title
                try {
                    for (voice in more.voices) copy(voice, entry.folder, BookFiles.voice(title, voice.manifest.voice), title)
                    val epub = entry.epub ?: throw IOException("Its EPUB is missing.")
                    val manifest = ZipIndex.open(library.open(epub)).use { EnrichedEpub.manifestOf(it) }
                    val voices = more.voices.map { BookFiles.infoOf(it.manifest) }
                    library.updateInfo(entry) { it.copy(voices = BookFiles.inOrder(manifest, it.voices + voices)) }
                    added += title to voiceovers(voices.map { it.label })
                } catch (e: Exception) {
                    problems += "$title: ${e.message ?: e}"
                }
            }
            library.changed()
            val oneBook = added.map { it.first }.distinct().size <= 1
            added.map { (title, line) -> if (oneBook) line else "$line: $title" } to problems
        }

    private fun voiceovers(labels: List<String>): String =
        context.resources.getQuantityString(R.plurals.activity_addedVoiceovers, labels.size, labels.joinToString())

    private fun describe(uri: Uri): Picked {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val (name, size) = context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0) to (if (cursor.isNull(1)) -1L else cursor.getLong(1))
            } else {
                null
            }
        } ?: (null to -1L)
        val known = size.takeIf { it >= 0 }
            ?: runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull()
            ?: 0L
        return Picked(uri, name ?: uri.lastPathSegment.orEmpty(), known)
    }

    companion object {
        private const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION

        /** The toast: what was added and to which book, or the one problem, or counts and a pointer to the Activity tab. */
        fun summary(titles: List<String>, added: List<String>, problems: List<String>): String {
            val problemCount = if (problems.size == 1) "1 problem" else "${problems.size} problems"
            return when {
                problems.isEmpty() && titles.size == 1 -> "${added.joinToString()}: ${titles[0]}"
                problems.isEmpty() -> "Added ${added.size} items."
                added.isEmpty() -> problems.singleOrNull() ?: "Nothing added, $problemCount. See Activity."
                else -> "Added ${added.size}, $problemCount. See Activity."
            }
        }
    }
}
