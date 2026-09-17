package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.copydown.CopyDown
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.ChapterListInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.crawler.source.en.l.LightNovelPub
import ca.kieve.yomiyou.scraper.Scraper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class HtmlFilter(
    val badTags: Set<String>,
    val badCss: Set<String>,
    val substitutions: Map<String, String>,
    val blacklistRegexes: Set<Regex>
)

class Crawler(val scraper: Scraper) {
    companion object {
        // Control and format characters (zero-width joiners, soft hyphens, BOMs...), minus the
        // newline, which is Markdown structure.
        private val NON_PRINTABLE_CHARS = Regex("[\\p{Cc}\\p{Cf}&&[^\\n]]")

        private val SOURCES = listOf<SourceCrawler>(
            LightNovelPub()
        )

        private val BAD_TAGS: Set<String> = hashSetOf(
            "noscript", "script", "style", "iframe", "ins", "header", "footer", "button", "input",
            "amp-auto-ads", "pirate", "figcaption", "address", "tfoot", "object", "video", "audio",
            "source", "nav", "output", "select", "textarea", "form", "map"
        )

        private val BAD_CSS: Set<String> = hashSetOf(
            ".code-block", ".adsbygoogle", ".sharedaddy", ".inline-ad-slot", ".ads-middle",
            ".jp-relatedposts", ".ezoic-adpicker-ad", ".ezoic-ad-adaptive", ".ezoic-ad",
            ".cb_p6_patreon_button", "a[href*=\"patreon.com\"]"
        )

        // Sites that use a double quote where an apostrophe belongs.
        private val SUBSTITUTIONS: Map<String, String> = hashMapOf(
            "\"s" to "'s",
            "“s" to "'s",
            "”s" to "'s"
        )

        val DEFAULT_FILTER = HtmlFilter(
            badTags = BAD_TAGS,
            badCss = BAD_CSS,
            substitutions = SUBSTITUTIONS,
            blacklistRegexes = setOf()
        )
    }

    private data class SourceMatch(
        val source: SourceCrawler,
        val baseUrl: String
    )

    /*
     * Current crawler state, resets when switching to a new source
     */

    private var currentSource: SourceCrawler? = null
    var currentNovelUrl: String? = null
        private set

    // Intended to be modified by SourceCrawlers
    var currentFilter: HtmlFilter = DEFAULT_FILTER

    private fun getSource(url: String): SourceMatch? {
        for (sourceCrawler in SOURCES) {
            for (baseUrl in sourceCrawler.baseUrls) {
                if (url.startsWith(baseUrl)) {
                    return SourceMatch(
                        source = sourceCrawler,
                        baseUrl = baseUrl
                    )
                }
            }
        }
        return null
    }

    private fun initSource(url: String): Boolean {
        // Reset our filter, since it might have been changed by the previous source
        currentFilter = DEFAULT_FILTER

        // Clear our source, we'll set a new one if we find one for this new URL
        currentSource = null
        currentNovelUrl = null

        val source = getSource(url) ?: return false
        source.source.initCrawler(this)
        currentSource = source.source
        return true
    }

    fun areSameNovel(aUrl: String, bUrl: String): Boolean {
        val aSource = getSource(aUrl)
        val bSource = getSource(bUrl)
        if (aSource == null && bSource == null) {
            // Fallback to a simple string comparison
            return aUrl == bUrl
        }
        if (aSource == null || bSource == null) {
            // We didn't find a source for one of the two, so assume they're different novels
            return false
        }
        if (aSource.source !== bSource.source) {
            // They aren't from the same source, so they can't be equal
            return false
        }

        // Defer to the source to decide
        return aSource.source.areSameNovel(aUrl, bUrl)
    }

    suspend fun getSoup(url: String, waitFor: String? = null): Document {
        val html = scraper.loadPage(url, waitFor)
        return Jsoup.parse(html ?: "", url)
    }

    suspend fun getNovelInfo(novelUrl: String): NovelInfo? {
        if (!initSource(novelUrl)) {
            return null
        }

        currentNovelUrl = novelUrl
        return currentSource?.getInfo(this)
    }

    suspend fun getChapterListInfo(novelUrl: String): ChapterListInfo {
        if (!initSource(novelUrl)) {
            return ChapterListInfo(totalPages = 0)
        }

        currentNovelUrl = novelUrl
        return currentSource?.getChapterListInfo(this) ?: ChapterListInfo(totalPages = 0)
    }

    suspend fun getChapterListPage(novelUrl: String, page: Int): List<ChapterInfo> {
        if (!initSource(novelUrl)) {
            return emptyList()
        }

        currentNovelUrl = novelUrl
        return currentSource?.getChapterListPage(this, page) ?: emptyList()
    }

    suspend fun downloadChapter(chapterUrl: String): Element? {
        if (!initSource(chapterUrl)) {
            return null
        }

        return currentSource?.downloadChapter(this, chapterUrl)
    }

    suspend fun searchNovels(query: String): List<NovelInfo> {
        return SOURCES[0].search(this, query)
    }

    private fun cleanContents(div: Element) {
        if (currentFilter.badCss.isNotEmpty()) {
            for (bad in div.select(currentFilter.badCss.joinToString(","))) {
                bad.remove()
            }
        }

        for (tag in div.allElements) {
            if (tag.normalName() == "br") {
                val next = tag.nextSibling()
                if (next != null && next.nodeName().lowercase() == "br") {
                    next.remove()
                }
            } else if (currentFilter.badTags.contains(tag.tagName())) {
                tag.remove()
            } else if (tag.attributesSize() > 0) {
                // Drop everything but image sources; links become plain text.
                val src = tag.attr("src")
                tag.clearAttributes()
                if (src.isNotEmpty()) {
                    tag.attr("src", src)
                }
            }
        }

        div.clearAttributes()
    }

    private fun isInBlacklist(line: String): Boolean {
        return currentFilter.blacklistRegexes.any { regex -> regex.containsMatchIn(line) }
    }

    /** Strips ads/junk from [tag] (in place) and converts what's left to Markdown. */
    fun toMarkdown(tag: Element): String {
        cleanContents(tag)
        var markdown = CopyDown().convert(tag.html())
            .replace(NON_PRINTABLE_CHARS, "")
        for ((key, value) in currentFilter.substitutions) {
            markdown = markdown.replace(key, value)
        }
        return markdown.lines()
            .filterNot { line -> isInBlacklist(line) }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
    }
}
