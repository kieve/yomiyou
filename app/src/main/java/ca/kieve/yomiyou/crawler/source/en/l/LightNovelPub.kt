package ca.kieve.yomiyou.crawler.source.en.l

import android.util.Log
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.SourceCrawler
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.util.getTag

class LightNovelPub : SourceCrawler {
    companion object {
        private val TAG = getTag()

        private const val NOVEL_SEARCH_FORMAT = "%s/search?title=%s"
        private const val CHAPTER_LIST_FORMAT = "%s/chapters/page-%d"
        private val PAGE_NUM_REGEX = ".*/page-(\\d+).*".toRegex()

        private val BAD_CSS = listOf(".adsbox", "p[class]", ".ad", "p:nth-child(1) > strong")
    }

    override val baseUrls: List<String> = listOf(
            "https://www.lightnovelpub.com/",
            "https://www.lightnovelworld.com/"
    )

    override fun initCrawler(crawler: Crawler) {
        crawler.currentFilter = Crawler.DEFAULT_FILTER.copy(
            badCss = Crawler.DEFAULT_FILTER.badCss + BAD_CSS
        )
    }

    override suspend fun searchNovel(crawler: Crawler, query: String): List<Map<String, String>> {
        val urlSafeQuery = query.lowercase().replace(' ', '+')
        val soup = crawler.getSoup(
            String.format(NOVEL_SEARCH_FORMAT, crawler.currentHomeUrl, urlSafeQuery))

        val result: MutableList<Map<String, String>> = arrayListOf()
        for (a in soup.select(".novel-list .novel-item a")) {
            val possibleInfo = a.selectFirst(".novel-stats")
            val info = possibleInfo?.text()?.trim()

            val resultMap: MutableMap<String, String> = hashMapOf()
            resultMap["title"] = a.attr("title").trim()
            resultMap["url"] = a.attr("href")
            resultMap["info"] = info ?: ""
        }

        return result
    }

    override suspend fun readNovelInfo(crawler: Crawler): NovelInfo? {
        Log.d(TAG, "readNovelInfo: Visiting ${crawler.currentNovelUrl}")

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

    override suspend fun downloadChapterBody(crawler: Crawler, chapter: ChapterInfo): String {
        val soup = crawler.getSoup(chapter.url)
        val body = soup.selectFirst("#chapter-container")
                ?: return ""
        return crawler.extractContents(body)
    }
}
