package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.crawler.model.NovelInfo

interface SourceCrawler {
    val baseUrls: List<String>

    fun initCrawler(crawler: Crawler)

    suspend fun searchNovel(crawler: Crawler, query: String): List<Map<String, String>>
    suspend fun readNovelInfo(crawler: Crawler): NovelInfo?
    suspend fun downloadChapterBody(crawler: Crawler, url: String): String
}
