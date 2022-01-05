package ca.kieve.yomiyou.crawler.source.en.l

import android.util.Log
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.SourceCrawler
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.delay
import org.jsoup.Jsoup

class LightNovelPub : SourceCrawler {
    companion object {
        private val TAG = getTag()

        private const val CHAPTER_LIST_FORMAT = "%s/chapters/page-%d"
        private val PAGE_NUM_REGEX = """.*/page-(\d+).*""".toRegex()
        private val SEARCH_JS = """
            document.querySelector("#inputContent").value = "%s";
            document.querySelector("#inputContent")
                .dispatchEvent(new KeyboardEvent('keydown', { key: "a" }));
            document.querySelector("#inputContent")
                .dispatchEvent(new KeyboardEvent('keyup', { key: "a" }));
            """.trimIndent()

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

    override val baseUrls: List<String> = listOf(
            "https://www.lightnovelpub.com/",
            "https://www.lightnovelworld.com/"
    )

    override fun initCrawler(crawler: Crawler) {
        crawler.currentFilter = Crawler.DEFAULT_FILTER.copy(
            badCss = Crawler.DEFAULT_FILTER.badCss + BAD_CSS,
            blacklistRegexes = Crawler.DEFAULT_FILTER.blacklistRegexes + BLACKLIST
        )
    }

    override suspend fun searchNovel(crawler: Crawler, query: String): List<NovelInfo> {
        val scraper = crawler.scraper
        val url = baseUrls[0] + "search"
        if (scraper.loadPage(url) == null) {
            Log.d(TAG, "searchNovel: Failed to load search page.")
            // TODO: Report error
            return listOf()
        }

        // Obviously this is directly inserting user input to run as JS.
        // But, it probably doesn't effect us, the user, or the site we're scraping.
        // So, I don't care.
        scraper.executeJs(SEARCH_JS.format(query))
        delay(4000)
        val html = scraper.getCurrentPageHtml()
        if (html == null) {
            Log.d(TAG, "searchNovel: Failed to get current page HTML")
            return listOf()
        }
        val soup = Jsoup.parse(html, url)

        val result: MutableList<NovelInfo> = mutableListOf()
        for (a in soup.select("#novelListBase .novel-list .novel-item a")) {
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

    override suspend fun readNovelInfo(crawler: Crawler): NovelInfo? {
        Log.d(TAG, "readNovelInfo: ${crawler.currentNovelUrl}")
        val novelUrl = crawler.currentNovelUrl ?: return null

        val result = NovelInfo(novelUrl)

        var soup = crawler.getSoup(novelUrl)
        val possibleTitle = soup.selectFirst(".novel-info .novel-title")
        result.title = possibleTitle?.text()?.trim() ?: ""
        Log.d(TAG, "readNovelInfo: Novel title: ${result.title}")

        val possibleImage = soup.selectFirst(".glass-background img")
        result.coverUrl =
                if (possibleImage != null)
                    crawler.absoluteUrl(possibleImage.attr("src"))
                else ""
        Log.d(TAG, "readNovelInfo: Novel cover: ${result.coverUrl}")

        val possibleAuthor = soup.selectFirst(".author a[href*=\"/author/\"]")
        result.author = possibleAuthor?.select("span")?.text() ?: ""
        Log.d(TAG, "readNovelInfo: Novel author: ${result.author}")

        Log.d(TAG, "readNovelInfo: Getting chapters...")

        soup = crawler.getSoup(String.format(CHAPTER_LIST_FORMAT, novelUrl, 1))
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
                            ?: 0
                else 0
        val soups = listOf(soup) + (2 .. pageCount).map {
            crawler.getSoup(String.format(CHAPTER_LIST_FORMAT, novelUrl, it))
        }

        for (subSoup in soups) {
            for (a in subSoup.select("ul.chapter-list li a")) {
                result.chapters.add(ChapterInfo(
                    id = result.chapters.size + 1.toLong(),
                    url = crawler.absoluteUrl(a.attr("href")),
                    title = a.select(".chapter-title").text())
                )
            }
        }

        Log.d(TAG, "$result")
        return result;
    }

    override suspend fun downloadChapterBody(crawler: Crawler, url: String): String {
        val soup = crawler.getSoup(url)
        val body = soup.selectFirst("#chapter-container")
        if (body == null) {
            Log.d(TAG, "downloadChapterBody: Body is null.")
            return ""
        }
        return crawler.extractContents(body)
    }
}
