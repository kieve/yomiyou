package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.crawler.model.ChapterInfo
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

    suspend fun chapters(novelUrl: String): List<ChapterInfo>
    suspend fun rawChapterHtml(chapterUrl: String): Element?
}
