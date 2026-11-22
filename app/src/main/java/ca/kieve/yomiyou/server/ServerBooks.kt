package ca.kieve.yomiyou.server

import ca.kieve.yomiyou.activity.ActivityLog
import ca.kieve.yomiyou.book.BookFiles
import ca.kieve.yomiyou.book.BookInfo
import ca.kieve.yomiyou.book.BytesAccess
import ca.kieve.yomiyou.book.EnrichedEpub
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.LibraryBook
import ca.kieve.yomiyou.book.ZipIndex
import ca.kieve.yomiyou.narration.Narration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where a book stands against the server. */
sealed interface BookState {
    data object NotBuilt : BookState

    /** The server doesn't have it: a book from elsewhere. */
    data object NotOnServer : BookState

    /** The server is updating it (after a correction). */
    data class Working(val stage: String) : BookState

    /** A build to download: the whole book, or an [update] of the copy here. */
    data class Available(val manifest: ServerManifest, val bytes: Long, val update: Boolean) : BookState

    data class Downloading(val bytes: Long, val total: Long) : BookState

    data object Current : BookState

    data class Failed(val message: String) : BookState
}

/**
 * The yomiage server's side of the app: its address, its books and where each stands, downloads into the library
 * (only the parts the copy here lacks), and corrections, followed until their update can be downloaded.
 */
class ServerBooks(
    private val library: Library,
    private val narration: Narration,
    private val log: ActivityLog,
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    val address: Flow<String> = store.data.map { it[ADDRESS] ?: DEFAULT_ADDRESS }

    private val _books = MutableStateFlow<Result<List<ServerBook>>?>(null)

    /** The server's books once listed; a failure says why it couldn't be reached. */
    val books: StateFlow<Result<List<ServerBook>>?> = _books

    private val _states = MutableStateFlow<Map<String, BookState>>(emptyMap())
    val states: StateFlow<Map<String, BookState>> = _states

    private val _updated = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** A book's files were just replaced by a download: an open reader opens it again. */
    val updated: SharedFlow<String> = _updated

    private val downloading = Mutex()
    private val following = HashMap<String, Job>()

    /** Books narrated at the reader's ask, by title: their narration is added once the server has built it. */
    private val adding = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun setAddress(address: String) {
        store.edit { it[ADDRESS] = address.trim().trimEnd('/') }
        refresh()
    }

    private suspend fun server() = YomiageServer(address.first())

    private fun set(bookId: String, state: BookState) = _states.update { it + (bookId to state) }

    /** Lists the server's books and where each stands. */
    fun refresh() {
        scope.launch {
            val listed = runCatching { withContext(Dispatchers.IO) { server().books() } }
            _books.value = listed
            listed.getOrNull()?.forEach { check(it.bookId) }
        }
    }

    /** The manifest a book in the library came with from the server; null for a book added from files. */
    fun stored(book: LibraryBook?): ServerManifest? = book?.file(ServerManifest.FILE)?.let { file ->
        runCatching { ServerManifest.parse(library.read(file).decodeToString()) }.getOrNull()
    }

    /** Works out where a book stands, following the server's update while one runs. */
    suspend fun check(bookId: String) {
        if (_states.value[bookId] is BookState.Downloading) return
        val state = runCatching {
            withContext(Dispatchers.IO) {
                val server = server()
                val status = try {
                    server.status(bookId)
                } catch (e: NotFound) {
                    return@withContext BookState.NotOnServer
                }
                if (status.running) return@withContext BookState.Working(status.stage)
                val manifest = try {
                    server.manifest(bookId)
                } catch (e: NotFound) {
                    return@withContext BookState.NotBuilt
                }
                val here = library.find(bookId)
                val before = stored(here)
                when {
                    before?.version == manifest.version && status.stage == "failed" ->
                        BookState.Failed("The update failed: ${status.error ?: "no reason given"}")
                    before?.version == manifest.version -> BookState.Current
                    else -> BookState.Available(manifest, VoiceAssembly.bytesToFetch(manifest, before), here != null)
                }
            }
        }.getOrElse { BookState.Failed(it.message ?: it.toString()) }
        set(bookId, state)
        if (state is BookState.Working) follow(bookId)
        if (state is BookState.Available) adding.remove(bookId)?.let { download(state.manifest, it) }
        if (state is BookState.Failed) adding.remove(bookId)
    }

    private fun follow(bookId: String) {
        if (following[bookId]?.isActive == true) return
        following[bookId] = scope.launch {
            do {
                delay(POLL_MS)
                check(bookId)
            } while (_states.value[bookId] is BookState.Working)
        }
    }

    /** Sends a correction of how a word is read; the server updates the book, and this follows it. */
    fun correct(bookId: String, correction: Correction) {
        set(bookId, BookState.Working("queued"))
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { server().correct(bookId, correction) } }
                .onSuccess { check(bookId) }
                .onFailure { set(bookId, BookState.Failed(it.message ?: it.toString())) }
        }
    }

    /** Asks the server to narrate a book, follows it, and adds the narration to the library once it's built. */
    fun narrate(bookId: String, title: String) {
        adding[bookId] = title
        set(bookId, BookState.Working("queued"))
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { server().narrate(bookId) } }
                .onSuccess { check(bookId) }
                .onFailure {
                    adding.remove(bookId)
                    set(bookId, BookState.Failed(it.message ?: it.toString()))
                }
        }
    }

    /** Downloads a build into the library, after any download already under way. */
    fun download(manifest: ServerManifest, title: String) {
        val bookId = manifest.bookId
        set(bookId, BookState.Downloading(0, 0))
        scope.launch {
            downloading.withLock {
                val id = log.start("Download")
                log.retitle(id, title)
                try {
                    if (narration.book.value?.bookId == bookId) withContext(Dispatchers.Main) { narration.stop() }
                    val added = apply(manifest) { bytes, total ->
                        set(bookId, BookState.Downloading(bytes, total))
                        log.progress(id, "Downloading $title", if (total > 0) (bytes * 100 / total).toInt() else null)
                    }
                    log.finish(id, listOf(added), emptyList())
                    set(bookId, BookState.Current)
                    _updated.tryEmit(bookId)
                } catch (e: Exception) {
                    log.finish(id, emptyList(), listOf(e.message ?: e.toString()))
                    set(bookId, BookState.Failed(e.message ?: e.toString()))
                }
            }
        }
    }

    /** Brings the library's copy up to [manifest], or makes one; returns what the activity log says was done. */
    private suspend fun apply(manifest: ServerManifest, progress: (Long, Long) -> Unit): String =
        withContext(Dispatchers.IO) {
            val server = server()
            val here = library.find(manifest.bookId)
            val before = stored(here)
            val total = VoiceAssembly.bytesToFetch(manifest, before)
            var downloaded = 0L
            val epubHere = here?.epub?.let { runCatching { library.read(it) }.getOrNull() }
            val sameEpub = epubHere != null && sha256(epubHere) == manifest.epub.sha256
            val epub = if (sameEpub) {
                epubHere
            } else {
                server.part(manifest.epub.sha256).also {
                    downloaded += it.size
                    progress(downloaded, total)
                }
            }
            val book = ZipIndex.open(BytesAccess(epub)).use { EnrichedEpub.manifestOf(it) }
            val title = here?.info?.title ?: book.title
            val folder = here?.folder ?: library.createFolder(book.title)
            try {
                if (!sameEpub) library.replace(folder, BookFiles.epub(title)) { it.write(epub) }
                for (voice in manifest.voices) {
                    val have = here?.voice(voice.id)?.let { runCatching { ZipIndex.open(library.open(it)) }.getOrNull() }
                    val start = downloaded
                    try {
                        library.replace(folder, BookFiles.voice(title, voice.id)) { out ->
                            downloaded += VoiceAssembly.write(
                                voice.entries,
                                have,
                                before?.voices?.firstOrNull { it.id == voice.id }?.entries.orEmpty(),
                                { server.part(it.sha256) },
                                out,
                            ) { progress(start + it, total) }
                        }
                    } finally {
                        have?.close()
                    }
                }
                val ids = manifest.voices.mapTo(HashSet()) { it.id }
                val voices = BookFiles.inOrder(book, book.voices.filter { it.id in ids })
                if (here == null) {
                    runCatching { ZipIndex.open(BytesAccess(epub)).use { EnrichedEpub.open(it).cover() } }.getOrNull()?.let {
                        library.write(folder, BookFiles.cover(book.title, it.mediaType), it.bytes.inputStream())
                    }
                    library.writeInfo(folder, BookInfo.of(book, voices).copy(lastOpened = System.currentTimeMillis()))
                } else {
                    library.updateInfo(here) { it.copy(paragraphs = book.paragraphs, voices = voices) }
                }
                library.write(folder, ServerManifest.FILE, manifest.encode().byteInputStream())
            } catch (e: Exception) {
                if (here == null) runCatching { library.delete(folder) }
                throw e
            }
            library.changed()
            val size = "${(downloaded + 500_000) / 1_000_000} MB"
            if (here == null) "Downloaded, $size" else "Updated, $size"
        }

    companion object {
        const val DEFAULT_ADDRESS = "https://yomiage.kieve.dev"
        private val ADDRESS = stringPreferencesKey("server.address")
        private const val POLL_MS = 2000L
    }
}
