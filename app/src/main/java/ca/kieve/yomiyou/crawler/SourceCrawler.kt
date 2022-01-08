package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo

interface SourceCrawler {
    val baseUrls: List<String>

    fun initCrawler(crawler: Crawler)

    suspend fun searchNovel(crawler: Crawler, query: String): List<NovelInfo>
    suspend fun getNovelInfo(crawler: Crawler): NovelInfo?
    suspend fun getNovelChapterList(crawler: Crawler): List<ChapterInfo>
    suspend fun downloadChapterBody(crawler: Crawler, url: String): String
}
