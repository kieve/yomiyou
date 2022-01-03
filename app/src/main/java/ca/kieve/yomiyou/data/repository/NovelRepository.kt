package ca.kieve.yomiyou.data.repository

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
import ca.kieve.yomiyou.copydown.CopyDown
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.data.model.Novel
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
import java.lang.IllegalStateException
import java.net.URL

class NovelRepository(context: Context) {
    companion object {
        private val TAG: String = getTag()
    }

    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)

    private val appContainer = (context as YomiApplication).container
    private val yomiFiles = appContainer.files
    private val novelDao = appContainer.database.novelDao()
    private val crawler = appContainer.crawler

    private val novelMutex = Mutex()
    private val _novels: MutableStateFlow<MutableMap<Long, Novel>> = MutableStateFlow(hashMapOf())
    val novels: StateFlow<Map<Long, Novel>> = _novels

    init {
        ioScope.launch {
            loadNovels()
        }
    }

    fun debugPrint(chapterMeta: ChapterMeta) {
        ioScope.launch {
            val novel = getNovel(chapterMeta.novelId)
            Log.d(TAG, novel?.chapterFiles?.get(chapterMeta)?.readText() ?: "It's null")
        }
    }

    fun getNovel(id: Long): Novel? {
        return _novels.value[id]
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
                    chapterFiles = novel.chapterFiles + Pair(chapterMeta, chapterFile)
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

    private suspend fun downloadChapter(chapterMeta: ChapterMeta) = withContext(Dispatchers.IO) {
        val logId = "${chapterMeta.novelId},${chapterMeta.id}"
        if (yomiFiles.chapterExists(chapterMeta)) {
            Log.d(TAG, "downloadChapter: $logId exists")
            return@withContext
        }
        Log.d(TAG, "downloadChapter: $logId")

        crawler.initCrawl(chapterMeta.url)
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
                chapterFiles = novel.chapterFiles + Pair(chapterMeta, chapterFile)
            ))
        }
    }

    suspend fun crawlNovelInfo(url: String) = withContext(Dispatchers.IO) {
        // First, see if we already have this novel.
        for (novel in novels.value.values) {
            if (novel.metadata.url == url) {
                // TODO update chapter list
                Log.d(TAG, "crawlNovelInfo: Already have this novel. Updates not implemented")
                return@withContext
            }
        }
        crawler.initCrawl(url)
        val novelInfo = crawler.crawl(url)
        Log.d(TAG, "Crawled novel: $novelInfo")
        if (novelInfo == null) {
            return@withContext
        }

        addNovel(novelInfo)
    }

    private suspend fun addNovel(novelInfo: NovelInfo) {
        val title = novelInfo.title
        if (title.isNullOrBlank()) {
            Log.e(TAG, "addNovel: failed. $novelInfo")
            return
        }
        val novelMeta = NovelMeta(
            title = title,
            url = novelInfo.url,
            author = novelInfo.author,
            coverUrl = novelInfo.coverUrl
        )
        val novelId = novelDao.upsertNovelMeta(novelMeta)

        val chapterList = novelInfo.chapters.map {
            ChapterMeta(
                id = it.id,
                novelId = novelId,
                title = it.title,
                url = it.url
            )
        }
        novelDao.upsertChapterMeta(chapterList)
        setNovel(Novel(
            metadata = novelMeta,
            coverFile = null,
            chapters = chapterList)
        )
        Log.d(TAG, "Added novel and saved chapters to DB")

        downloadCover(novelMeta)
    }
}
