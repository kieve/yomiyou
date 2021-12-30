package ca.kieve.yomiyou.data.repository

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiyouApplication
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class NovelRepository(context: Context) {
    companion object {
        private val TAG: String = getTag()
    }

    private val appContainer = (context as YomiyouApplication).container
    private val novelDao = appContainer.database.novelDao()
    private val crawler = appContainer.crawler

    private val allNovels: MutableMap<Long, NovelMeta> = hashMapOf()
    private val allChapters: MutableMap<Long, MutableList<ChapterMeta>> = hashMapOf()

    init {
        runBlocking {
            allNovels.putAll(novelDao.getAllNovelMeta().map { it.id to it })

            val chapters = novelDao.getAllChapterMeta()
            for (chapter in chapters) {
                allChapters.computeIfAbsent(chapter.novelId) { arrayListOf() }
                    .add(chapter)
            }
            for (chapterList in allChapters.values) {
                chapterList.sortBy { it.id }
            }
            Log.d(TAG, "Loaded novels: $allNovels")
            Log.d(TAG, "loaded chapters: $allChapters")
        }
    }

    suspend fun crawlNovelInfo(url: String) = withContext(Dispatchers.IO) {
        // First, see if we already have this novel.
        for (novel in allNovels.values) {
            if (novel.url == url) {
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
        allNovels[novelMeta.id] = novelMeta

        val chapterMetaList = mutableListOf<ChapterMeta>()
        for (chapterInfo in novelInfo.chapters) {
            chapterMetaList.add(ChapterMeta(
                chapterInfo.id,
                novelId,
                chapterInfo.title,
                chapterInfo.url
            ))
        }
        novelDao.upsertChapterMeta(chapterMetaList)
        for (chapterMeta in chapterMetaList) {
            allChapters.computeIfAbsent(chapterMeta.novelId) { arrayListOf() }
                .add(chapterMeta)
        }

        Log.d(TAG, "Added novel and saved chapters to DB")
    }
}
