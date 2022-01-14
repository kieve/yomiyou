package ca.kieve.yomiyou.data

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
import ca.kieve.yomiyou.copydown.CopyDown
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.data.model.Novel
import ca.kieve.yomiyou.data.model.OpenChapter
import ca.kieve.yomiyou.data.scheduler.DownloadChapterInfoJob
import ca.kieve.yomiyou.data.scheduler.DownloadNovelInfoJob
import ca.kieve.yomiyou.data.scheduler.NovelScheduler
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
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class NovelRepository(context: Context) {
    companion object {
        private val TAG: String = getTag()
    }

    private val ioScope = CoroutineScope(context = Job() + Dispatchers.IO)
    private val defaultScope = CoroutineScope(context = Job() + Dispatchers.Default)

    private val appContainer = (context as YomiApplication).container
    private val yomiFiles = appContainer.files
    private val novelDao = appContainer.database.novelDao()
    private val scheduler: NovelScheduler get() { return appContainer.novelScheduler }
    private val crawler = appContainer.crawler

    private val memoryNovelId = AtomicLong(-1)
    private val novelMutex = Mutex()
    private val _novels: MutableStateFlow<MutableMap<Long, Novel>> = MutableStateFlow(hashMapOf())
    val novels: StateFlow<Map<Long, Novel>> = _novels

    private val _openNovel: MutableStateFlow<List<OpenChapter>> = MutableStateFlow(listOf())
    val openNovel: StateFlow<List<OpenChapter>> = _openNovel

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
            if (novel.metadata.url == url) {
                return novel
            }
        }
        return null
    }

    private suspend fun loadNovels() = withContext(Dispatchers.IO) {
        val novelMetas = novelDao.getAllNovelMeta()
        for (novelMeta in novelMetas) {
            novelMutex.withLock {
                setNovel(Novel(
                    inLibrary = true,
                    metadata = novelMeta
                ))
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
                setNovel(novel.copy(
                    coverFile = novelCoverFile
                ))
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
            setNovel(novel.copy(
                chapters = chapters
            ))
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
                setNovel(novel.copy(
                    chapterFiles = novel.chapterFiles + Pair(chapterMeta.id, chapterFile)
                ))
            }
            return@withContext
        }
        downloadChapter(chapterMeta)
    }

    suspend fun onJobComplete(job: DownloadNovelInfoJob) {
        val result = job.result ?: return
        Log.d(TAG, "DownloadNovelInfoJob complete: ${result.title}")

        novelMutex.withLock {
            val novel = getNovel(job.novel.metadata.id)
            if (novel == null) {
                Log.d(TAG, "Novel is missing after Downloading Novel Info")
                return@withLock
            }

            var anyChange = false
            var metadata = novel.metadata

            val title = result.title
            if (title != null && metadata.title != title) {
                anyChange = true
                metadata = metadata.copy(
                    title = title
                )
            }

            val author = result.author
            if (author != null && metadata.author != author) {
                anyChange = true
                metadata = metadata.copy(
                    author = author
                )
            }

            var downloadCover = false
            val coverUrl = result.coverUrl
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
                setNovel(updatedNovel)
            }
            if (downloadCover) {
                downloadCover(updatedNovel.metadata)
            }
        }
    }

    suspend fun onJobComplete(job: DownloadChapterInfoJob) {
        val result = job.result ?: return
        Log.d(TAG, "DownloadChapterInfoJob complete: ${job.novel.metadata.title}")

        val chapterList = result.map { info ->
            ChapterMeta(
                id = info.id,
                novelId = job.novel.metadata.id,
                title = info.title,
                url = info.url
            )
        }
        novelMutex.withLock {
            val novel = getNovel(job.novel.metadata.id) ?: return
            setNovel(
                novel.copy(
                    chapters = chapterList
                )
            )
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

        val coverFile = if (novelMeta.id < 0) {
            // This is a search / temp novel, so store it appropriately
            yomiFiles.writeSearchCover(novelMeta.id, fileExtension, bytes)
        } else {
            yomiFiles.writeNovelCover(novelMeta, fileExtension, bytes)
        }

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
            setNovel(novel.copy(
                coverFile = coverFile
            ))
        }
    }

    private suspend fun downloadChapter(chapterMeta: ChapterMeta) = withContext(Dispatchers.IO) {
        val logId = "${chapterMeta.novelId},${chapterMeta.id}"
        if (yomiFiles.chapterExists(chapterMeta)) {
            Log.d(TAG, "downloadChapter: $logId exists")
            return@withContext
        }
        Log.d(TAG, "downloadChapter: $logId")

        val content = crawler.downloadChapter(chapterMeta.url)

        val markdown = if (content != null) {
            Log.d(TAG, "downloadChapter: $logId converting to MD")
            CopyDown().convert(content)
        } else {
            Log.d(TAG, "downloadChapter: $logId failed to download")
            return@withContext
        }
        val chapterFile = yomiFiles.writeChapter(
            chapterMeta = chapterMeta,
            content = markdown)
        if (chapterFile == null || !chapterFile.exists()) {
            Log.w(TAG, "downloadChapter: Chapter failed to save")
            return@withContext
        }

        novelMutex.withLock {
            val novel = getNovel(chapterMeta.novelId)
            if (novel == null) {
                Log.w(TAG, "downloadChapter: Novel to update is null")
                return@withContext
            }
            setNovel(novel.copy(
                chapterFiles = novel.chapterFiles + Pair(chapterMeta.id, chapterFile)
            ))
        }
    }

    private suspend fun addNovel(id: Long, novelInfo: NovelInfo): Novel? {
        val title = novelInfo.title
        if (title.isNullOrBlank()) {
            Log.e(TAG, "addNovel: failed. $novelInfo")
            return null
        }
        val novelMeta = NovelMeta(
            id = id,
            title = title,
            url = novelInfo.url,
            author = novelInfo.author,
            coverUrl = novelInfo.coverUrl
        )

        val novel = Novel(
            inLibrary = false,
            metadata = novelMeta,
        )
        novelMutex.withLock {
            setNovel(novel)
        }
        Log.d(TAG, "Added novel")

        ioScope.launch {
            downloadCover(novelMeta)
        }

        scheduler.schedule(DownloadChapterInfoJob(
            novel = novel,
            crawler = crawler
        ))

        return novel
    }

    fun openNovel(novelId: Long) {
        ioScope.launch {
            val allChapters = mutableListOf<OpenChapter>()
            val novel = getNovel(novelId)
            if (novel == null) {
                Log.d(TAG, "openNovel: Novel shouldn't be null: $novelId")
                _openNovel.value = allChapters
                return@launch
            }
            for (chapterMeta in novel.chapters) {
                val chapterFile = novel.chapterFiles[chapterMeta.id]
                if (chapterFile == null || !chapterFile.exists()) {
                    Log.d(TAG, "FUCK the chapter file isn't there? $chapterFile")
                    allChapters.add(
                        OpenChapter(
                            chapterMeta = chapterMeta,
                            content = "Isn't downloaded yet."
                        )
                    )
                    continue
                }

                val text = chapterFile.readText()
                if (text.isBlank()) {
                    Log.d(TAG, "FUCK It's BLANK: |$text|")
                    allChapters.add(
                        OpenChapter(
                            chapterMeta = chapterMeta,
                            content = "Chapter file is there, but it's blank ☹️"
                        )
                    )
                } else {
                    allChapters.add(
                        OpenChapter(
                            chapterMeta = chapterMeta,
                            content = text
                        )
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
            yomiFiles.clearSearchCache()
            val novelInfoList = crawler.searchNovels(query)
            searchMutex.withLock {
                val result: Set<Long> = novelInfoList.mapNotNull { novelInfo ->
                    val existingNovel = getNovel(novelInfo.url)
                    if (existingNovel != null) {
                        return@mapNotNull existingNovel.metadata.id
                    }

                    val tempId = memoryNovelId.getAndDecrement()
                    val newNovel = addNovel(
                        id = tempId,
                        novelInfo = novelInfo
                    ) ?: return@mapNotNull null

                    // It's unlikely the search info contains everything we want, so schedule to do
                    // a full novel info grab
                    Log.d(TAG, "searchForNewNovels: Scheduling download for ${newNovel.metadata.title}")
                    scheduler.schedule(DownloadNovelInfoJob(
                        crawler = crawler,
                        novel = newNovel
                    ))
                    tempId
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
                val novel = _novels.value[novelId]
                    ?: return@launch

                // Set metadata ID to generate DB id.
                var novelMeta = novel.metadata.copy(
                    id = 0
                )
                val newId = novelDao.upsertNovelMeta(novelMeta)
                novelMeta = novelMeta.copy(
                    id = newId
                )

                val chapters = novel.chapters
                val newChapters = chapters.map { chapterMeta ->
                    chapterMeta.copy(
                        novelId = newId
                    )
                }
                novelDao.upsertChapterMeta(newChapters)

                val newCover =
                    if (novel.coverFile == null)
                        null
                    else
                        yomiFiles.migrateSearchCover(
                            novelMeta = novelMeta,
                            coverFile = novel.coverFile
                        )

                setNovel(Novel(
                    inLibrary = true,
                    metadata = novelMeta,
                    chapters = newChapters,
                    coverFile = newCover
                ))
            }
        }
    }
}
