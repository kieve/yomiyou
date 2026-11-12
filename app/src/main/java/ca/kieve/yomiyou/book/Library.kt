package ca.kieve.yomiyou.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/** A book folder in the library, its `book.json`, and its files by name. */
data class LibraryBook(val folder: Uri, val info: BookInfo, val files: Map<String, Uri>) {
    fun file(name: String): Uri? = files[name]

    val epub: Uri? get() = files.entries.firstOrNull { BookFiles.isEpub(it.key) }?.value

    fun voice(id: String): Uri? = files.entries.firstOrNull { BookFiles.voiceOf(it.key) == id }?.value

    val cover: Uri? get() = files.entries.firstOrNull { BookFiles.isCover(it.key) }?.value
}

/**
 * The folder the user chose for their books, through Android's storage framework, so it's theirs to see and back up:
 * a folder per book, named after its title, its `book.json` saying which book it holds.
 */
class Library(private val context: Context, private val store: DataStore<Preferences>) {
    private val resolver get() = context.contentResolver
    private val scanning = Mutex()
    private val writingInfo = Mutex()
    private val coverless = HashSet<String>()
    private val notVoices = HashSet<Child.Version>()

    /** The chosen folder, or null before one is chosen or after its permission is gone. */
    val folder: Flow<Uri?> = store.data.map { preferences ->
        preferences[FOLDER]?.let(Uri::parse)?.takeIf(::permitted)
    }

    private val _changes = MutableStateFlow(0)

    /** Moves on whenever books are added or removed. */
    val changes: StateFlow<Int> = _changes

    suspend fun choose(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri, flags)
        val old = store.data.first()[FOLDER]?.let(Uri::parse)
        store.edit { it[FOLDER] = uri.toString() }
        if (old != null && old != uri) {
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
    }

    /**
     * The books in the folder. A folder copied in by hand (an enriched EPUB and its voice files, no `book.json`) joins
     * here: its files get their usual names and it gets its `book.json`. So do voice files copied into a book's folder
     * later, or still being copied when it joined; voice files deleted by hand leave it. A book without its cover file
     * gets one.
     */
    suspend fun books(): List<LibraryBook> = withContext(Dispatchers.IO) {
        scanning.withLock {
            val root = root() ?: return@withLock emptyList()
            val books = mutableListOf<LibraryBook>()
            val loose = mutableListOf<Pair<Uri, List<Child>>>()
            for (folder in children(root).filter { it.directory }) {
                var files = children(folder.uri)
                val info = files.firstOrNull { it.name == BookInfo.FILE }?.let { readInfo(it.uri) }
                if (info != null) {
                    var book = LibraryBook(folder.uri, info, files.associate { it.name to it.uri })
                    if (runCatching { renameOld(book) }.getOrDefault(false)) {
                        files = children(folder.uri)
                        book = book.copy(files = files.associate { it.name to it.uri })
                    }
                    books += runCatching { withVoicesInFolder(book, files) }.getOrDefault(book)
                } else {
                    loose += folder.uri to files
                }
            }
            for ((folder, files) in loose) {
                val shelf = books.associate { it.info.bookId to it.info }
                runCatching { adopt(folder, files, shelf) }.getOrNull()?.let { books += it }
            }
            books.distinctBy { it.info.bookId }.map(::withCover)
        }
    }

    suspend fun find(bookId: String): LibraryBook? = books().firstOrNull { it.info.bookId == bookId }

    /** A new folder for a book, named after it. */
    suspend fun createFolder(title: String): Uri = withContext(Dispatchers.IO) {
        val root = root() ?: throw IOException("Choose a library folder first.")
        val taken = children(root).mapTo(HashSet()) { it.name }
        DocumentsContract.createDocument(resolver, root, Document.MIME_TYPE_DIR, FolderNames.of(title, taken))
            ?: throw IOException("Couldn't make a folder for $title.")
    }

    /** Writes [name] in [folder] from [input], replacing any file of that name. */
    suspend fun write(folder: Uri, name: String, input: InputStream, progress: (Long) -> Unit = {}): Uri =
        withContext(Dispatchers.IO) {
            val file = children(folder).firstOrNull { it.name == name && !it.directory }?.uri
                ?: DocumentsContract.createDocument(resolver, folder, OCTET_STREAM, name)
                ?: throw IOException("Couldn't write $name.")
            overwrite(file, input, progress)
            file
        }

    suspend fun writeInfo(folder: Uri, info: BookInfo): Uri = write(folder, BookInfo.FILE, info.encode().byteInputStream())

    /** Changes a book's `book.json` from what's on disk now. */
    suspend fun updateInfo(book: LibraryBook, change: (BookInfo) -> BookInfo) {
        withContext(Dispatchers.IO) {
            writingInfo.withLock {
                val file = book.file(BookInfo.FILE)
                val current = file?.let(::readInfo) ?: book.info
                val text = change(current).encode().byteInputStream()
                if (file != null) overwrite(file, text) else write(book.folder, BookInfo.FILE, text)
            }
        }
    }

    suspend fun delete(folder: Uri) {
        withContext(Dispatchers.IO) {
            if (!DocumentsContract.deleteDocument(resolver, folder)) throw IOException("Couldn't delete the folder.")
        }
        changed()
    }

    fun changed() {
        _changes.value++
    }

    /** A file for reading at any position; close it when done. */
    fun open(file: Uri): RandomAccess {
        val descriptor = resolver.openFileDescriptor(file, "r") ?: throw IOException("Couldn't open $file.")
        return ChannelAccess(FileInputStream(descriptor.fileDescriptor).channel) { descriptor.close() }
    }

    /** The folder as the user would find it, such as `Documents/yomiyou`. */
    fun describe(uri: Uri): String {
        val id = DocumentsContract.getTreeDocumentId(uri)
        return id.substringAfter(':').ifEmpty { id }
    }

    private class Child(val name: String, val uri: Uri, val directory: Boolean, val version: Version) {
        /** A file as it is now: one still being copied in is a new version each time it's seen. */
        data class Version(val uri: Uri, val size: Long, val modified: Long)
    }

    /** One query per folder: going through `DocumentFile` costs one per file and name. */
    private fun children(folder: Uri): List<Child> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getDocumentId(folder))
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        return resolver.query(query, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(0))
                    val version = Child.Version(uri, cursor.getLong(3), cursor.getLong(4))
                    add(Child(cursor.getString(1), uri, cursor.getString(2) == Document.MIME_TYPE_DIR, version))
                }
            }
        }.orEmpty()
    }

    /** Renames files named the way the first builds named them; true if any were. */
    private fun renameOld(book: LibraryBook): Boolean {
        val renames = BookFiles.oldNames(book.info.title, book.files.keys)
        for ((from, to) in renames) {
            DocumentsContract.renameDocument(resolver, book.files.getValue(from), to)
                ?: throw IOException("Couldn't rename $from.")
        }
        return renames.isNotEmpty()
    }

    private suspend fun withVoicesInFolder(book: LibraryBook, files: List<Child>): LibraryBook {
        val kept = book.info.voices.filter { book.voice(it.id) != null }
        val keptIds = kept.mapTo(HashSet()) { it.id }
        val found = files
            .filter { !it.directory && it.name.endsWith(".zip", ignoreCase = true) }
            .filter { BookFiles.voiceOf(it.name) !in keptIds && it.version !in notVoices }
            .mapNotNull { child ->
                val voice = BookFiles.identify(child.name, open(child.uri)) as? BookFiles.Voice
                val id = voice?.manifest?.voice
                if (voice == null || voice.manifest.bookId != book.info.bookId || kept.any { it.id == id }) {
                    notVoices += child.version
                    null
                } else {
                    child to voice
                }
            }
            .distinctBy { it.second.manifest.voice }
        if (found.isEmpty() && kept.size == book.info.voices.size) return book
        val named = book.files.toMutableMap()
        for ((child, voice) in found) {
            if (BookFiles.voiceOf(child.name) != voice.manifest.voice) {
                val name = BookFiles.voice(book.info.title, voice.manifest.voice)
                named.remove(child.name)
                named[name] = DocumentsContract.renameDocument(resolver, child.uri, name)
                    ?: throw IOException("Couldn't rename ${child.name}.")
            }
        }
        val added = found.map { BookFiles.infoOf(it.second.manifest) }
        val voices = if (added.isEmpty()) {
            kept
        } else {
            val epub = book.epub ?: throw IOException("No EPUB in ${book.info.title}.")
            BookFiles.inOrder(ZipIndex.open(open(epub)).use { EnrichedEpub.manifestOf(it) }, kept + added)
        }
        val info = writingInfo.withLock {
            val file = book.file(BookInfo.FILE) ?: throw IOException("No ${BookInfo.FILE} in ${book.info.title}.")
            val updated = (readInfo(file) ?: book.info).copy(voices = voices)
            overwrite(file, updated.encode().byteInputStream())
            updated
        }
        return book.copy(info = info, files = named)
    }

    private fun adopt(folder: Uri, files: List<Child>, shelf: Map<String, BookInfo>): LibraryBook? {
        val candidates = files.filter {
            !it.directory && (it.name.endsWith(".epub", ignoreCase = true) || it.name.endsWith(".zip", ignoreCase = true))
        }
        if (candidates.isEmpty()) return null
        val book = BookFiles.recognize(candidates.map { BookFiles.identify(it.name, open(it.uri)) }, shelf) ?: return null
        val named = files.associateTo(HashMap()) { it.name to it.uri }
        for ((from, to) in BookFiles.renames(book)) {
            val uri = named.remove(from) ?: continue
            named[to] = DocumentsContract.renameDocument(resolver, uri, to) ?: throw IOException("Couldn't rename $from.")
        }
        val manifest = book.epub.manifest
        val voices = BookFiles.inOrder(manifest, book.voices.map { BookFiles.infoOf(it.manifest) })
        val info = BookInfo.of(manifest, voices).copy(lastOpened = System.currentTimeMillis())
        val infoFile = named[BookInfo.FILE]
            ?: DocumentsContract.createDocument(resolver, folder, OCTET_STREAM, BookInfo.FILE)
            ?: throw IOException("Couldn't write ${BookInfo.FILE}.")
        overwrite(infoFile, info.encode().byteInputStream())
        named[BookInfo.FILE] = infoFile
        return LibraryBook(folder, info, named)
    }

    private fun withCover(book: LibraryBook): LibraryBook {
        if (book.cover != null || book.info.bookId in coverless) return book
        val epub = book.epub ?: return book
        val cover = runCatching { ZipIndex.open(open(epub)).use { EnrichedEpub.open(it).cover() } }.getOrNull()
        if (cover == null) {
            coverless += book.info.bookId
            return book
        }
        val name = BookFiles.cover(book.info.title, cover.mediaType)
        return runCatching {
            val file = DocumentsContract.createDocument(resolver, book.folder, OCTET_STREAM, name)
                ?: throw IOException("Couldn't write $name.")
            overwrite(file, cover.bytes.inputStream())
            book.copy(files = book.files + (name to file))
        }.getOrDefault(book)
    }

    private fun overwrite(file: Uri, input: InputStream, progress: (Long) -> Unit = {}) {
        val output = resolver.openOutputStream(file, "wt") ?: throw IOException("Couldn't write $file.")
        output.use {
            val buffer = ByteArray(1 shl 20)
            var copied = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                it.write(buffer, 0, read)
                copied += read
                progress(copied)
            }
        }
    }

    private suspend fun root(): Uri? = folder.first()?.let { tree ->
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun readInfo(file: Uri): BookInfo? = runCatching {
        resolver.openInputStream(file)?.use { BookInfo.parse(it.readBytes().decodeToString()) }
    }.getOrNull()

    private fun permitted(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private companion object {
        val FOLDER = stringPreferencesKey("library.folder")

        /** Kept exactly as named: other types can get an extension added. */
        const val OCTET_STREAM = "application/octet-stream"
    }
}
