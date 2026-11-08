package ca.kieve.yomiyou.book

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The book a debug build carries (`yomiyou.testBook`, see `app/build.gradle.kts`), copied into the library to read. */
class TestBook(private val context: Context, private val library: Library, private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State
        data class Copying(val percent: Int) : State
        data class Ready(val bookId: String) : State
        data class Failed(val message: String) : State
    }

    private val files: List<String> = context.assets.list(FOLDER).orEmpty().sorted()

    val bundled: Boolean = BOOK in files

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Makes sure the library holds it, copying it in the first time. */
    fun prepare() {
        if (_state.value is State.Copying) return
        scope.launch {
            _state.value = try {
                State.Ready(copyIn())
            } catch (e: Exception) {
                State.Failed(e.message ?: e.toString())
            }
        }
    }

    private suspend fun copyIn(): String {
        val epub = context.assets.open("$FOLDER/$BOOK").use { it.readBytes() }
        val manifest = ZipIndex.open(BytesAccess(epub)).use { EnrichedEpub.manifestOf(it) }
        if (library.find(manifest.bookId) != null) return manifest.bookId
        val voices = manifest.voices.filter { "voice-${it.id}.zip" in files }
        val sizes = files.associateWith { name -> context.assets.openFd("$FOLDER/$name").use { it.length } }
        val total = sizes.values.sum().coerceAtLeast(1)
        var done = 0L
        _state.value = State.Copying(0)
        val folder = library.createFolder(manifest.title)
        for (name in listOf(BOOK) + voices.map { "voice-${it.id}.zip" }) {
            val before = done
            context.assets.open("$FOLDER/$name").use { input ->
                library.write(folder, name, input) { copied ->
                    _state.value = State.Copying(((before + copied) * 100 / total).toInt())
                }
            }
            done += sizes.getValue(name)
        }
        library.writeInfo(folder, BookInfo.of(manifest, voices))
        return manifest.bookId
    }

    private companion object {
        const val FOLDER = "testbook"
        const val BOOK = "book.epub"
    }
}
