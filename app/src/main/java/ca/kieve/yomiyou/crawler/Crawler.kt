package ca.kieve.yomiyou.crawler

import android.icu.text.UnicodeSet
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.crawler.source.en.l.LightNovelPub
import ca.kieve.yomiyou.scraper.Scraper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

data class HtmlFilter(
    val badTags: Set<String>,
    val badCss: Set<String>,
    val pBlockTags: Set<String>,
    val unchangedTags: Set<String>,
    val plainTextTags: Set<String>,
    val substitutions: Map<String, String>,
    val blacklistRegexes: Set<Regex>
)

class Crawler(val scraper: Scraper) {
    companion object {
        private const val LINE_SEP = "<br>"
        private val INVISIBLE_CHARS = UnicodeSet("[\\p{Cf}\\p{Cc}]")
        private val NON_PRINTABLE_CHARS = UnicodeSet(INVISIBLE_CHARS)
                .addAll(0x00, 0x20 - 1)
                .addAll(0x7f, 0xa0 - 1)

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

        private val P_BLOCK_TAGS: Set<String> = hashSetOf(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "main", "aside", "article", "div", "section"
        )

        private val UNCHANGED_TAGS: Set<String> = hashSetOf(
            "pre", "canvas", "img"
        )

        private val PLAIN_TEXT_TAGS: Set<String> = hashSetOf(
            "span", "a", "abbr", "acronym", "label", "time"
        )

        private val SUBSTITUTIONS: Map<String, String> = hashMapOf(
            "\"s" to "'s",
            "“s" to "'s",
            "”s" to "'s",
            "&" to "&amp;",
            "u003c" to "<",
            "u003e" to ">",
            "<" to "&lt;",
            ">" to "&gt;"
        )

        val DEFAULT_FILTER = HtmlFilter(
            badTags = BAD_TAGS,
            badCss = BAD_CSS,
            pBlockTags = P_BLOCK_TAGS,
            unchangedTags = UNCHANGED_TAGS,
            plainTextTags = PLAIN_TEXT_TAGS,
            substitutions = SUBSTITUTIONS,
            blacklistRegexes = setOf()
        )
    }

    var lastVisitedUrl: String? = null
        private set

    /*
     * Current crawler state, resets when switching to a new source
     */

    private var currentSource: SourceCrawler? = null
    var currentHomeUrl: String? = null
        private set
    var currentNovelUrl: String? = null
        private set

    // Intended to be modified by SourceCrawlers
    var currentFilter: HtmlFilter = DEFAULT_FILTER

    private fun initSource(url: String): Boolean {
        // Reset our filter, since it might have been changed by the previous source
        currentFilter = DEFAULT_FILTER

        // Clear our source, we'll set a new one if we find one for this new URL
        currentSource = null
        currentHomeUrl = null
        currentNovelUrl = null

        // TODO: Build a trie mapping the baseUrls to their sources. Then here, we can search for
        //       this URL in the trie and select the deepest matching source crawler.
        for (sourceCrawler in SOURCES) {
            for (baseUrl in sourceCrawler.baseUrls) {
                if (url.startsWith(baseUrl)) {
                    currentSource = sourceCrawler
                    currentHomeUrl = baseUrl
                    return true
                }
            }
        }

        return false
    }

    private fun verifySource(url: String): Boolean {
        val source = currentSource
        val homeUrl = currentHomeUrl

        return source != null
                && homeUrl != null
                && url.startsWith(homeUrl)
    }

    suspend fun getSoup(url: String): Document {
        val html = scraper.loadPage(url)
        return Jsoup.parse(html ?: "", url)
    }

    suspend fun getNovelInfo(novelUrl: String): NovelInfo? {
        if (!initSource(novelUrl)) {
            return null
        }

        currentNovelUrl = novelUrl
        return currentSource?.getNovelInfo(this)
    }

    suspend fun getChapterInfo(novelUrl: String): List<ChapterInfo> {
        if (!initSource(novelUrl)) {
            return emptyList()
        }

        currentNovelUrl = novelUrl
        return currentSource?.getNovelChapterList(this) ?: emptyList()
    }

    suspend fun downloadChapter(chapterUrl: String): String? {
        if (!initSource(chapterUrl)) {
            return null
        }

        return currentSource?.downloadChapterBody(this, chapterUrl)
    }

    suspend fun searchNovels(query: String): List<NovelInfo> {
        return SOURCES[0].searchNovel(this, query)
    }

    fun absoluteUrl(_url: String = "", _pageUrl: String? = null): String {
        val homeUrl = currentHomeUrl ?: return ""

        val url = _url.trim()

        if (url.length > 1000 || url.startsWith("data:")) {
            return url
        }

        val pageUrl = _pageUrl ?: lastVisitedUrl
        when {
            url.isEmpty() -> {
                return url
            }
            url.startsWith("//") -> {
                return homeUrl.split(":")[0] + ":" + url
            }
            url.contains("//") -> {
                return url
            }
            url.startsWith("/") -> {
                return homeUrl.trim('/') + url
            }
            pageUrl != null -> {
                return pageUrl.trim('/') + '/' + url
            }
            else -> {
                return homeUrl + url
            }
        }
    }

    private fun cleanText(node: TextNode): String {
        val rawText = node.text().trim()
        var text = rawText.filterNot { c -> NON_PRINTABLE_CHARS.contains(c.code) }
        for ((key, value) in currentFilter.substitutions) {
            text = text.replace(key, value)
        }
        return text
    }

    private fun cleanContents(div: Element) {
        if (currentFilter.badCss.isNotEmpty()) {
            for (bad in div.select(currentFilter.badCss.joinToString(","))) {
                bad.remove()
            }
        }

        for (tag in div.allElements) {
            if (tag.normalName() == "#comment") {
                tag.remove()
            } else if (tag.normalName() == "br") {
                val next = tag.nextSibling()
                if (next != null && next.nodeName().lowercase() == "br") {
                    next.remove()
                }
            } else if (currentFilter.badTags.contains(tag.tagName())) {
                tag.remove()
            } else if (tag.attributesSize() > 0) {
                // TODO: This is doing something with the "src" attr
                // Only keep "src" attributes maybe?
                val src = tag.attr("src")
                tag.clearAttributes()
                if (src.isNotEmpty()) {
                    tag.attr("src", src)
                }
            }
        }

        div.clearAttributes()
    }

    private fun isInBlacklist(text: String): Boolean {
        if (text.isEmpty()) {
            return true
        }
        for (regex in currentFilter.blacklistRegexes) {
            if (regex.containsMatchIn(text)) {
                return true
            }
        }
        return false
    }

    fun extractContents(tag: Element): String {
        cleanContents(tag)
        val body = internalExtractContents(tag).joinToString(" ")
        return body.split(LINE_SEP)
                .filterNot { s -> isInBlacklist(s) }
                .joinToString("\n") { s -> "<p>$s<p>" }
    }

    private fun internalExtractContents(tag: Element): List<String> {
        val body: MutableList<String> = arrayListOf()

        for (elem in tag.children()) {
            if (elem.normalName() == "#comment") {
                continue
            }
            if (currentFilter.unchangedTags.contains(elem.normalName())) {
                body.add(elem.text())
                continue
            }
            if (elem.normalName() == "hr") {
                body.add(LINE_SEP)
                continue
            }
            if (elem.normalName() == "br") {
                body.add(LINE_SEP)
                continue
            }

            for (node in elem.textNodes()) {
                body.add(cleanText(node))
            }

            val isBlock = currentFilter.pBlockTags.contains(elem.normalName())
            val isPlain = currentFilter.plainTextTags.contains(elem.normalName())

            val content = internalExtractContents(elem).joinToString(" ")

            if (isBlock) {
                body.add(LINE_SEP)
            }

            for (rawLine in content.split(LINE_SEP)) {
                var line = rawLine.trim()
                if (line.isEmpty()) {
                    continue
                }

                if (!(isPlain || isBlock)) {
                    line = String.format("<%s>%s<%s>", elem.normalName(), line, elem.normalName())
                }

                body.add(line)
                body.add(LINE_SEP)
            }

            if (body.isNotEmpty()
                    && body[body.size - 1] == LINE_SEP
                    && !isBlock)
            {
                body.removeLast()
            }
        }

        val iterator = body.listIterator()
        while (iterator.hasNext()) {
            val oldValue = iterator.next()
            iterator.set(oldValue.trim())
        }

        return body
    }
}
