package ca.kieve.yomiyou.book

import java.io.IOException

/**
 * The files of a book folder: what each one is, what it's called there, and which go together. They're named as
 * yomiage names them, after the book's title (`<title>.yomiage.epub`, `<title>.<voice>.voice.zip`, `<title>.cover.jpg`),
 * and found by those endings, so renaming one by hand does no harm.
 */
object BookFiles {
    private const val EPUB_ENDING = ".yomiage.epub"
    private const val VOICE_ENDING = ".voice.zip"
    private val COVER = Regex("""\.cover\.(jpg|png|webp|gif)$""", RegexOption.IGNORE_CASE)

    private const val OLD_EPUB = "book.epub"
    private val OLD_VOICE = Regex("""voice-(.+)\.zip""")
    private val OLD_COVER = Regex("""cover\.(jpg|png|webp|gif)""")

    fun epub(title: String) = FolderNames.file(title, EPUB_ENDING)

    fun voice(title: String, id: String) = FolderNames.file(title, ".$id$VOICE_ENDING")

    fun cover(title: String, mediaType: String) = FolderNames.file(title, ".cover." + extensionOf(mediaType))

    fun isEpub(name: String) = name.endsWith(EPUB_ENDING, ignoreCase = true)

    /** The voice id a voice file's name ends with, if it's named as one. */
    fun voiceOf(name: String): String? =
        name.takeIf { it.endsWith(VOICE_ENDING, ignoreCase = true) }
            ?.dropLast(VOICE_ENDING.length)
            ?.substringAfterLast('.', "")
            ?.ifEmpty { null }

    fun isCover(name: String) = COVER.containsMatchIn(name)

    /** Files named the way the first builds named them (`book.epub`, `voice-<id>.zip`, `cover.jpg`), to their names now. */
    fun oldNames(title: String, names: Collection<String>): List<Pair<String, String>> = names.mapNotNull { name ->
        when {
            name == OLD_EPUB -> name to epub(title)
            OLD_VOICE.matches(name) -> name to voice(title, OLD_VOICE.matchEntire(name)!!.groupValues[1])
            OLD_COVER.matches(name) -> name to FolderNames.file(title, ".$name")
            else -> null
        }
    }

    private fun extensionOf(mediaType: String) = when (mediaType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    sealed interface Found {
        val name: String
    }

    class Book(override val name: String, val manifest: BookManifest) : Found

    class Voice(override val name: String, val manifest: VoiceManifest) : Found

    class Other(override val name: String, val reason: String) : Found

    class NewBook(val epub: Book, val voices: List<Voice>)

    class MoreVoices(val bookId: String, val voices: List<Voice>)

    class Plan(val books: List<NewBook>, val moreVoices: List<MoreVoices>, val problems: List<String>)

    /** Reads [access] (and closes it) to tell an enriched EPUB from a voice file. */
    fun identify(name: String, access: RandomAccess): Found = try {
        ZipIndex.open(access).use { zip ->
            if (zip.has(VoiceManifest.FILE)) {
                Voice(name, VoiceManifest.of(zip))
            } else {
                Book(name, EnrichedEpub.manifestOf(zip))
            }
        }
    } catch (e: BookError) {
        Other(name, e.message.orEmpty())
    } catch (e: IOException) {
        access.close()
        Other(name, "Not an EPUB or a voice file.")
    }

    /**
     * What to do with files picked together: each EPUB is a book, and voice files join their book, picked with them or
     * already on the [shelf] (book ids to their `book.json`). Nothing on the shelf is replaced.
     */
    fun plan(found: List<Found>, shelf: Map<String, BookInfo>): Plan {
        val problems = found.filterIsInstance<Other>().map { "${it.name}: ${it.reason}" }.toMutableList()
        val (onShelf, new) = found.filterIsInstance<Book>()
            .distinctBy { it.manifest.bookId }
            .partition { it.manifest.bookId in shelf }
        onShelf.forEach { problems += "${it.manifest.title} is already on the shelf." }
        val newIds = new.mapTo(HashSet()) { it.manifest.bookId }
        val voices = found.filterIsInstance<Voice>().distinctBy { it.manifest.bookId to it.manifest.voice }
        val more = LinkedHashMap<String, MutableList<Voice>>()
        for (voice in voices) {
            val bookId = voice.manifest.bookId
            val known = shelf[bookId]
            when {
                bookId in newIds -> {}
                known == null -> problems += "${voice.name} is for a book that isn't on the shelf. Pick its EPUB with it."
                known.voices.any { it.id == voice.manifest.voice } ->
                    problems += "${known.title} already has ${voice.manifest.label}."
                else -> more.getOrPut(bookId) { mutableListOf() } += voice
            }
        }
        val books = new.map { epub -> NewBook(epub, voices.filter { it.manifest.bookId == epub.manifest.bookId }) }
        return Plan(books, more.map { (bookId, list) -> MoreVoices(bookId, list) }, problems)
    }

    /**
     * The book in a folder copied in by hand, if it holds one: a single enriched EPUB not on the [shelf] yet, with any of
     * its voice files. Other files don't matter.
     */
    fun recognize(found: List<Found>, shelf: Map<String, BookInfo>): NewBook? = plan(found, shelf).books.singleOrNull()

    /** The book's files whose names don't say what they are, to names that do. yomiage's own names stay. */
    fun renames(book: NewBook): List<Pair<String, String>> {
        val title = book.epub.manifest.title
        val epub = listOfNotNull(book.epub.name.takeUnless(::isEpub)?.let { it to epub(title) })
        val voices = book.voices
            .filter { voiceOf(it.name) != it.manifest.voice }
            .map { it.name to voice(title, it.manifest.voice) }
        return epub + voices
    }

    /** The voices in the book's own order, any it doesn't list after them. */
    fun inOrder(manifest: BookManifest, voices: List<VoiceInfo>): List<VoiceInfo> {
        val known = manifest.voices.map { it.id }.toSet()
        val have = voices.associateBy { it.id }
        return manifest.voices.filter { it.id in have } + voices.filter { it.id !in known }.distinctBy { it.id }
    }

    fun infoOf(voice: VoiceManifest) = VoiceInfo(voice.voice, voice.label, voice.credit)
}
