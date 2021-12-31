package ca.kieve.yomiyou.data.repository

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
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
import kotlinx.coroutines.withContext
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

    private val _novels: MutableStateFlow<MutableMap<Long, Novel>> = MutableStateFlow(hashMapOf())
    val novels: StateFlow<Map<Long, Novel>> = _novels

    init {
        ioScope.launch {
            val novelMetas = novelDao.getAllNovelMeta()
            val chapters = novelDao.getAllChapterMeta()

            val chapterMap = hashMapOf<Long, MutableList<ChapterMeta>>()
            for (chapter in chapters) {
                chapterMap.computeIfAbsent(chapter.novelId) { arrayListOf() }
                    .add(chapter)
            }
            for (chapterList in chapterMap.values) {
                chapterList.sortBy { it.id }
            }

            val mappedNovels = hashMapOf<Long, Novel>()
            for (novelMeta in novelMetas) {
                mappedNovels[novelMeta.id] = Novel(
                    metadata = novelMeta,
                    coverFile = yomiFiles.getNovelCoverFile(novelMeta),
                    chapterMap[novelMeta.id] ?: emptyList()
                )
            }

            _novels.value = mappedNovels
            Log.d(TAG, "Loaded novels: ${novels.value}")

            for (novelMeta in novelMetas) {
                downloadCoverIfMissing(novelMeta)
            }
        }
    }

    fun getNovel(id: Long): Novel? {
        return novels.value[id]
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
                it.id,
                novelId,
                it.title,
                it.url
            )
        }
        novelDao.upsertChapterMeta(chapterList)

        val result = _novels.value.toMutableMap()
        val novel = Novel(
            metadata = novelMeta,
            coverFile = null,
            chapterList)
        result[novelId] = novel
        _novels.value = result

        Log.d(TAG, "Added novel and saved chapters to DB")

        downloadCover(novelMeta)
    }

    private suspend fun downloadCoverIfMissing(novelMeta: NovelMeta) {
        if (yomiFiles.novelCoverExists(novelMeta)) {
            Log.d(TAG, "downloadCoverIfMissing: Cover exists")
            return
        }
        downloadCover(novelMeta)
    }

    private suspend fun downloadCover(novelMeta: NovelMeta) = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadCover")
        if (novelMeta.coverUrl.isNullOrBlank()) {
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
            return@withContext
        }

        val novel = _novels.value[novelMeta.id]
        if (novel != null) {
            _novels.value[novelMeta.id] = novel.copy(
                coverFile = coverFile
            )
        }
    }
}
