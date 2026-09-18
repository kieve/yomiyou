package ca.kieve.yomiyou.crawler.source

import android.util.Log
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.NovelSource
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.scraper.Scraper
import ca.kieve.yomiyou.scraper.soup
import ca.kieve.yomiyou.util.getTag
import org.jsoup.nodes.Element
import java.net.URLEncoder

class NovelPhoenix(private val scraper: Scraper) : NovelSource {
    companion object {
        private val TAG = getTag()
    }

    override val baseUrl = "https://novelphoenix.com"

    override val filter = Crawler.DEFAULT_FILTER.copy(
        badCss = Crawler.DEFAULT_FILTER.badCss + ".nf-ads"
    )

    override suspend fun search(query: String): List<NovelInfo> {
        val url = "$baseUrl/search?keyword=${URLEncoder.encode(query, "UTF-8")}&type=title"
        val soup = scraper.soup(url, waitFor = ".novel-list") ?: return emptyList()
        return soup.select("header + ul.novel-list .novel-item a").map { a ->
            NovelInfo(
                url = a.absUrl("href"),
                title = a.attr("title").trim(),
                coverUrl = a.selectFirst("img")?.imageUrl()
            )
        }
    }

    override suspend fun novelInfo(novelUrl: String): NovelInfo? {
        val soup = scraper.soup(novelUrl, waitFor = ".novel-info") ?: return null
        return NovelInfo(
            url = novelUrl,
            title = soup.selectFirst(".novel-info .novel-title")?.text()?.trim(),
            author = soup.selectFirst(".author a[href*=\"/author/\"]")?.text()?.trim(),
            coverUrl = soup.selectFirst(".fixed-img img, .glass-background img")?.imageUrl()
        )
    }

    private fun Element.imageUrl(): String? {
        return absUrl("data-src").ifEmpty { absUrl("src") }
            .takeUnless { it.isEmpty() || it.startsWith("data:") }
    }

    override suspend fun chapters(novelUrl: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        var pageUrl: String? = "$novelUrl/chapters"
        while (pageUrl != null) {
            val soup = scraper.soup(pageUrl, waitFor = "ul.chapter-list")
            if (soup == null) {
                Log.w(TAG, "chapters: failed to load $pageUrl, returning ${chapters.size} so far")
                break
            }
            for (a in soup.select("ul.chapter-list li a")) {
                chapters.add(ChapterInfo(
                    id = chapters.size + 1L,
                    url = a.absUrl("href"),
                    title = a.selectFirst(".chapter-title")?.text()?.trim() ?: a.attr("title")
                ))
            }
            pageUrl = soup.selectFirst("ul.pagination a[rel=next]")?.absUrl("href")
        }
        return chapters
    }

    override suspend fun rawChapterHtml(chapterUrl: String): Element? {
        return scraper.soup(chapterUrl, waitFor = "#content")?.selectFirst("#content")
    }
}
