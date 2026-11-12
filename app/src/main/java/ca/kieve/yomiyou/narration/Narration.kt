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
import androidx.datastore.preferences.core.stringPreferencesKey
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
data class NarratedBook(
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

    /** The voice last chosen in a menu, for every book that has it. */
    val remembered: Flow<String?> = store.data.map { it[VOICE] }

    @Volatile
    private var voice: VoiceFile? = null

    /** The narrated book's voices opened so far, left open: the player may still be reading the one it just left. */
    @Volatile
    private var files: Map<String, VoiceFile> = emptyMap()
    private var chapterNumbers: List<Int> = emptyList()
    private var entry: LibraryBook? = null
    private var wanted: String? = null
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

    /**
     * Remembers [voiceId] as the voice to narrate in. If [entry] is being narrated, it switches there and then: the
     * phrase being spoken starts again in the new voice, playing or paused as it was.
     */
    suspend fun useVoice(entry: LibraryBook, voiceId: String) {
        store.edit { it[VOICE] = voiceId }
        wanted = voiceId
        val book = _book.value?.takeIf { it.bookId == entry.info.bookId && it.voice != voiceId } ?: return
        val player = controller ?: return
        _error.value = null
        val numbers = chapterNumbers
        val file = try {
            files[voiceId] ?: withContext(Dispatchers.IO) {
                val found = entry.voice(voiceId) ?: throw BookError("This voice's file is missing.")
                openVoice(found, book.bookId, numbers)
            }
        } catch (e: BookError) {
            _error.value = e.message
            return
        } catch (e: IOException) {
            _error.value = e.message ?: e.toString()
            return
        }
        val now = _book.value
        if (now?.bookId != book.bookId || now.voice == voiceId || wanted != voiceId) {
            if (files[voiceId] !== file) file.close()
            return
        }
        files = files + (voiceId to file)
        val timeline = file.timeline
        val target = _spoken.value?.let { timeline.from(it.phrase) } ?: timeline.chapterStart(0) ?: return
        val switched = now.copy(voice = voiceId)
        voice = file
        _book.value = switched
        player.setMediaItems(items(switched, file), target.segment, (target.start * 1000).toLong())
        player.prepare()
        _spoken.value = Spoken(target.chapter, target.phrase)
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

    /** For [VoiceDataSource]: segment bytes of the book being narrated, by their URI. */
    fun audio(uri: Uri): Pair<VoiceFile, LongRange>? {
        if (uri.scheme != VoiceDataSource.SCHEME) return null
        val (voiceId, segment) = uri.pathSegments.takeIf { it.size == 2 } ?: return null
        val voice = files[voiceId]?.takeIf { it.manifest.bookId == uri.authority } ?: return null
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
        val remembered = remembered.first()
        val opened = withContext(Dispatchers.IO) { open(entry, remembered) }
        voice = opened.voice
        files = mapOf(opened.book.voice to opened.voice)
        chapterNumbers = opened.chapterNumbers
        this.entry = entry
        _book.value = opened.book
        player.setMediaItems(items(opened.book, opened.voice))
        player.setPlaybackSpeed(speed.first())
        player.prepare()
    }

    private fun items(book: NarratedBook, voice: VoiceFile): List<MediaItem> =
        voice.manifest.segments.indices.map { segment ->
            val chapter = voice.timeline.segmentChapters[segment]
            val metadata = MediaMetadata.Builder()
                .setTitle(book.title)
                .setArtist(chapter?.let(book.chapters::getOrNull) ?: book.author)
                .setArtworkUri(book.cover?.let(Uri::fromFile))
                .build()
            MediaItem.Builder()
                .setMediaId(mediaId(book.bookId, book.voice, segment))
                .setUri(VoiceDataSource.uri(book.bookId, book.voice, segment))
                .setMimeType(MimeTypes.AUDIO_OGG)
                .setMediaMetadata(metadata)
                .build()
        }

    private class Opened(val book: NarratedBook, val voice: VoiceFile, val chapterNumbers: List<Int>)

    private fun open(entry: LibraryBook, remembered: String?): Opened {
        val epubFile = entry.epub ?: throw BookError("This book's EPUB is missing.")
        val (manifest, cover) = ZipIndex.open(library.open(epubFile)).use { zip ->
            val epub = EnrichedEpub.open(zip)
            epub.manifest to epub.cover()
        }
        val voices = entry.info.voices
        val (choice, file) = (listOfNotNull(VoiceChoice.of(voices, remembered)) + voices)
            .firstNotNullOfOrNull { info -> entry.voice(info.id)?.let { info to it } }
            ?: throw BookError("No voice files for this book.")
        val numbers = manifest.chapters.map { it.number }
        val voice = openVoice(file, manifest.bookId, numbers)
        val coverFile = cover?.let {
            val extension = if (it.mediaType == "image/png") "png" else "jpg"
            File(context.cacheDir, "covers/${manifest.bookId}.$extension").apply {
                parentFile?.mkdirs()
                writeBytes(it.bytes)
            }
        }
        val labels = manifest.chapters.map { it.title.ifEmpty { it.number.toString() } }
        val book = NarratedBook(manifest.bookId, manifest.title, manifest.author, labels, choice.id, coverFile)
        return Opened(book, voice, numbers)
    }

    private fun openVoice(file: Uri, bookId: String, chapterNumbers: List<Int>): VoiceFile {
        val access = library.open(file)
        val voice = try {
            VoiceFile.open(ZipIndex.open(access), chapterNumbers)
        } catch (e: Exception) {
            access.close()
            throw e
        }
        if (voice.manifest.bookId != bookId) {
            voice.close()
            throw BookError("This voice file belongs to another book.")
        }
        return voice
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
        val voice = voice ?: return TICK_MAX_MS
        if (player.mediaItemCount == 0) return TICK_MAX_MS
        val segment = player.currentMediaItemIndex
        // Right after a voice switch, the service's player can still be on the old voice for a moment.
        if (player.currentMediaItem?.mediaId != mediaId(voice.manifest.bookId, voice.manifest.voice, segment)) {
            return TICK_MIN_MS
        }
        val timeline = voice.timeline
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
        files.values.forEach { it.close() }
        files = emptyMap()
        voice = null
        chapterNumbers = emptyList()
        entry = null
        _book.value = null
        _spoken.value = null
        _playing.value = false
    }

    private companion object {
        val SPEED = floatPreferencesKey("narration.speed")
        val VOICE = stringPreferencesKey("narration.voice")
        const val TICK_MIN_MS = 8L
        const val TICK_MAX_MS = 250L
        const val LEAD_SECONDS = 0.04
        const val SAVE_EVERY_MS = 5000L

        fun mediaId(bookId: String, voiceId: String, segment: Int) = "$bookId/$voiceId/$segment"
    }
}
