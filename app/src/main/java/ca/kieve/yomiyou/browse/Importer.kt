package ca.kieve.yomiyou.browse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import ca.kieve.yomiyou.book.BookFiles
import ca.kieve.yomiyou.book.BookInfo
import ca.kieve.yomiyou.book.EnrichedEpub
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.IdentityHashMap

/** Copies picked EPUBs and voice files into the library: a new folder per book, voice files into their book's. */
class Importer(private val context: Context, private val library: Library, private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Copying(val title: String, val percent: Int) : State
        data class Done(val added: List<String>, val problems: List<String>) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var job: Job? = null

    private class Picked(val uri: Uri, val name: String, val size: Long)

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || job?.isActive == true) return
        job = scope.launch {
            val kept = uris.filter {
                runCatching { context.contentResolver.takePersistableUriPermission(it, READ) }.isSuccess
            }
            _state.value = try {
                run(uris)
            } catch (e: Exception) {
                State.Done(emptyList(), listOf(e.message ?: e.toString()))
            } finally {
                kept.forEach { runCatching { context.contentResolver.releasePersistableUriPermission(it, READ) } }
            }
        }
    }

    private suspend fun run(uris: List<Uri>): State.Done = withContext(Dispatchers.IO) {
        _state.value = State.Checking
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
        val copied = plan.books.flatMap { listOf(it.epub) + it.voices } + plan.moreVoices.flatMap { it.voices }
        val total = copied.sumOf { sources.getValue(it).size }.coerceAtLeast(1)
        var done = 0L
        suspend fun copy(file: BookFiles.Found, folder: Uri, name: String, title: String): Uri {
            val picked = sources.getValue(file)
            val input = context.contentResolver.openInputStream(picked.uri) ?: throw IOException("Can't read ${picked.name}.")
            val before = done
            val uri = input.use {
                library.write(folder, name, it) { bytes ->
                    _state.value = State.Copying(title, ((before + bytes) * 100 / total).toInt().coerceAtMost(100))
                }
            }
            done = before + picked.size
            return uri
        }

        val added = mutableListOf<String>()
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
                added += if (voices.isEmpty()) "Added ${manifest.title}." else {
                    "Added ${manifest.title} with ${voices.joinToString { it.label }}."
                }
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
                added += "Added ${voices.joinToString { it.label }} to $title."
            } catch (e: Exception) {
                problems += "$title: ${e.message ?: e}"
            }
        }
        library.changed()
        State.Done(added, problems)
    }

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

    private companion object {
        const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}
