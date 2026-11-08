package ca.kieve.yomiyou.narration

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ca.kieve.yomiyou.book.BookError
import ca.kieve.yomiyou.book.EnrichedEpub
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.LibraryBook
import ca.kieve.yomiyou.book.Position
import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The book being narrated: what the floating player and the notification show. */
class NarratedBook(
    val bookId: String,
    val title: String,
    val author: String,
    val chapters: List<String>,
    val voice: String,
    val cover: File?,
)

/** The phrase being spoken, with its chapter by place in the book's list. */
data class Spoken(val chapter: Int, val phrase: Int)

/**
 * The app's side of the narration: loads a book's voice into [PlaybackService] through a media controller, follows the
 * phrase being spoken, and keeps it as the book's reading position while it plays.
 */
class Narration(
    private val context: Context,
    private val library: Library,
    private val store: DataStore<Preferences>,
) {
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _book = MutableStateFlow<NarratedBook?>(null)
    val book: StateFlow<NarratedBook?> = _book

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _spoken = MutableStateFlow<Spoken?>(null)
    val spoken: StateFlow<Spoken?> = _spoken

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val speed: Flow<Float> = store.data.map { Speed.of(it[SPEED]) }

    @Volatile
    private var voice: VoiceFile? = null
    private var entry: LibraryBook? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var lastSaved = 0L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
            if (isPlaying) {
                ticker?.cancel()
                ticker = main.launch {
                    while (isActive) delay(follow())
                }
            } else {
                ticker?.cancel()
                follow()
                save()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            follow()
        }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = error.message ?: error.errorCodeName
        }
    }

    /** Plays [entry] from [phrase], or from [chapter]'s start; loads it first unless it's the book being narrated. */
    suspend fun play(entry: LibraryBook, chapter: Int, phrase: Int?) {
        _error.value = null
        try {
            val player = connect()
            if (_book.value?.bookId != entry.info.bookId) load(entry, player)
            val timeline = voice?.timeline ?: return
            val timing = phrase?.let(timeline::from) ?: timeline.chapterStart(chapter) ?: return
            player.seekTo(timing.segment, (timing.start * 1000).toLong())
            _spoken.value = Spoken(timing.chapter, timing.phrase)
            player.play()
        } catch (e: BookError) {
            _error.value = e.message
        } catch (e: IOException) {
            _error.value = e.message ?: e.toString()
        }
    }

    /** Moves the narration of the book being narrated to [chapter]'s start, playing or paused as it was. */
    fun moveTo(chapter: Int) {
        val player = controller ?: return
        val timing = voice?.timeline?.chapterStart(chapter) ?: return
        player.seekTo(timing.segment, (timing.start * 1000).toLong())
        _spoken.value = Spoken(timing.chapter, timing.phrase)
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play()
    }

    suspend fun changeSpeed(steps: Int) {
        store.edit { it[SPEED] = Speed.step(Speed.of(it[SPEED]), steps) }
    }

    /** Ends the narration: the notification goes, and the floating player with it. */
    fun stop() {
        save()
        controller?.run {
            stop()
            clearMediaItems()
        }
        unload()
    }

    /** For [VoiceDataSource]: segment bytes of the voice being narrated, by their URI. */
    fun audio(uri: Uri): Pair<VoiceFile, LongRange>? {
        val voice = voice ?: return null
        if (uri.scheme != VoiceDataSource.SCHEME || uri.authority != voice.manifest.bookId) return null
        val (voiceId, segment) = uri.pathSegments.takeIf { it.size == 2 } ?: return null
        if (voiceId != voice.manifest.voice) return null
        val index = segment.toIntOrNull()?.takeIf { it in voice.manifest.segments.indices } ?: return null
        return voice to voice.audioRange(index)
    }

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    this@Narration.controller = null
                    unload()
                }
            })
            .buildAsync()
        val connected = suspendCancellableCoroutine { continuation ->
            future.addListener(
                { continuation.resumeWith(runCatching { future.get() }) },
                ContextCompat.getMainExecutor(context)
            )
        }
        connected.addListener(listener)
        controller = connected
        main.launch { speed.collect { connected.setPlaybackSpeed(it) } }
        return connected
    }

    private suspend fun load(entry: LibraryBook, player: MediaController) {
        save()
        player.stop()
        player.clearMediaItems()
        unload()
        val (book, voice) = withContext(Dispatchers.IO) { open(entry) }
        this.voice = voice
        this.entry = entry
        _book.value = book
        val items = voice.manifest.segments.indices.map { segment ->
            val chapter = voice.timeline.segmentChapters[segment]
            val metadata = MediaMetadata.Builder()
                .setTitle(book.title)
                .setArtist(chapter?.let(book.chapters::getOrNull) ?: book.author)
                .setArtworkUri(book.cover?.let(Uri::fromFile))
                .build()
            MediaItem.Builder()
                .setMediaId("${book.bookId}/${book.voice}/$segment")
                .setUri(VoiceDataSource.uri(book.bookId, book.voice, segment))
                .setMimeType(MimeTypes.AUDIO_OGG)
                .setMediaMetadata(metadata)
                .build()
        }
        player.setMediaItems(items)
        player.setPlaybackSpeed(speed.first())
        player.prepare()
    }

    private fun open(entry: LibraryBook): Pair<NarratedBook, VoiceFile> {
        val epubFile = entry.file("book.epub") ?: throw BookError("This book's EPUB is missing.")
        val (manifest, cover) = ZipIndex.open(library.open(epubFile)).use { zip ->
            val epub = EnrichedEpub.open(zip)
            epub.manifest to epub.cover()
        }
        val choice = entry.info.voices.firstNotNullOfOrNull { info -> entry.file("voice-${info.id}.zip")?.let { info to it } }
            ?: throw BookError("No voice files for this book.")
        val access = library.open(choice.second)
        val voice = try {
            VoiceFile.open(ZipIndex.open(access), manifest.chapters.map { it.number })
        } catch (e: Exception) {
            access.close()
            throw e
        }
        if (voice.manifest.bookId != manifest.bookId) {
            voice.close()
            throw BookError("This voice file belongs to another book.")
        }
        val coverFile = cover?.let {
            val extension = if (it.mediaType == "image/png") "png" else "jpg"
            File(context.cacheDir, "covers/${manifest.bookId}.$extension").apply {
                parentFile?.mkdirs()
                writeBytes(it.bytes)
            }
        }
        val labels = manifest.chapters.map { it.title.ifEmpty { it.number.toString() } }
        val book = NarratedBook(manifest.bookId, manifest.title, manifest.author, labels, choice.first.id, coverFile)
        return book to voice
    }

    /** The service's player, in this process: its position is exact, where the controller's is an estimate. */
    @Volatile
    private var exact: Player? = null

    fun attach(player: Player) {
        exact = player
    }

    fun detach(player: Player) {
        if (exact === player) exact = null
    }

    /**
     * Follows the phrase being spoken; while playing, keeps it as the reading position now and then. Returns how long
     * until the next phrase starts, so the light changes on time rather than at the next poll.
     */
    private fun follow(): Long {
        val player = exact ?: controller ?: return TICK_MAX_MS
        val timeline = voice?.timeline ?: return TICK_MAX_MS
        if (player.mediaItemCount == 0) return TICK_MAX_MS
        val segment = player.currentMediaItemIndex
        // A little ahead of the voice: the page takes a frame or two to show the light.
        val seconds = player.currentPosition / 1000.0 + LEAD_SECONDS
        timeline.phraseAt(segment, seconds)?.let { timing ->
            val spoken = Spoken(timing.chapter, timing.phrase)
            if (spoken != _spoken.value) {
                _spoken.value = spoken
                if (SystemClock.elapsedRealtime() - lastSaved > SAVE_EVERY_MS) save()
            }
        }
        val next = timeline.nextStart(segment, seconds) ?: return TICK_MAX_MS
        val wait = (next - seconds) / player.playbackParameters.speed * 1000
        return wait.toLong().coerceIn(TICK_MIN_MS, TICK_MAX_MS)
    }

    private fun save() {
        val entry = entry ?: return
        val spoken = _spoken.value ?: return
        lastSaved = SystemClock.elapsedRealtime()
        main.launch(Dispatchers.IO) {
            runCatching { library.updateInfo(entry) { it.copy(position = Position(spoken.chapter, spoken.phrase)) } }
        }
    }

    private fun unload() {
        ticker?.cancel()
        voice?.close()
        voice = null
        entry = null
        _book.value = null
        _spoken.value = null
        _playing.value = false
    }

    private companion object {
        val SPEED = floatPreferencesKey("narration.speed")
        const val TICK_MIN_MS = 8L
        const val TICK_MAX_MS = 250L
        const val LEAD_SECONDS = 0.04
        const val SAVE_EVERY_MS = 5000L
    }
}
