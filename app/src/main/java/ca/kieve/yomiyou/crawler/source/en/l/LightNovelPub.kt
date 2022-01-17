package ca.kieve.yomiyou.crawler.source.en.l

import android.util.Log
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.SourceCrawler
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.ChapterListInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.util.getTag
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

class LightNovelPub : SourceCrawler {
    companion object {
        private val TAG = getTag()

        private const val SEARCH_FORMAT = "lnwsearchlive?inputContent=%s"
        private const val CHAPTER_LIST_FORMAT = "%s/chapters/page-%d"
        private val PAGE_NUM_REGEX = """.*/page-(\d+).*""".toRegex()
        private val URL_SUFFIX_REGEX = """(\d+)$""".toRegex()

        private val BAD_CSS = setOf(
            ".adsbox",
            "p[class]",
            ".ad",
            "p:nth-child(1) > strong"
        )
        private val BLACKLIST = setOf(
            ".*lightnovelpub.com.*".toRegex()
        )
    }

    private data class SearchResults(
        @JsonProperty("\$id")
        val id: String,
        val success: Boolean,
        @JsonProperty("resultview")
        val html: String
    )

    override val baseUrls: List<String> = listOf(
            "https://www.lightnovelpub.com/",
            "https://www.lightnovelworld.com/"
    )

    override fun areSameNovel(aUrl: String, bUrl: String): Boolean {
        val aFixed = aUrl.replace(URL_SUFFIX_REGEX, "")
        val bFixed = bUrl.replace(URL_SUFFIX_REGEX, "")
        return aFixed == bFixed
    }

    override fun initCrawler(crawler: Crawler) {
        crawler.currentFilter = Crawler.DEFAULT_FILTER.copy(
            badCss = Crawler.DEFAULT_FILTER.badCss + BAD_CSS,
            blacklistRegexes = Crawler.DEFAULT_FILTER.blacklistRegexes + BLACKLIST
        )
    }

    override suspend fun search(crawler: Crawler, query: String): List<NovelInfo> {
        val scraper = crawler.scraper

        val url = baseUrls[0] + SEARCH_FORMAT.format(URLEncoder.encode(query, UTF_8.name()))
        val loadedPage = scraper.loadPage(url)
        if (loadedPage == null) {
            Log.d(TAG, "search: Failed to load search page.")
            // TODO: Report error
            return emptyList()
        }

        val loadedSoup = Jsoup.parse(loadedPage)
        val json = loadedSoup.select("pre").text()
        val mapper = jacksonObjectMapper()
        val searchResults: SearchResults = mapper.readValue(json)

        if (!searchResults.success) {
            Log.d(TAG, "search: search results was not successful")
            // TODO: Communicate this
            return emptyList()
        }

        val html = searchResults.html
        val soup = Jsoup.parse(html, url)

        val result: MutableList<NovelInfo> = mutableListOf()
        for (a in soup.select(".novel-list .novel-item a")) {
            val novelUrl = baseUrls[0] + a.attr("href").substring(1)
            val title = a.attr("title").trim()

            val coverImg = a.selectFirst("img")
            val coverUrl = coverImg?.attr("src")?.replace(
                regex = "\\d+x\\d+".toRegex(),
                replacement = "300x400")
            result.add(NovelInfo(
                url = novelUrl,
                title = title,
                coverUrl = coverUrl
            ))
        }

        return result
    }

    override suspend fun getInfo(crawler: Crawler): NovelInfo? {
        Log.d(TAG, "getInfo: ${crawler.currentNovelUrl}")
        val novelUrl = crawler.currentNovelUrl ?: return null

        val result = NovelInfo(novelUrl)

        val soup = crawler.getSoup(novelUrl)
        val possibleTitle = soup.selectFirst(".novel-info .novel-title")
        result.title = possibleTitle?.text()?.trim() ?: ""
        Log.d(TAG, "getInfo: Novel title: ${result.title}")

        val possibleImage = soup.selectFirst(".glass-background img")
        result.coverUrl =
                if (possibleImage != null)
                    crawler.absoluteUrl(possibleImage.attr("src"))
                else ""
        Log.d(TAG, "getInfo: Novel cover: ${result.coverUrl}")

        val possibleAuthor = soup.selectFirst(".author a[href*=\"/author/\"]")
        result.author = possibleAuthor?.select("span")?.text() ?: ""
        Log.d(TAG, "getInfo: Novel author: ${result.author}")

        return result;
    }

    override suspend fun getChapterListInfo(crawler: Crawler): ChapterListInfo {
        Log.d(TAG, "getChapterListInfo")
        val novelUrl = crawler.currentNovelUrl ?: return ChapterListInfo(totalPages = 0)

        val soup = crawler.getSoup(String.format(CHAPTER_LIST_FORMAT, novelUrl, 1))
        var lastPage = soup.selectFirst(".PagedList-skipToLast a")
        if (lastPage == null) {
            val paginationElements = soup.select(".pagination li")
            lastPage = if (paginationElements.size > 1) {
                paginationElements[paginationElements.size - 2]
            } else {
                paginationElements[0]
            }
        }
        val pageCount =
            if (lastPage != null)
                PAGE_NUM_REGEX.matchEntire(lastPage.toString())
                    ?.groups?.get(1)?.value?.toInt()
                    ?: 1
            else 1

        val firstPageChapters: MutableList<ChapterInfo> = arrayListOf()
        for (a in soup.select("ul.chapter-list li a")) {
            firstPageChapters.add(ChapterInfo(
                id = firstPageChapters.size + 1L,
                url = crawler.absoluteUrl(a.attr("href")),
                title = a.select(".chapter-title").text())
            )
        }

        return ChapterListInfo(
            firstPageChapters = firstPageChapters,
            totalPages = pageCount
        )
    }

    override suspend fun getChapterListPage(crawler: Crawler, page: Int): List<ChapterInfo> {
        Log.d(TAG, "getChapterListPage")
        val novelUrl = crawler.currentNovelUrl ?: return listOf()

        val soup = crawler.getSoup(String.format(CHAPTER_LIST_FORMAT, novelUrl, page))

        val result: MutableList<ChapterInfo> = arrayListOf()
        for (a in soup.select("ul.chapter-list li a")) {
            result.add(ChapterInfo(
                id = result.size + 1L,
                url = crawler.absoluteUrl(a.attr("href")),
                title = a.select(".chapter-title").text())
            )
        }

        return result
    }

    override suspend fun downloadChapter(crawler: Crawler, url: String): Element? {
        val soup = crawler.getSoup(url)
        val body = soup.selectFirst("#chapter-container")
        if (body == null) {
            Log.d(TAG, "downloadChapter: Body is null.")
            return null
        }
        return body
    }
}
