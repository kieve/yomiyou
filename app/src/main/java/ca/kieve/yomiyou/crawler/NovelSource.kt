package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.crawler.model.ChapterListPage
import ca.kieve.yomiyou.crawler.model.NovelInfo
import org.jsoup.nodes.Element

/**
 * One website. Stateless: every call gets the URL it needs, every URL returned is absolute.
 */
interface NovelSource {
    val baseUrl: String
    val filter: HtmlFilter

    fun owns(url: String): Boolean = url.startsWith(baseUrl)

    suspend fun search(query: String): List<NovelInfo>
    suspend fun novelInfo(novelUrl: String): NovelInfo?

    suspend fun chapterListPage(novelUrl: String, page: String?, firstPosition: Int): ChapterListPage?
    suspend fun rawChapterHtml(chapterUrl: String): Element?
}
