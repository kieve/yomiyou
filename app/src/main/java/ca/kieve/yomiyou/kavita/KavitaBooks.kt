package ca.kieve.yomiyou.kavita

import android.content.Context
import ca.kieve.yomiyou.activity.ActivityLog
import ca.kieve.yomiyou.book.BookError
import ca.kieve.yomiyou.book.BookFiles
import ca.kieve.yomiyou.book.BookInfo
import ca.kieve.yomiyou.book.ChannelAccess
import ca.kieve.yomiyou.book.EnrichedEpub
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.LibraryBook
import ca.kieve.yomiyou.book.PlainEpub
import ca.kieve.yomiyou.book.ZipIndex
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/** A download from Kavita under way, or why it failed. */
sealed interface KavitaState {
    data class Downloading(val bytes: Long, val total: Long) : KavitaState

    data class Failed(val message: String) : KavitaState
}

/**
 * Kavita's side of the app: its address and API key, its libraries and their EPUBs, and downloads of the originals
 * into the library, each book folder then holding a `kavita.json`.
 */
class KavitaBooks(
    private val context: Context,
    private val library: Library,
    private val log: ActivityLog,
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    val address: Flow<String> = store.data.map { it[ADDRESS].orEmpty() }
    val key: Flow<String> = store.data.map { it[KEY].orEmpty() }

    private val _libraries = MutableStateFlow<Result<List<KavitaLibrary>>?>(null)

    /** The libraries that hold books, once listed; a failure says why Kavita couldn't be reached. */
    val libraries: StateFlow<Result<List<KavitaLibrary>>?> = _libraries

    private val _books = MutableStateFlow<Map<Int, Result<List<KavitaBook>>>>(emptyMap())

    /** Each listed library's EPUBs, by library ID. */
    val books: StateFlow<Map<Int, Result<List<KavitaBook>>>> = _books

    private val _states = MutableStateFlow<Map<Int, KavitaState>>(emptyMap())

    /** Downloads under way or failed, by chapter ID. */
    val states: StateFlow<Map<Int, KavitaState>> = _states

    private val downloading = Mutex()

    suspend fun setAddress(address: String) {
        store.edit { it[ADDRESS] = address.trim().trimEnd('/') }
        forget()
    }

    suspend fun setKey(key: String) {
        store.edit { it[KEY] = key.trim() }
        forget()
    }

    private fun forget() {
        _libraries.value = null
        _books.value = emptyMap()
    }

    private suspend fun kavita(): Kavita {
        val address = address.first()
        val key = key.first()
        if (address.isEmpty() || key.isEmpty()) throw IOException("Set Kavita's address and API key in Settings.")
        return Kavita(address, key)
    }

    /** Lists the libraries again, and forgets the books listed before. */
    fun refresh() {
        scope.launch {
            _books.value = emptyMap()
            _libraries.value = runCatching { withContext(Dispatchers.IO) { kavita().libraries().filter { it.holdsBooks } } }
        }
    }

    /** Lists a library's EPUBs: its series, then each one's volumes, a few at a time. */
    fun load(library: KavitaLibrary) {
        scope.launch {
            _books.update { it - library.id }
            val listed = runCatching {
                withContext(Dispatchers.IO) {
                    val kavita = kavita()
                    val series = kavita.series().filter { it.libraryId == library.id }
                    val gate = Semaphore(4)
                    val volumes = series.map { one -> async { gate.withPermit { one.id to kavita.volumes(one.id) } } }.awaitAll()
                    KavitaCatalog.books(library, series, volumes.toMap())
                }
            }
            _books.update { it + (library.id to listed) }
        }
    }

    /** The Kavita chapter each book on the shelf came from. */
    suspend fun links(books: List<LibraryBook>): Map<Int, String> = withContext(Dispatchers.IO) {
        books.mapNotNull { book ->
            book.file(KavitaLink.FILE)?.let { file ->
                runCatching { KavitaLink.parse(library.read(file).decodeToString()).chapterId to book.info.bookId }.getOrNull()
            }
        }.toMap()
    }

    private fun set(chapterId: Int, state: KavitaState?) =
        _states.update { if (state == null) it - chapterId else it + (chapterId to state) }

    /** Downloads a book's original into the library, after any download already under way. */
    fun download(book: KavitaBook) {
        set(book.chapterId, KavitaState.Downloading(0, book.bytes))
        scope.launch {
            downloading.withLock {
                val id = log.start("Download")
                log.retitle(id, book.title)
                try {
                    val added = withContext(Dispatchers.IO) { fetch(book, id) }
                    log.finish(id, listOf(added), emptyList())
                    set(book.chapterId, null)
                } catch (e: Exception) {
                    log.finish(id, emptyList(), listOf(e.message ?: e.toString()))
                    set(book.chapterId, KavitaState.Failed(e.message ?: e.toString()))
                }
            }
        }
    }

    private suspend fun fetch(book: KavitaBook, logId: Long): String {
        val temp = File.createTempFile("kavita", ".epub", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            kavita().download(book.chapterId) { input, length ->
                val total = if (length > 0) length else book.bytes
                var done = 0L
                var shown = -1L
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (done - shown < 256 * 1024) continue
                        shown = done
                        set(book.chapterId, KavitaState.Downloading(done, total))
                        log.progress(logId, "Downloading ${book.title}", if (total > 0) (done * 100 / total).toInt() else null)
                    }
                }
            }
            val bookId = digest.digest().take(16).joinToString("") { "%02x".format(it) }
            val link = KavitaLink.of(book).encode()
            library.find(bookId)?.let { here ->
                library.write(here.folder, KavitaLink.FILE, link.byteInputStream())
                library.changed()
                return "Already on the shelf"
            }
            val (title, author, cover) = open(temp).use { zip ->
                if (EnrichedEpub.isEnriched(zip)) throw BookError("It's a narrated book: add it from the yomiage server.")
                val epub = PlainEpub.open(zip)
                Triple(epub.title.ifBlank { book.title }, epub.author, runCatching { epub.cover() }.getOrNull())
            }
            val folder = library.createFolder(title)
            try {
                temp.inputStream().use { library.write(folder, BookFiles.original(title), it) }
                cover?.let { library.write(folder, BookFiles.cover(title, it.mediaType), it.bytes.inputStream()) }
                library.writeInfo(
                    folder,
                    BookInfo(bookId = bookId, title = title, author = author, lastOpened = System.currentTimeMillis())
                )
                library.write(folder, KavitaLink.FILE, link.byteInputStream())
            } catch (e: Exception) {
                runCatching { library.delete(folder) }
                throw e
            }
            library.changed()
            return "Downloaded, ${(temp.length() + 500_000) / 1_000_000} MB"
        } finally {
            temp.delete()
        }
    }

    private fun open(file: File): ZipIndex {
        val handle = RandomAccessFile(file, "r")
        return try {
            ZipIndex.open(ChannelAccess(handle.channel) { handle.close() })
        } catch (e: Exception) {
            handle.close()
            throw e
        }
    }

    companion object {
        private val ADDRESS = stringPreferencesKey("kavita.address")
        private val KEY = stringPreferencesKey("kavita.key")
    }
}
