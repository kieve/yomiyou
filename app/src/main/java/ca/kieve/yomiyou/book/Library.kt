package ca.kieve.yomiyou.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/** A book folder in the library, and its `book.json`. */
class LibraryBook(val folder: DocumentFile, val info: BookInfo) {
    fun file(name: String): DocumentFile? = folder.findFile(name)
}

/**
 * The folder the user chose for their books, through Android's storage framework, so it's theirs to see and back up:
 * a folder per book, named after its title, its `book.json` saying which book it holds.
 */
class Library(private val context: Context, private val store: DataStore<Preferences>) {
    /** The chosen folder, or null before one is chosen or after its permission is gone. */
    val folder: Flow<Uri?> = store.data.map { preferences ->
        preferences[FOLDER]?.let(Uri::parse)?.takeIf(::permitted)
    }

    suspend fun choose(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(uri, flags)
        val old = store.data.first()[FOLDER]?.let(Uri::parse)
        store.edit { it[FOLDER] = uri.toString() }
        if (old != null && old != uri) {
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
    }

    suspend fun books(): List<LibraryBook> = withContext(Dispatchers.IO) {
        root()?.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { folder -> folder.findFile(BookInfo.FILE)?.let { readInfo(it) }?.let { LibraryBook(folder, it) } }
    }

    suspend fun find(bookId: String): LibraryBook? = books().firstOrNull { it.info.bookId == bookId }

    /** A new folder for a book, named after it. */
    suspend fun createFolder(title: String): DocumentFile = withContext(Dispatchers.IO) {
        val root = root() ?: throw IOException("Choose a library folder first.")
        val taken = root.listFiles().mapNotNullTo(HashSet()) { it.name }
        root.createDirectory(FolderNames.of(title, taken)) ?: throw IOException("Couldn't make a folder for $title.")
    }

    /** Writes [name] in [folder] from [input], replacing any file of that name. */
    suspend fun write(folder: DocumentFile, name: String, input: InputStream, progress: (Long) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val file = folder.findFile(name) ?: folder.createFile(OCTET_STREAM, name)
                ?: throw IOException("Couldn't write $name.")
            val output = context.contentResolver.openOutputStream(file.uri, "wt") ?: throw IOException("Couldn't write $name.")
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

    suspend fun writeInfo(folder: DocumentFile, info: BookInfo) =
        write(folder, BookInfo.FILE, info.encode().byteInputStream())

    /** Changes a book's `book.json` from what's on disk now. */
    suspend fun updateInfo(book: LibraryBook, change: (BookInfo) -> BookInfo) {
        val current = book.file(BookInfo.FILE)?.let { readInfo(it) } ?: book.info
        writeInfo(book.folder, change(current))
    }

    /** A file in the library for reading at any position; close it when done. */
    fun open(file: DocumentFile): RandomAccess {
        val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r")
            ?: throw IOException("Couldn't open ${file.name}.")
        return ChannelAccess(FileInputStream(descriptor.fileDescriptor).channel) { descriptor.close() }
    }

    /** The folder as the user would find it, such as `Documents/yomiyou`. */
    fun describe(uri: Uri): String {
        val id = DocumentsContract.getTreeDocumentId(uri)
        return id.substringAfter(':').ifEmpty { id }
    }

    private suspend fun root(): DocumentFile? = folder.first()?.let { DocumentFile.fromTreeUri(context, it) }

    private fun readInfo(file: DocumentFile): BookInfo? = runCatching {
        context.contentResolver.openInputStream(file.uri)?.use { BookInfo.parse(it.readBytes().decodeToString()) }
    }.getOrNull()

    private fun permitted(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private companion object {
        val FOLDER = stringPreferencesKey("library.folder")

        /** Kept exactly as named: other types can get an extension added. */
        const val OCTET_STREAM = "application/octet-stream"
    }
}
