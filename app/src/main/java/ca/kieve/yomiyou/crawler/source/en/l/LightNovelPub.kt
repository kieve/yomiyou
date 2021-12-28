package ca.kieve.yomiyou.crawler.source.en.l

import android.util.Log
import ca.kieve.yomiyou.crawler.ChapterInfo
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.NovelInfo

class LightNovelPub(
    homeUrl: String,
    novelUrl: String)
    : Crawler(homeUrl, novelUrl)
{
    companion object {
        // val TAG: String = LightNovelPub::class.java.simpleName
        private const val TAG = "FUCK-LightNovelPub"
        const val NOVEL_SEARCH_FORMAT = "%s/search?title=%s"
        const val CHAPTER_LIST_FORMAT = "%s/chapters/page-%d"
        val PAGE_NUM_REGEX = ".*/page-(\\d+).*".toRegex()
    }

    override val baseUrls: List<String> = listOf(
            "https://www.lightnovelpub.com/",
            "https://www.lightnovelworld.com/"
    )

    init {
        badCss.addAll(listOf(".adsbox", "p[class]", ".ad", "p:nth-child(1) > strong"))
    }

    override suspend fun searchNovel(query: String): List<Map<String, String>> {
        val urlSafeQuery = query.lowercase().replace(' ', '+')
        val soup = getSoup(String.format(NOVEL_SEARCH_FORMAT, homeUrl, urlSafeQuery))

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

    override suspend fun readNovelInfo(): NovelInfo {
        Log.d(TAG, "readNovelInfo: Visiting $novelUrl")
        var soup = getSoup(novelUrl)
        val possibleTitle = soup.selectFirst(".novel-info .novel-title")
        val novelTitle = possibleTitle?.text()?.trim() ?: ""
        Log.d(TAG, "readNovelInfo: Novel title: $novelTitle")

        val possibleImage = soup.selectFirst(".glass-background img")
        val novelCover =
                if (possibleImage != null)
                    absoluteUrl(possibleImage.attr("src"))
                else ""
        Log.d(TAG, "readNovelInfo: Novel cover: $novelCover")

        val possibleAuthor = soup.selectFirst(".author a[href*=\"/author/\"]")
        val novelAuthor = possibleAuthor?.select("span")?.text() ?: ""
        Log.d(TAG, "readNovelInfo: Novel author: $novelAuthor")

        Log.d(TAG, "readNovelInfo: Getting chapters...")

        soup = getSoup(String.format(CHAPTER_LIST_FORMAT, novelUrl, 1))
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
            getSoup(String.format(CHAPTER_LIST_FORMAT, novelUrl, it))
        }

        for (subSoup in soups) {
            for (a in subSoup.select("ul.chapter-list li a")) {
                chapters.add(ChapterInfo(
                    chapters.size + 1,
                    a.select(".chapter-title").text(),
                    absoluteUrl(a.attr("href"))
                ))
            }
        }

        val result = NovelInfo(novelTitle, novelAuthor, novelCover, false)
        Log.d(TAG, "$result")
        Log.d(TAG, "$chapters")
        return result;
    }

    override suspend fun downloadChapterBody(chapter: ChapterInfo): String {
        val soup = getSoup(chapter.url)
        val body = soup.selectFirst("#chapter-container")
                ?: return ""
        return extractContents(body)
    }
}
