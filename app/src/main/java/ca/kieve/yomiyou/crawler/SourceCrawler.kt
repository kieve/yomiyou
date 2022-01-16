package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.ChapterListInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo

interface SourceCrawler {
    val baseUrls: List<String>

    fun areSameNovel(aUrl: String, bUrl: String): Boolean

    fun initCrawler(crawler: Crawler)

    suspend fun search(crawler: Crawler, query: String): List<NovelInfo>
    suspend fun getInfo(crawler: Crawler): NovelInfo?
    suspend fun getChapterListInfo(crawler: Crawler): ChapterListInfo
    suspend fun getChapterListPage(crawler: Crawler, page: Int): List<ChapterInfo>
    suspend fun downloadChapter(crawler: Crawler, url: String): String
}
