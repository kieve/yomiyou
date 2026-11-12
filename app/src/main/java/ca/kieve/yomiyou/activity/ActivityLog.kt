package ca.kieve.yomiyou.activity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File

/** A piece of work the app did or is doing, such as an import, and how it went. */
@Serializable
data class ActivityEntry(
    val id: Long,
    val title: String,
    val started: Long,
    val state: State = State.Running,
    val finished: Long? = null,
    val added: List<String> = emptyList(),
    val problems: List<String> = emptyList(),
    /** While it runs: what it's doing, and how far through, 0 to 100. */
    @Transient val doing: String? = null,
    @Transient val percent: Int? = null,
) {
    enum class State { Running, Done, Interrupted }
}

/** The Activity tab's list, newest first, kept in [file] across restarts: the latest [MAX] entries. */
class ActivityLog(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<ActivityEntry>> = _entries

    @Synchronized
    fun start(title: String): Long {
        val id = (_entries.value.maxOfOrNull { it.id } ?: 0) + 1
        change { listOf(ActivityEntry(id, title, clock())) + it }
        return id
    }

    @Synchronized
    fun retitle(id: Long, title: String) = change { entries -> entries.map { if (it.id == id) it.copy(title = title) else it } }

    /** Not kept: it changes too often, and means nothing once the work stops. */
    @Synchronized
    fun progress(id: Long, doing: String, percent: Int? = null) {
        _entries.value = _entries.value.map { if (it.id == id) it.copy(doing = doing, percent = percent) else it }
    }

    @Synchronized
    fun finish(id: Long, added: List<String>, problems: List<String>) = change { entries ->
        entries.map {
            if (it.id != id) {
                it
            } else {
                it.copy(state = ActivityEntry.State.Done, finished = clock(), added = added, problems = problems,
                    doing = null, percent = null)
            }
        }
    }

    /** Removes what's finished; work still running stays. */
    @Synchronized
    fun clear() = change { entries -> entries.filter { it.state == ActivityEntry.State.Running } }

    private fun change(transform: (List<ActivityEntry>) -> List<ActivityEntry>) {
        val changed = transform(_entries.value)
        val running = changed.count { it.state == ActivityEntry.State.Running }
        var spare = MAX - running
        _entries.value = changed.filter { it.state == ActivityEntry.State.Running || spare-- > 0 }
        save()
    }

    /** Work that was running when the app last closed was cut off with it. */
    private fun load(): List<ActivityEntry> =
        runCatching { json.decodeFromString<List<ActivityEntry>>(file.readText()) }
            .getOrDefault(emptyList())
            .map { if (it.state == ActivityEntry.State.Running) it.copy(state = ActivityEntry.State.Interrupted) else it }

    private fun save() {
        runCatching {
            val written = File(file.parentFile, file.name + ".new")
            written.writeText(json.encodeToString(_entries.value))
            if (!written.renameTo(file)) {
                file.delete()
                written.renameTo(file)
            }
        }
    }

    companion object {
        const val MAX = 100
        private val json = Json { ignoreUnknownKeys = true }
    }
}
