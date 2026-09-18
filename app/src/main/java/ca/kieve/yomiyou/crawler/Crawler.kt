package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.copydown.CopyDown
import ca.kieve.yomiyou.crawler.model.ChapterListPage
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.crawler.source.NovelPhoenix
import ca.kieve.yomiyou.scraper.Scraper
import org.jsoup.nodes.Element

data class HtmlFilter(
    val badTags: Set<String>,
    val badCss: Set<String>,
    val substitutions: Map<String, String>,
    val blacklistRegexes: Set<Regex>
)

class Crawler(scraper: Scraper) {
    companion object {
        // Control and format characters (zero-width joiners, soft hyphens, BOMs...), minus the
        // newline, which is Markdown structure.
        private val NON_PRINTABLE_CHARS = Regex("[\\p{Cc}\\p{Cf}&&[^\\n]]")

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

    private val sources: List<NovelSource> = listOf(
        NovelPhoenix(scraper)
    )

    private fun sourceFor(url: String): NovelSource? = sources.firstOrNull { it.owns(url) }

    fun areSameNovel(aUrl: String, bUrl: String): Boolean {
        return aUrl.trimEnd('/') == bUrl.trimEnd('/')
    }

    suspend fun searchNovels(query: String): List<NovelInfo> = sources[0].search(query)

    suspend fun getNovelInfo(novelUrl: String): NovelInfo? {
        return sourceFor(novelUrl)?.novelInfo(novelUrl)
    }

    suspend fun getChapterListPage(novelUrl: String, page: String?, firstPosition: Int): ChapterListPage? {
        return sourceFor(novelUrl)?.chapterListPage(novelUrl, page, firstPosition)
    }

    /** @return The chapter as Markdown, or null if it couldn't be fetched. */
    suspend fun downloadChapter(chapterUrl: String): String? {
        val source = sourceFor(chapterUrl) ?: return null
        val html = source.rawChapterHtml(chapterUrl) ?: return null
        return toMarkdown(html, source.filter)
    }

    private fun cleanContents(div: Element, filter: HtmlFilter) {
        if (filter.badCss.isNotEmpty()) {
            for (bad in div.select(filter.badCss.joinToString(","))) {
                bad.remove()
            }
        }

        for (tag in div.allElements) {
            if (tag.normalName() == "br") {
                val next = tag.nextSibling()
                if (next != null && next.nodeName().lowercase() == "br") {
                    next.remove()
                }
            } else if (filter.badTags.contains(tag.tagName())) {
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

    /** Strips ads/junk from [tag] (in place) and converts what's left to Markdown. */
    fun toMarkdown(tag: Element, filter: HtmlFilter = DEFAULT_FILTER): String {
        cleanContents(tag, filter)
        var markdown = CopyDown().convert(tag.html())
            .replace(NON_PRINTABLE_CHARS, "")
        for ((key, value) in filter.substitutions) {
            markdown = markdown.replace(key, value)
        }
        return markdown.lines()
            .filterNot { line -> filter.blacklistRegexes.any { it.containsMatchIn(line) } }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
    }
}
