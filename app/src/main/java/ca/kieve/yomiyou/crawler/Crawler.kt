package ca.kieve.yomiyou.crawler

import android.icu.text.UnicodeSet
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

data class NovelInfo(
    val title: String,
    val author: String,
    val cover: String,
    val isRtl: Boolean
)

data class ChapterInfo(
    val id: Int,
    val title: String,
    val url: String
)

abstract class Crawler(
    protected val homeUrl: String,
    protected val novelUrl: String)
{
    companion object {
        // val TAG: String = Crawler::class.java.simpleName
        private const val TAG = "FUCK-Crawler"
        private const val USER_AGENT_FALLBACK = "Mozilla/5.0 (Linux; Android 6.0.1; SM-G920V Build/MMB29K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.98 Mobile Safari/537.36"
        private const val LINE_SEP = "<br>"
        private val INVISIBLE_CHARS = UnicodeSet("[\\p{Cf}\\p{Cc}]")
        private val NON_PRINTABLE_CHARS = UnicodeSet(INVISIBLE_CHARS)
                .addAll(0x00, 0x20)
                .addAll(0x7f, 0xa0)
    }

    abstract val baseUrls: List<String>

    abstract suspend fun searchNovel(query: String): List<Map<String, String>>
    abstract suspend fun readNovelInfo(): NovelInfo
    abstract suspend fun downloadChapterBody(chapter: ChapterInfo): String

    protected val badTags: MutableSet<String> = hashSetOf(
            "noscript", "script", "style", "iframe", "ins", "header", "footer", "button", "input",
            "amp-auto-ads", "pirate", "figcaption", "address", "tfoot", "object", "video", "audio",
            "source", "nav", "output", "select", "textarea", "form", "map"
    )

    protected val badCss: MutableSet<String> = hashSetOf(
            ".code-block", ".adsbygoogle", ".sharedaddy", ".inline-ad-slot", ".ads-middle",
            ".jp-relatedposts", ".ezoic-adpicker-ad", ".ezoic-ad-adaptive", ".ezoic-ad",
            ".cb_p6_patreon_button", "a[href*=\"patreon.com\"]"
    )

    protected val pBlockTags: MutableSet<String> = hashSetOf(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "main", "aside", "article", "div", "section"
    )

    protected val unchangedTags: MutableSet<String> = hashSetOf(
            "pre", "canvas", "img"
    )

    protected val plainTextTags: MutableSet<String> = hashSetOf(
            "span", "a", "abbr", "acronym", "label", "time"
    )

    protected val substitutions: MutableMap<String, String> = hashMapOf(
            "\"s" to "'s",
            "“s" to "'s",
            "”s" to "'s",
            "&" to "&amp;",
            "u003c" to "<",
            "u003e" to ">",
            "<" to "&lt;",
            ">" to "&gt;"
    )

    protected val chapters: MutableList<ChapterInfo> = arrayListOf()

    private val requestedUrlState: MutableState<String?> = mutableStateOf(null)
    val requestedUrl: State<String?> = requestedUrlState

    private var webViewUpdateJob: CompletableJob? = null
    private var fetchedHtml: String? = null

    var lastVisitedUrl: String? = null

    fun webViewUpdated(html: String) {
        fetchedHtml = html
        webViewUpdateJob!!.complete()
    }

    protected suspend fun getSoup(url: String): Document {
        webViewUpdateJob = Job()
        requestedUrlState.value = url
        webViewUpdateJob!!.join()
        return Jsoup.parse(fetchedHtml ?: "", url)
    }

    protected fun absoluteUrl(_url: String = "", _pageUrl: String? = null): String {
        val url = _url.trim()

        if (url.length > 1000 || url.startsWith("data:")) {
            return url
        }

        val pageUrl = _pageUrl ?: lastVisitedUrl
        if (url.isEmpty()) {
            return url
        } else if (url.startsWith("//")) {
            return homeUrl.split(":")[0] + ":" + url
        } else if (url.contains("//")) {
            return url
        } else if (url.startsWith("/")) {
            return homeUrl.trim('/') + url
        } else if (pageUrl != null) {
            return pageUrl.trim('/') + '/' + url
        } else {
            return homeUrl + url
        }
    }

    private fun cleanText(node: TextNode): String {
        val rawText = node.text().trim()
        var text = rawText.filterNot { c -> NON_PRINTABLE_CHARS.contains(c.code) }
        for ((key, value) in substitutions) {
            text = text.replace(key, value)
        }
        return text
    }

    private fun cleanContents(div: Element) {
        if (badCss.isNotEmpty()) {
            for (bad in div.select(badCss.joinToString(","))) {
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
            } else if (badTags.contains(tag.tagName())) {
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
        // TODO: Implement the blacklist patterns
        return false
    }

    protected fun extractContents(tag: Element): String {
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
            if (unchangedTags.contains(elem.normalName())) {
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

            val isBlock = pBlockTags.contains(elem.normalName())
            val isPlain = plainTextTags.contains(elem.normalName())

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