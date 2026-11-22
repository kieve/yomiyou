package ca.kieve.yomiyou.kavita

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.activity.ActivityLog
import ca.kieve.yomiyou.book.BookError
import ca.kieve.yomiyou.book.BookFiles
import ca.kieve.yomiyou.book.BookInfo
import ca.kieve.yomiyou.book.ChannelAccess
import ca.kieve.yomiyou.book.EnrichedEpub
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.LibraryBook
import ca.kieve.yomiyou.book.PlainEpub
import ca.kieve.yomiyou.book.Position
import ca.kieve.yomiyou.book.SpinePlace
import ca.kieve.yomiyou.book.ZipIndex
import ca.kieve.yomiyou.reader.ReaderBook
import ca.kieve.yomiyou.server.ServerBooks
import ca.kieve.yomiyou.server.YomiageServer
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
import kotlinx.coroutines.withTimeoutOrNull
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
    private val server: ServerBooks,
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

    private val _moved = MutableSharedFlow<Pair<String, Position>>(extraBufferCapacity = 8)

    /** A book's place moved to Kavita's (book ID, place): an open reader goes there. */
    val moved: SharedFlow<Pair<String, Position>> = _moved

    private val downloading = Mutex()
    private val outbox = KavitaOutbox(File(context.filesDir, "kavita-outbox.json"))
    private val pulling = Mutex()
    /** The books an earlier pull already tried to link, so each is looked for in Kavita once a run. */
    private val linkTried = java.util.Collections.synchronizedSet(HashSet<String>())
    @Volatile
    private var pulledAt = 0L

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
    suspend fun links(books: List<LibraryBook>): Map<Int, String> =
        books.mapNotNull { book -> link(book)?.let { it.chapterId to book.info.bookId } }.toMap()

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

    /** The Kavita chapter a book came from; null for one from elsewhere. */
    suspend fun link(book: LibraryBook): KavitaLink? = withContext(Dispatchers.IO) {
        val file = book.file(KavitaLink.FILE) ?: return@withContext null
        runCatching { KavitaLink.parse(library.read(file).decodeToString()) }
            .onFailure { Log.w(SYNC, "${book.info.title}: unreadable ${KavitaLink.FILE}", it) }
            .getOrNull()
    }

    /** Gives a book here that's also in Kavita (added from files) its link, so its place keeps in step too. */
    fun adopt(bookId: String, book: KavitaBook) {
        scope.launch {
            val here = library.find(bookId) ?: return@launch
            if (here.file(KavitaLink.FILE) != null) return@launch
            library.write(here.folder, KavitaLink.FILE, KavitaLink.of(book).encode().byteInputStream())
            Log.i(SYNC, "${here.info.title}: linked to Kavita chapter ${book.chapterId}")
            library.changed()
        }
    }

    /**
     * Queues the reader's place for Kavita, for a book from there ([link]). [book] is open, so its place in Kavita's
     * form is worked out now; the outbox and the network wait for later.
     */
    fun queue(bookId: String, link: KavitaLink, book: ReaderBook, position: Position) {
        val place = runCatching { book.spinePlace(position) }
            .onFailure { Log.w(SYNC, "${book.title}: no Kavita place for $position", it) }
            .getOrNull()
        if (place == null) {
            Log.i(SYNC, "${book.title}: $position has no Kavita place")
            return
        }
        val queued = QueuedPlace(link, place, position.at ?: System.currentTimeMillis())
        scope.launch {
            if (outbox.queue(bookId, queued)) {
                Log.i(SYNC, "${book.title}: queued $place (at ${queued.at})")
                KavitaSyncWorker.schedule(context)
            } else {
                Log.d(SYNC, "${book.title}: $place already queued or in Kavita")
            }
        }
    }

    /** Sends what's queued; false when Kavita couldn't be reached, to try again later. */
    suspend fun sendQueued(): Boolean = withContext(Dispatchers.IO) {
        val kavita = runCatching { kavita() }.getOrElse {
            Log.w(SYNC, "send: ${it.message}")
            return@withContext true
        }
        val queued = outbox.queued()
        Log.i(SYNC, "send: ${queued.size} queued")
        for ((bookId, place) in queued) {
            val link = place.link
            try {
                kavita.saveProgress(
                    KavitaProgress(link.volumeId, link.chapterId, place.place.page, link.seriesId, link.libraryId, place.place.path)
                )
                outbox.sent(bookId, place)
                Log.i(SYNC, "send: chapter ${link.chapterId} now at ${place.place}")
            } catch (e: Rejected) {
                Log.w(SYNC, "send: chapter ${link.chapterId} refused, dropped", e)
                outbox.drop(bookId, place)
            } catch (e: IOException) {
                Log.w(SYNC, "send: chapter ${link.chapterId} failed, will retry", e)
                return@withContext false
            }
        }
        true
    }

    /**
     * Takes Kavita's places for the books from there, where Kavita's is newer than the one here (or one queued here),
     * at most every [PULL_MS]. The shelf asks, as the app starts and whenever it shows.
     */
    fun pull() {
        scope.launch {
            if (!pulling.tryLock()) return@launch
            try {
                if (System.currentTimeMillis() - pulledAt < PULL_MS) {
                    Log.d(SYNC, "pull: skipped, pulled under ${PULL_MS / 1000} s ago")
                    return@launch
                }
                if (pullNow()) pulledAt = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(SYNC, "pull: failed", e)
            } finally {
                pulling.unlock()
            }
        }
    }

    /** False when Kavita couldn't be reached. */
    private suspend fun pullNow(): Boolean = withContext(Dispatchers.IO) {
        val kavita = runCatching { kavita() }.getOrElse {
            Log.w(SYNC, "pull: ${it.message}")
            return@withContext true
        }
        if (linkUnlinked(kavita)) library.changed()
        val books = library.books()
        Log.i(SYNC, "pull: ${books.size} books")
        var changed = false
        for (book in books) {
            val moved = try {
                reconcile(kavita, book)
            } catch (e: IOException) {
                Log.w(SYNC, "pull: ${book.info.title}: Kavita unreachable", e)
                return@withContext false
            }
            if (moved != null) {
                changed = true
                _moved.tryEmit(book.info.bookId to moved)
            }
        }
        if (changed) library.changed()
        true
    }

    /**
     * One book's place against Kavita's, as [pull] does for all of them: asked as a book opens, and as the app comes back
     * to an open book. Waits at most [PULL_ONE_MS] for the place it moved to (null: none, or not yet); a slower answer
     * still moves the book, and [moved] says so.
     */
    suspend fun pullBook(bookId: String): Position? {
        val check = scope.async {
            val kavita = runCatching { kavita() }.getOrNull() ?: return@async null
            val book = library.find(bookId) ?: return@async null
            runCatching { reconcile(kavita, book) }
                .onFailure { Log.w(SYNC, "pull: ${book.info.title}: ${it.message}") }
                .getOrNull()
                ?.also {
                    library.changed()
                    _moved.tryEmit(bookId to it)
                }
        }
        return withTimeoutOrNull(PULL_ONE_MS) { check.await() }
    }

    /**
     * Takes Kavita's place for [book] when it's newer than the one here (or one queued here), and queues the one here
     * when it's newer. Gives the place it moved to.
     *
     * @throws IOException when Kavita can't be reached
     */
    private suspend fun reconcile(kavita: Kavita, book: LibraryBook): Position? {
        val title = book.info.title
        val link = link(book)
        if (link == null) {
            Log.i(SYNC, "pull: $title: no ${KavitaLink.FILE}, not kept in step")
            return null
        }
        val progress = try {
            kavita.progress(link.chapterId)
        } catch (e: Rejected) {
            Log.w(SYNC, "pull: $title: Kavita refused chapter ${link.chapterId}", e)
            return null
        }
        val at = progress.savedAt
        if (at == null) {
            Log.i(SYNC, "pull: $title: never read in Kavita")
            return null
        }
        val bookId = book.info.bookId
        val queued = outbox.queued()[bookId]
        // A place saved before places had times dates from when the book was last opened.
        val here = queued?.at ?: book.info.position?.let { it.at ?: book.info.lastOpened } ?: 0L
        val there = SpinePlace(progress.pageNum, progress.bookScrollId ?: "//body")
        Log.i(SYNC, "pull: $title: Kavita $there at $at; here ${book.info.position} (queued: ${queued?.place}) at $here")
        if (here >= at) {
            // Newer here, and not on its way: send it, so Kavita needn't wait for the book to be opened.
            val mine = if (queued == null) book.info.position?.let { position -> read(book) { it.spinePlace(position) } } else null
            if (mine != null && mine != there && outbox.queue(bookId, QueuedPlace(link, mine, here))) {
                Log.i(SYNC, "pull: $title: newer here, queued $mine")
                KavitaSyncWorker.schedule(context)
            }
            return null
        }
        val (position, mine) = read(book) { it.positionAt(there) to book.info.position?.let(it::spinePlace) } ?: return null
        outbox.took(bookId, there)
        if (position == null || mine == there) {
            Log.i(SYNC, "pull: $title: nothing to change (Kavita's place here: $position, same place: ${mine == there})")
            return null
        }
        val moved = position.copy(at = at)
        library.updateInfo(book) { it.copy(position = moved) }
        Log.i(SYNC, "pull: $title: moved to $moved")
        return moved
    }

    /**
     * Links the books here that came from elsewhere (files, the yomiage server) but are in Kavita too: yomiage knows each
     * one's path by its book ID, and Kavita's books have the same paths. True when it linked any.
     */
    private suspend fun linkUnlinked(kavita: Kavita): Boolean {
        val unlinked = library.books().filter { it.file(KavitaLink.FILE) == null && it.info.bookId !in linkTried }
        if (unlinked.isEmpty()) return false
        Log.i(SYNC, "link: looking for ${unlinked.size} in Kavita: ${unlinked.joinToString { it.info.title }}")
        val files = try {
            YomiageServer(server.address.first()).books()
                .mapNotNull { book -> book.file?.let { book.bookId to KavitaCatalog.nfc(it) } }.toMap()
        } catch (e: IOException) {
            Log.w(SYNC, "link: the yomiage server is unreachable", e)
            return false
        }
        val inKavita = try {
            val series = kavita.series()
            val gate = Semaphore(4)
            kavita.libraries().filter { it.holdsBooks }.flatMap { library ->
                val mine = series.filter { it.libraryId == library.id }
                val volumes = coroutineScope {
                    mine.map { one -> async { gate.withPermit { one.id to kavita.volumes(one.id) } } }.awaitAll()
                }
                KavitaCatalog.books(library, mine, volumes.toMap())
            }.associateBy { it.file }
        } catch (e: IOException) {
            Log.w(SYNC, "link: Kavita is unreachable", e)
            return false
        }
        var linked = false
        for (book in unlinked) {
            linkTried += book.info.bookId
            val file = files[book.info.bookId]
            val there = file?.let(inKavita::get)
            if (there == null) {
                Log.i(SYNC, "link: ${book.info.title}: " + if (file == null) "not on the yomiage server" else "$file isn't in Kavita")
                continue
            }
            library.write(book.folder, KavitaLink.FILE, KavitaLink.of(there).encode().byteInputStream())
            Log.i(SYNC, "link: ${book.info.title}: Kavita chapter ${there.chapterId}")
            linked = true
        }
        return linked
    }

    private fun <T> read(book: LibraryBook, use: (ReaderBook) -> T): T? {
        val file = book.epub ?: book.original ?: return null
        return runCatching {
            ZipIndex.open(library.open(file)).use { zip ->
                use(if (book.epub != null) EnrichedEpub.open(zip) else PlainEpub.open(zip))
            }
        }.onFailure { Log.w(SYNC, "${book.info.title}: can't read it", it) }.getOrNull()
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
        private const val PULL_MS = 20_000L
        private const val PULL_ONE_MS = 3_000L

        /** Logcat's tag for keeping places in step with Kavita. */
        const val SYNC = "Yomi-KavitaSync"
    }
}
