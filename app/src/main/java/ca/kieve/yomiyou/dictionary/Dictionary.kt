package ca.kieve.yomiyou.dictionary

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.room.withTransaction
import ca.kieve.yomiyou.activity.ActivityLog
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The popup dictionary: one Yomitan dictionary at a time, imported from Browse into a database of its own. The reader
 * page's lookup asks it for entries.
 */
class Dictionary(
    private val context: Context,
    private val library: Library,
    private val log: ActivityLog,
    private val scope: CoroutineScope,
) {
    private val database by lazy { DictionaryDatabase.create(context) }
    private val dao get() = database.dao()

    private val _info = MutableStateFlow<StoredInfo?>(null)
    /** The dictionary there is, or null. */
    val info: StateFlow<StoredInfo?> = _info

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init {
        scope.launch {
            // An import cut short left entries and no info: they go.
            val stored = dao.info()
            if (stored == null) dao.clearEntries()
            _info.value = stored
        }
    }

    /** Replaces the dictionary with the one in [uri]; an entry in the activity log, and a toast at the end. */
    fun import(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            val id = log.start("Dictionary")
            val result = runCatching { withContext(Dispatchers.IO) { read(uri, id) } }
            val title = result.getOrNull()
            if (title != null) {
                log.retitle(id, title)
                log.finish(id, listOf("Added dictionary"), emptyList())
            } else {
                log.finish(id, emptyList(), listOf(result.exceptionOrNull()?.message ?: "Can't read it."))
            }
            _busy.value = false
            val message = if (title != null) "Added dictionary: $title" else result.exceptionOrNull()?.message
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private suspend fun read(uri: Uri, id: Long): String = ZipIndex.open(library.open(uri)).use { zip ->
        val info = Yomitan.info(zip)
        log.retitle(id, info.title)
        log.progress(id, "Clearing the old dictionary")
        clearStored()
        val banks = Yomitan.termBanks(zip)
        var count = 0
        banks.forEachIndexed { index, bank ->
            log.progress(id, "Reading", index * 100 / banks.size)
            val entries = Yomitan.bank(zip, bank)
            database.withTransaction { dao.insert(entries) }
            count += entries.size
        }
        val stored = StoredInfo(title = info.title, revision = info.revision, attribution = info.attribution, entryCount = count)
        dao.setInfo(stored)
        _info.value = stored
        info.title
    }

    fun clear() {
        scope.launch { clearStored() }
    }

    private suspend fun clearStored() {
        _info.value = null
        database.withTransaction {
            dao.clearInfo()
            dao.clearEntries()
        }
    }

    /** Entries whose term or reading is any of [texts], as the page's JSON; none while there's no dictionary. */
    suspend fun findJson(texts: List<String>): String {
        if (_info.value == null) return "[]"
        // SQLite takes up to 999 variables in a query, and each text is two.
        val found = texts.distinct().chunked(CHUNK).flatMap { dao.find(it) }
            .distinctBy { listOf(it.term, it.reading, it.sequence, it.meanings) }
        return Json.encodeToString(found)
    }

    private companion object {
        const val CHUNK = 400
    }
}
