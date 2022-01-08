package ca.kieve.yomiyou.data.repository

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
import ca.kieve.yomiyou.copydown.CopyDown
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.data.model.Novel
import ca.kieve.yomiyou.data.model.OpenChapter
import ca.kieve.yomiyou.data.model.SearchResult
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
import java.lang.IllegalStateException
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class NovelRepository(context: Context) {
    companion object {
        private val TAG: String = getTag()
    }

    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)

    private val appContainer = (context as YomiApplication).container
    private val yomiFiles = appContainer.files
    private val novelDao = appContainer.database.novelDao()
    private val crawler = appContainer.crawler

    private val memoryNovelId = AtomicLong(-1)
    private val novelMutex = Mutex()
    private val _novels: MutableStateFlow<MutableMap<Long, Novel>> = MutableStateFlow(hashMapOf())
    val novels: StateFlow<Map<Long, Novel>> = _novels

    private val _openNovel: MutableStateFlow<List<OpenChapter>> = MutableStateFlow(listOf())
    val openNovel: StateFlow<List<OpenChapter>> = _openNovel

    private val searchMutex = Mutex()
    private val _searchResults: MutableStateFlow<Map<Long, SearchResult>> =
        MutableStateFlow(hashMapOf())
    val searchResults: StateFlow<Map<Long, SearchResult>> = _searchResults
    private val _searchInProgress: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val searchInProgress: StateFlow<Boolean> = _searchInProgress

    init {
        ioScope.launch {
            loadNovels()
        }
    }

    fun getNovel(id: Long): Novel? {
        return _novels.value[id]
    }

    fun getChapter(novelId: Long, chapterId: Long): ChapterMeta? {
        // TODO: Maybe make a map for this
        val novel = getNovel(novelId) ?: return null
        for (chapterMeta in novel.chapters) {
            if (chapterMeta.id == chapterId) {
                return chapterMeta
            }
        }
        return null
    }

    private fun setNovel(novel: Novel) {
        if (!novelMutex.isLocked) {
            throw IllegalStateException("Novel mutex lock must be held to update.")
        }
        _novels.value = (_novels.value + Pair(novel.metadata.id, novel)).toMutableMap()
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
            setNovel(novel.copy(
                coverFile = coverFile
            ))
        }
    }

    private suspend fun downloadSearchCover(searchResult: SearchResult) =
        withContext(Dispatchers.IO)
    {
        Log.d(TAG, "downloadSearchCover ${searchResult.novelInfo.title}")
        val url = searchResult.novelInfo.coverUrl
        if (url.isNullOrBlank()) {
            Log.w(TAG, "downloadSearchCover: Cover URL is null")
            return@withContext
        }

        val bytes = runCatching {
            val coverUrl = URL(url)
            coverUrl.openStream()
                .buffered()
                .readBytes()
        }.onFailure { e ->
            Log.e(TAG, "downloadSearchCover", e)
        }.getOrNull()
            ?: return@withContext

        val fileExtension = url.substring(
            url.lastIndexOf('.')
        )

        val coverFile = yomiFiles.writeSearchCover(searchResult.tempId, fileExtension, bytes)
        if (coverFile == null || !coverFile.exists()) {
            Log.w(TAG, "downloadCover: Cover failed to save")
            return@withContext
        }

        searchMutex.withLock {
            val result = _searchResults.value[searchResult.tempId]
            if (result == null) {
                Log.w(TAG, "downloadSearchCover: result to update is null")
                return@withContext
            }
            val updatedSearchResult = result.copy(
                coverFile = coverFile
            )
            _searchResults.value = (_searchResults.value
                    + Pair(searchResult.tempId, updatedSearchResult)
                    ).toMutableMap()
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

    // TODO: I think this can be removed, as it will be the search workflow that add it.
    suspend fun downloadNovelInfo(url: String) = withContext(Dispatchers.IO) {
        // First, see if we already have this novel.
//        for (novel in novels.value.values) {
//            if (novel.metadata.url == url) {
//                // TODO update chapter list
//                Log.d(TAG, "crawlNovelInfo: Already have this novel. Updates not implemented")
//                return@withContext
//            }
//        }
//
//        val novelInfo = crawler.getNovelInfo(url)
//        Log.d(TAG, "Crawled novel: $novelInfo")
//        if (novelInfo == null) {
//            return@withContext
//        }
//
//        addNovel(novelInfo)
    }

    private suspend fun downloadChapterInfo(novelMeta: NovelMeta) = withContext(Dispatchers.IO) {
        val chapterInfo = crawler.getChapterInfo(novelMeta.url)
        val chapterList = chapterInfo.map { info ->
            ChapterMeta(
                id = info.id,
                novelId = novelMeta.id,
                title = info.title,
                url = info.url
            )
        }
        novelMutex.withLock {
            val novel = getNovel(novelMeta.id) ?: return@withContext
            setNovel(
                novel.copy(
                    chapters = chapterList
                )
            )
        }
    }

    private suspend fun addNovel(id: Long, novelInfo: NovelInfo, coverFile: File? = null): Novel? {
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

        // TODO: Add to library when the user clicks "Add to library"
        // val novelId = novelDao.upsertNovelMeta(novelMeta)
        // novelDao.upsertChapterMeta(chapterList)

        val novel = Novel(
            inLibrary = false,
            metadata = novelMeta,
            coverFile = coverFile
        )
        novelMutex.withLock {
            setNovel(novel)
        }
        Log.d(TAG, "Added novel")

        if (coverFile == null) {
            ioScope.launch {
                downloadCover(novelMeta)
            }
        }
        ioScope.launch {
            downloadChapterInfo(novelMeta)
        }
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
        _searchResults.value = mutableMapOf()
        ioScope.launch {
            yomiFiles.clearSearchCache()
        }
        ioScope.launch {
            val novelInfoList = crawler.searchNovels(query)
            searchMutex.withLock {
                val result: Map<Long, SearchResult> = novelInfoList.map { novelInfo ->
                    var existingNovel: Novel? = null
                    for (novel in _novels.value.values) {
                        if (novel.metadata.url == novelInfo.url) {
                            // It's in the library already
                            existingNovel = novel
                            break
                        }
                    }
                    val tempId = existingNovel?.metadata?.id
                        ?: memoryNovelId.getAndDecrement()

                    val searchResult = SearchResult(
                        tempId = tempId,
                        novelInfo = novelInfo,
                        coverFile = existingNovel?.coverFile
                    )
                    if (existingNovel == null) {
                        launch {
                            downloadSearchCover(searchResult)
                            val updated = _searchResults.value[searchResult.tempId]
                            addNovel(
                                id = tempId,
                                novelInfo = novelInfo,
                                coverFile = updated?.coverFile
                            )
                        }
                    }
                    tempId to searchResult
                }.toMap()
                _searchResults.value = result
            }
            _searchInProgress.value = false
        }
    }
}
