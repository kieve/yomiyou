package ca.kieve.yomiyou.data.repository

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.data.model.Novel
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class NovelRepository(context: Context) {
    companion object {
        private val TAG: String = getTag()
    }

    private val appContainer = (context as YomiApplication).container
    private val novelDao = appContainer.database.novelDao()
    private val crawler = appContainer.crawler

    private val _novels: MutableStateFlow<MutableMap<Long, Novel>> = MutableStateFlow(hashMapOf())
    val novels: StateFlow<Map<Long, Novel>> = _novels

    init {
        runBlocking {
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
                    novelMeta,
                    chapterMap[novelMeta.id] ?: emptyList()
                )
            }

            _novels.value = mappedNovels
            Log.d(TAG, "Loaded novels: ${novels.value}")
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
        result[novelId] = Novel(novelMeta, chapterList)
        _novels.value = result

        Log.d(TAG, "Added novel and saved chapters to DB")
    }
}
