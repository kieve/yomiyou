package ca.kieve.yomiyou.kavita

import ca.kieve.yomiyou.book.SpinePlace
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A place for Kavita: which chapter there, the place, and when the reader moved to it (epoch milliseconds). */
@Serializable
data class QueuedPlace(val link: KavitaLink, val place: SpinePlace, val at: Long)

/**
 * Places waiting to reach Kavita, one per book (a newer one replaces it), and the place Kavita last had from here or
 * gave here, so the same place isn't sent again. Kept in [file], so they wait out the app closing and the network
 * being gone.
 */
class KavitaOutbox(private val file: File) {
    @Serializable
    private data class State(
        val queued: Map<String, QueuedPlace> = emptyMap(),
        val known: Map<String, SpinePlace> = emptyMap(),
    )

    /** Queues a book's place; false when Kavita has it already (anything queued then dropped) or it's queued. */
    @Synchronized
    fun queue(bookId: String, place: QueuedPlace): Boolean {
        val state = load()
        if (state.known[bookId] == place.place) {
            if (bookId in state.queued) save(state.copy(queued = state.queued - bookId))
            return false
        }
        if (state.queued[bookId]?.place == place.place) return false
        save(state.copy(queued = state.queued + (bookId to place)))
        return true
    }

    @Synchronized
    fun queued(): Map<String, QueuedPlace> = load().queued

    /** Kavita turned [place] down: out of the queue, unless a newer one replaced it. */
    @Synchronized
    fun drop(bookId: String, place: QueuedPlace) {
        val state = load()
        if (state.queued[bookId] == place) save(state.copy(queued = state.queued - bookId))
    }

    /** Kavita took [place]: known there now, and out of the queue unless a newer one replaced it meanwhile. */
    @Synchronized
    fun sent(bookId: String, place: QueuedPlace) {
        val state = load()
        val queued = if (state.queued[bookId] == place) state.queued - bookId else state.queued
        save(State(queued, state.known + (bookId to place.place)))
    }

    /** Kavita's newer [place] was taken here: whatever was queued is dropped. */
    @Synchronized
    fun took(bookId: String, place: SpinePlace) {
        val state = load()
        save(State(state.queued - bookId, state.known + (bookId to place)))
    }

    private fun load(): State = runCatching { json.decodeFromString<State>(file.readText()) }.getOrDefault(State())

    private fun save(state: State) {
        val written = File(file.parentFile, file.name + ".new")
        written.writeText(json.encodeToString(state))
        if (!written.renameTo(file)) {
            file.delete()
            written.renameTo(file)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
