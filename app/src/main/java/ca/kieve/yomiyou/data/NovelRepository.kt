package ca.kieve.yomiyou.data

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.data.model.Novel
import ca.kieve.yomiyou.data.model.OpenChapter
import ca.kieve.yomiyou.data.scheduler.DownloadChapterInfoJob
import ca.kieve.yomiyou.data.scheduler.DownloadChapterJob
import ca.kieve.yomiyou.data.scheduler.DownloadNovelInfoJob
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.SortedMap
import java.util.TreeMap

class NovelRepository(context: Context) {
    companion object {
        private val TAG: String = getTag()
    }

    private val ioScope = CoroutineScope(context = Job() + Dispatchers.IO)
    private val defaultScope = CoroutineScope(context = Job() + Dispatchers.Default)

    private val appContainer = (context as YomiApplication).container
    private val yomiFiles = appContainer.files
    private val novelDao = appContainer.database.novelDao()
    private val scheduler = appContainer.novelScheduler
    private val crawler = appContainer.crawler

    private val novelMutex = Mutex()
    private val _novels: MutableStateFlow<MutableMap<Long, Novel>> = MutableStateFlow(hashMapOf())
    val novels: StateFlow<Map<Long, Novel>> = _novels

    private var openNovelId: Long? = null
    private val _openNovel: MutableStateFlow<SortedMap<Long, OpenChapter>> =
        MutableStateFlow(TreeMap())
    val openNovel: StateFlow<SortedMap<Long, OpenChapter>> = _openNovel

    private val searchMutex = Mutex()
    private val _searchResults: MutableStateFlow<Set<Long>> = MutableStateFlow(hashSetOf())
    val searchResults: StateFlow<Set<Long>> = _searchResults
    private val _searchInProgress: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val searchInProgress: StateFlow<Boolean> = _searchInProgress

    init {
        ioScope.launch {
            loadNovels()
        }
    }

    private fun setNovel(novel: Novel) {
        if (!novelMutex.isLocked) {
            throw IllegalStateException("Novel mutex lock must be held to update.")
        }
        _novels.value = (_novels.value + Pair(novel.metadata.id, novel)).toMutableMap()
    }

    private fun getNovel(id: Long): Novel? {
        return _novels.value[id]
    }

    private fun getNovel(url: String): Novel? {
        for (novel in _novels.value.values) {
            if (crawler.areSameNovel(novel.metadata.url, url)) {
                return novel
            }
        }
        return null
    }

    private suspend fun loadNovels() = withContext(Dispatchers.IO) {
        val novelMetas = novelDao.getAllNovelMeta()
        for (novelMeta in novelMetas) {
            novelMutex.withLock {
                setNovel(
                    Novel(
                        metadata = novelMeta
                    )
                )
            }
            launch {
                loadNovelCover(novelMeta)
            }
            launch {
                loadNovelChapters(novelMeta)
            }
        }
        Log.d(TAG, "Queued novel loading ")
    }

    private suspend fun loadNovelCover(novelMeta: NovelMeta) = withContext(Dispatchers.IO) {
        val novelCoverFile = yomiFiles.getNovelCoverFile(novelMeta)
        if (novelCoverFile != null && novelCoverFile.exists()) {
            novelMutex.withLock {
                val novel = getNovel(novelMeta.id)
                if (novel == null) {
                    Log.w(TAG, "Can't load novel cover; the novel is null?")
                    return@withContext
                }
                setNovel(
                    novel.copy(
                        coverFile = novelCoverFile
                    )
                )
            }
            return@withContext
        }
        downloadCover(novelMeta)
    }

    private suspend fun loadNovelChapters(novelMeta: NovelMeta) = withContext(Dispatchers.IO) {
        val chapters = novelDao.getNovelChapters(novelMeta.id)
        novelMutex.withLock {
            val novel = getNovel(novelMeta.id)
            if (novel == null) {
                Log.w(TAG, "Can't load novel chapters; the novel is null?")
                return@withContext
            }
            setNovel(
                novel.copy(
                    chapters = chapters
                )
            )
        }

        for (chapterMeta in chapters) {
            launch {
                loadNovelChapter(chapterMeta)
            }
        }
    }

    private suspend fun loadNovelChapter(chapterMeta: ChapterMeta) = withContext(Dispatchers.IO) {
        val chapterFile = yomiFiles.getChapterFile(chapterMeta)
        if (chapterFile != null && chapterFile.exists()) {
            novelMutex.withLock {
                val novel = getNovel(chapterMeta.novelId)
                if (novel == null) {
                    Log.w(TAG, "Can't load chapter; the novel is null?")
                    return@withContext
                }
                setNovel(
                    novel.copy(
                        chapterFiles = novel.chapterFiles + Pair(chapterMeta.id, chapterFile)
                    )
                )
            }
            return@withContext
        }
        val novel = getNovel(chapterMeta.novelId)
        if (novel != null && novel.metadata.inLibrary) {
            downloadChapter(chapterMeta)
        }
    }

    suspend fun onNovelInfoUpdate(novelId: Long, novelInfo: NovelInfo?) {
        novelInfo ?: return
        Log.d(TAG, "onNovelInfoUpdate: ${novelInfo.title}")

        novelMutex.withLock {
            val novel = getNovel(novelId)
            if (novel == null) {
                Log.d(TAG, "Novel is missing after Downloading Novel Info")
                return@withLock
            }

            var anyChange = false
            var metadata = novel.metadata

            val title = novelInfo.title
            if (title != null && metadata.title != title) {
                anyChange = true
                metadata = metadata.copy(
                    title = title
                )
            }

            val author = novelInfo.author
            if (author != null && metadata.author != author) {
                anyChange = true
                metadata = metadata.copy(
                    author = author
                )
            }

            var downloadCover = false
            val coverUrl = novelInfo.coverUrl
            if (coverUrl != null && metadata.coverUrl != coverUrl) {
                anyChange = true
                downloadCover = true
                metadata = metadata.copy(
                    coverUrl = coverUrl
                )
            }

            var updatedNovel = novel
            if (anyChange) {
                updatedNovel = novel.copy(
                    metadata = metadata
                )
                novelDao.upsertNovelMeta(metadata)
                setNovel(updatedNovel)
            }
            if (downloadCover) {
                downloadCover(updatedNovel.metadata)
            }
        }
    }

    suspend fun onChapterListUpdate(novelId: Long, chapterInfoList: List<ChapterInfo>) {
        if (chapterInfoList.isEmpty()) return

        novelMutex.withLock {
            val novel = getNovel(novelId) ?: return
            Log.d(TAG, "onChapterListUpdate: ${novel.metadata.title}")

            val chapterList = chapterInfoList.map { info ->
                ChapterMeta(
                    id = info.id,
                    novelId = novel.metadata.id,
                    title = info.title,
                    url = info.url
                )
            }

            // Merge the chapter list
            val chapterMap = novel.chapters.map { chapterMeta ->
                chapterMeta.id to chapterMeta
            }.toMap().toMutableMap()

            for (chapter in chapterList) {
                chapterMap[chapter.id] = chapter
            }

            val sortedChapters = chapterMap.values.toList()
                .sortedBy { chapterMeta -> chapterMeta.id }

            novelDao.upsertChapterMeta(sortedChapters)
            setNovel(
                novel.copy(
                    chapters = sortedChapters
                )
            )
        }
    }

    suspend fun onChapterDownloaded(chapterMeta: ChapterMeta, chapterFile: File) {
        novelMutex.withLock {
            val novel = getNovel(chapterMeta.novelId)
            if (novel == null) {
                Log.w(TAG, "downloadChapter: Novel to update is null")
                return
            }
            setNovel(
                novel.copy(
                    chapterFiles = novel.chapterFiles + Pair(chapterMeta.id, chapterFile)
                )
            )
        }

        if (openNovelId == chapterMeta.novelId) {
            _openNovel.value = (
                    _openNovel.value + Pair(
                        chapterMeta.id,
                        OpenChapter(
                            chapterMeta = chapterMeta,
                            content = chapterFile.readText()
                        )
                    )
            ).toSortedMap()
        }
    }

    private suspend fun downloadCover(novelMeta: NovelMeta) = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadCover ${novelMeta.title}")
        if (novelMeta.coverUrl.isNullOrBlank()) {
            Log.w(TAG, "downloadCover: Cover URL is null")
            return@withContext
        }

        val bytes = runCatching {
            val coverUrl = URL(novelMeta.coverUrl)
            coverUrl.openStream()
                .buffered()
                .readBytes()
        }.onFailure { e ->
            Log.e(TAG, "downloadCover", e)
        }.getOrNull()
            ?: return@withContext

        val fileExtension = novelMeta.coverUrl.substring(
            novelMeta.coverUrl.lastIndexOf('.')
        )

        val coverFile = yomiFiles.writeNovelCover(novelMeta, fileExtension, bytes)
        if (coverFile == null || !coverFile.exists()) {
            Log.w(TAG, "downloadCover: Cover failed to save")
            return@withContext
        }

        novelMutex.withLock {
            val novel = getNovel(novelMeta.id)
            if (novel == null) {
                Log.w(TAG, "downloadCover: Novel to update is null")
                return@withContext
            }
            setNovel(
                novel.copy(
                    coverFile = coverFile
                )
            )
        }
    }

    private suspend fun downloadChapter(chapterMeta: ChapterMeta) {
        val logId = "${chapterMeta.novelId},${chapterMeta.id}"
        if (yomiFiles.chapterExists(chapterMeta)) {
            Log.d(TAG, "downloadChapter: $logId exists")
            return
        }

        val novel = getNovel(chapterMeta.novelId)
        if (novel == null) {
            Log.d(TAG, "downloadChapter: Novel missing: $logId")
            return
        }
        Log.d(TAG, "downloadChapter: $logId")

        scheduler.schedule(
            DownloadChapterJob(
                novel = novel,
                chapterMeta = chapterMeta,
                appContainer = appContainer
            )
        )
    }

    private suspend fun addNovel(novelInfo: NovelInfo): Novel? {
        val title = novelInfo.title
        if (title.isNullOrBlank()) {
            Log.e(TAG, "addNovel: failed. $novelInfo")
            return null
        }
        var novelMeta = NovelMeta(
            title = title,
            url = novelInfo.url,
            author = novelInfo.author,
            coverUrl = novelInfo.coverUrl
        )
        novelMeta = novelMeta.copy(
            id = novelDao.upsertNovelMeta(novelMeta)
        )

        val novel = Novel(
            metadata = novelMeta,
        )
        novelMutex.withLock {
            setNovel(novel)
        }
        Log.d(TAG, "Added novel")

        ioScope.launch {
            downloadCover(novelMeta)
        }

        scheduler.schedule(
            DownloadChapterInfoJob(
                novel = novel,
                crawler = crawler,
                novelRepository = this
            )
        )

        return novel
    }

    fun openNovel(novelId: Long) {
        ioScope.launch {
            val allChapters = TreeMap<Long, OpenChapter>()
            val novel = getNovel(novelId)
            if (novel == null) {
                Log.d(TAG, "openNovel: Novel shouldn't be null: $novelId")
                _openNovel.value = allChapters
                openNovelId = null
                return@launch
            }
            openNovelId = novelId
            for (chapterMeta in novel.chapters) {
                val chapterFile = novel.chapterFiles[chapterMeta.id]
                if (chapterFile == null || !chapterFile.exists()) {
                    allChapters[chapterMeta.id] = OpenChapter(
                        chapterMeta = chapterMeta,
                        content = "Isn't downloaded yet."
                    )
                    continue
                }

                val text = chapterFile.readText()
                if (text.isBlank()) {
                    allChapters[chapterMeta.id] = OpenChapter(
                        chapterMeta = chapterMeta,
                        content = "Chapter file is there, but it's blank ☹️"
                    )
                } else {
                    allChapters[chapterMeta.id] = OpenChapter(
                        chapterMeta = chapterMeta,
                        content = text
                    )
                }
            }
            _openNovel.value = allChapters
        }
    }

    fun searchForNewNovels(query: String) {
        _searchInProgress.value = true
        _searchResults.value = mutableSetOf()

        ioScope.launch {
            val novelInfoList = crawler.searchNovels(query)
            searchMutex.withLock {
                val result: Set<Long> = novelInfoList.mapNotNull { novelInfo ->
                    val existingNovel = getNovel(novelInfo.url)
                    if (existingNovel != null) {
                        return@mapNotNull existingNovel.metadata.id
                    }

                    val newNovel = addNovel(
                        novelInfo = novelInfo
                    ) ?: return@mapNotNull null

                    // It's unlikely the search info contains everything we want, so schedule to do
                    // a full novel info grab
                    Log.d(
                        TAG,
                        "searchForNewNovels: Scheduling download for ${newNovel.metadata.title}"
                    )
                    scheduler.schedule(
                        DownloadNovelInfoJob(
                            crawler = crawler,
                            novel = newNovel,
                            novelRepository = this@NovelRepository
                        )
                    )
                    newNovel.metadata.id
                }.toHashSet()
                scheduler.setActiveSearch(result)
                _searchResults.value = result
            }
            _searchInProgress.value = false
        }
    }

    fun addToLibrary(novelId: Long) {
        defaultScope.launch {
            novelMutex.withLock {
                val novel = getNovel(novelId)
                    ?: return@launch

                if (novel.metadata.inLibrary) {
                    Log.d(TAG, "addToLibrary: Already in library")
                    return@launch
                }

                val novelMeta = novel.metadata.copy(
                    inLibrary = true
                )
                novelDao.upsertNovelMeta(novelMeta)
                setNovel(
                    novel.copy(
                        metadata = novelMeta
                    )
                )
                Log.d(TAG, "addToLibrary done: ${novelMeta.title}")

                novel.chapters.forEach { chapterMeta ->
                    downloadChapter(chapterMeta)
                }
            }
        }
    }

    fun removeFromLibrary(novelId: Long) {
        defaultScope.launch {
            novelMutex.withLock {
                val novel = getNovel(novelId)
                    ?: return@launch

                if (!novel.metadata.inLibrary) {
                    Log.d(TAG, "removeFromLibrary: Already not in library")
                    return@launch
                }

                val novelMeta = novel.metadata.copy(
                    inLibrary = false
                )
                novelDao.upsertNovelMeta(novelMeta)
                setNovel(
                    novel.copy(
                        metadata = novelMeta
                    )
                )
                Log.d(TAG, "removeFromLibrary done: ${novelMeta.title}")
            }
        }
    }
}
