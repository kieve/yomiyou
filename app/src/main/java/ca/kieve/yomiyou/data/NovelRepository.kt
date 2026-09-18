package ca.kieve.yomiyou.data

import android.util.Log
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.ChapterListPage
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.database.YomiDatabase
import ca.kieve.yomiyou.data.database.model.Chapter
import ca.kieve.yomiyou.data.database.model.LibraryNovel
import ca.kieve.yomiyou.data.database.model.Novel
import ca.kieve.yomiyou.data.model.OpenChapter
import ca.kieve.yomiyou.data.scheduler.DownloadChapterJob
import ca.kieve.yomiyou.data.scheduler.DownloadChapterListJob
import ca.kieve.yomiyou.data.scheduler.DownloadNovelInfoJob
import ca.kieve.yomiyou.data.scheduler.NovelScheduler
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NovelRepository(
    database: YomiDatabase,
    private val files: YomiFiles,
    private val crawler: Crawler,
    private val scheduler: NovelScheduler
) {
    companion object {
        private val TAG = getTag()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val novelDao = database.novelDao()
    private val chapterDao = database.chapterDao()

    val library: Flow<List<LibraryNovel>> = novelDao.library()
    fun novel(id: Long): Flow<Novel?> = novelDao.novel(id)
    fun chapters(novelId: Long): Flow<List<Chapter>> = chapterDao.chapters(novelId)

    fun openChapter(novelId: Long, position: Int): Flow<OpenChapter?> =
        chapterDao.chapter(novelId, position).map { chapter ->
            chapter?.let { OpenChapter(it, files.readChapter(it)) }
        }

    private val _searchResults = MutableStateFlow<List<Novel>>(emptyList())
    val searchResults: StateFlow<List<Novel>> = _searchResults.asStateFlow()
    private val _searchInProgress = MutableStateFlow(false)
    val searchInProgress: StateFlow<Boolean> = _searchInProgress.asStateFlow()

    init {
        // Resume whatever was left unfinished last time.
        scope.launch {
            for (novel in novelDao.libraryNow()) {
                sync(novel)
                resumeChapterList(novel)
                downloadMissing(novel)
            }
        }
    }

    private fun sync(novel: Novel) {
        scheduler.setNovelState(novel.id, novel.inLibrary, novel.lastOpened)
    }

    fun search(query: String) {
        _searchInProgress.value = true
        scope.launch {
            val results = crawler.searchNovels(query).mapNotNull { info ->
                val title = info.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                novelDao.getByUrl(info.url) ?: run {
                    val novel = Novel(url = info.url, title = title, coverUrl = info.coverUrl)
                    novel.copy(id = novelDao.upsert(novel))
                }
            }
            _searchResults.value = results
            _searchInProgress.value = false
        }
    }

    /** The user opened this novel's screen: fetch details and (the rest of) its chapters. */
    fun loadNovelDetails(novelId: Long) {
        scope.launch {
            novelDao.setLastOpened(novelId, System.currentTimeMillis())
            val novel = novelDao.get(novelId) ?: return@launch
            sync(novel)
            scheduler.schedule(DownloadNovelInfoJob(novel, crawler, this@NovelRepository))
            resumeChapterList(novel)
        }
    }

    /** The user is reading [position]: make sure it and its neighbours come first. */
    fun setReading(novelId: Long, position: Int) {
        scheduler.setReading(novelId, position)
        scope.launch {
            scheduler.schedule(chapterDao.range(novelId, position - 1, position + 1)
                .filterNot { it.downloaded }
                .map { DownloadChapterJob(it, crawler, this@NovelRepository) })
        }
    }

    fun setInLibrary(novelId: Long, inLibrary: Boolean) {
        scope.launch {
            novelDao.setInLibrary(novelId, inLibrary)
            val novel = novelDao.get(novelId) ?: return@launch
            sync(novel)
            if (inLibrary) {
                resumeChapterList(novel)
                downloadMissing(novel)
            }
        }
    }

    /** Continues an unfinished chapter list from its last fetched page. */
    private fun resumeChapterList(novel: Novel) {
        if (novel.listComplete) return
        scheduler.schedule(DownloadChapterListJob(novel, crawler, this))
    }

    private suspend fun downloadMissing(novel: Novel) {
        scheduler.schedule(chapterDao.notDownloaded(novel.id).map { chapter ->
            DownloadChapterJob(chapter, crawler, this)
        })
    }

    suspend fun isDownloaded(chapter: Chapter): Boolean {
        return chapterDao.get(chapter.novelId, chapter.url)?.downloaded == true
    }

    suspend fun onNovelInfo(novelId: Long, info: NovelInfo?) {
        info ?: return
        val novel = novelDao.get(novelId) ?: return
        novelDao.upsert(novel.copy(
            title = info.title?.takeIf { it.isNotBlank() } ?: novel.title,
            author = info.author ?: novel.author,
            coverUrl = info.coverUrl ?: novel.coverUrl
        ))
    }

    suspend fun onChapterListPage(novelId: Long, page: ChapterListPage, firstPosition: Int) {
        chapterDao.upsert(mergeChapters(novelId, chapterDao.chaptersNow(novelId), page.chapters))
        novelDao.setListPage(novelId, page.page, firstPosition, complete = page.nextPage == null)
    }

    suspend fun onChapterListComplete(novelId: Long) {
        val novel = novelDao.get(novelId) ?: return
        Log.d(TAG, "chapter list done: ${novel.title}: ${chapterDao.chaptersNow(novelId).size} chapters")
        if (novel.inLibrary) {
            downloadMissing(novel)
        }
    }

    suspend fun onChapterDownloaded(chapter: Chapter, markdown: String) {
        if (files.writeChapter(chapter, markdown)) {
            chapterDao.markDownloaded(chapter.novelId, chapter.url)
        }
    }
}

/** Crawled positions/titles win; the `downloaded` flag of a known URL survives. */
fun mergeChapters(novelId: Long, existing: List<Chapter>, crawled: List<ChapterInfo>): List<Chapter> {
    val downloaded = existing.filter { it.downloaded }.map { it.url }.toSet()
    return crawled.map { info ->
        Chapter(
            novelId = novelId,
            url = info.url,
            position = info.position,
            title = info.title,
            downloaded = info.url in downloaded
        )
    }
}
