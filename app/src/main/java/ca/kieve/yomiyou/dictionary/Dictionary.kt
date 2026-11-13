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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * The popup dictionaries (JMdict, JMnedict...): imported from Browse into a database of their own, a new revision of
 * one replacing it. The reader page's lookup asks them for entries.
 */
class Dictionary(
    private val context: Context,
    private val library: Library,
    private val log: ActivityLog,
    private val scope: CoroutineScope,
) {
    private val database by lazy { DictionaryDatabase.create(context) }
    private val dao get() = database.dao()

    private val _infos = MutableStateFlow<List<StoredInfo>>(emptyList())
    /** The dictionaries there are, in the order lookups rank them. */
    val infos: StateFlow<List<StoredInfo>> = _infos

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init {
        scope.launch {
            dao.deleteOrphans()
            refresh()
        }
    }

    private suspend fun refresh() {
        _infos.value = inOrder(dao.infos())
    }

    /** Adds the dictionary in [uri], replacing an earlier revision of it; an activity entry, and a toast at the end. */
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
                runCatching { dao.deleteOrphans() }
                log.finish(id, emptyList(), listOf(result.exceptionOrNull()?.message ?: "Can't read it."))
            }
            refresh()
            _busy.value = false
            val message = if (title != null) "Added dictionary: $title" else result.exceptionOrNull()?.message
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private suspend fun read(uri: Uri, logId: Long): String = ZipIndex.open(library.open(uri)).use { zip ->
        val info = Yomitan.info(zip)
        log.retitle(logId, info.title)
        val known = dao.infos()
        val same = known.firstOrNull { nameOf(it.title) == nameOf(info.title) }
        if (same != null) {
            log.progress(logId, "Removing ${same.title}")
            remove(same)
        }
        val id = same?.id ?: ((known.maxOfOrNull { it.id } ?: 0) + 1)
        val banks = Yomitan.termBanks(zip)
        var count = 0
        var names = 0
        banks.forEachIndexed { index, bank ->
            log.progress(logId, "Reading", index * 100 / banks.size)
            val entries = Yomitan.bank(zip, bank).map { it.copy(dictionary = id) }
            database.withTransaction { dao.insert(entries) }
            count += entries.size
            names += entries.count(Yomitan::isName)
        }
        dao.setInfo(StoredInfo(id, info.title, info.revision, info.attribution, count, names * 2 > count))
        info.title
    }

    fun clear(info: StoredInfo) {
        scope.launch {
            _infos.value = _infos.value - info
            remove(info)
            refresh()
        }
    }

    private suspend fun remove(info: StoredInfo) {
        database.withTransaction {
            dao.deleteInfo(info.id)
            dao.deleteEntries(info.id)
        }
    }

    /** Entries whose term or reading is any of [texts], each with its dictionary's rank, as the page's JSON. */
    suspend fun findJson(texts: List<String>): String {
        val ranks = _infos.value.withIndex().associate { (rank, info) -> info.id to rank }
        if (ranks.isEmpty()) return "[]"
        // SQLite takes up to 999 variables in a query, and each text is two.
        val found = texts.distinct().chunked(CHUNK).flatMap { dao.find(it) }
            .filter { it.dictionary in ranks }
            .distinctBy { listOf(it.dictionary, it.term, it.reading, it.sequence, it.meanings) }
        return JsonArray(
            found.map { entry ->
                val fields = Json.encodeToJsonElement(DictionaryEntry.serializer(), entry).jsonObject
                JsonObject(fields + ("rank" to JsonPrimitive(ranks.getValue(entry.dictionary))))
            }
        ).toString()
    }

    companion object {
        private const val CHUNK = 400

        /** The dictionary a title names, whatever its revision: "JMdict (Legacy) [2026-11-11]" → "JMdict (Legacy)". */
        fun nameOf(title: String): String = title.replace(Regex("""\s*\[[^\]]*]\s*$"""), "")

        /** Words before names, then as imported. */
        fun inOrder(infos: List<StoredInfo>): List<StoredInfo> = infos.sortedWith(compareBy({ it.names }, { it.id }))
    }
}
