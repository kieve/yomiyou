package ca.kieve.yomiyou.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

/**
 * An EPUB's package document, as both kinds of book read it: its spine, direction, cover, title and author, and its
 * table of contents (EPUB 3's nav, else EPUB 2's NCX) as each document's first label.
 */
internal class Package(
    val spine: List<String>,
    val vertical: Boolean,
    val coverPath: String?,
    val title: String,
    val author: String,
    /** A document's path to the first label the table of contents gives it. */
    val toc: Map<String, String>,
) {
    companion object {
        fun read(zip: ZipIndex, opfPath: String): Package {
            val opf = parse(zip.text(opfPath))
            val elements = { localName: String -> EnrichedEpub.elements(opf, localName) }
            val items = elements("item").associate { it.attr("id") to it }
            val spineElement = elements("spine").firstOrNull()
            val spine = elements("itemref")
                .mapNotNull { items[it.attr("idref")]?.attr("href") }
                .map { EpubPaths.resolve(opfPath, it) }
            // EPUB 3 marks the cover in the manifest; EPUB 2 names its item in a meta.
            val coverItem = items.values.firstOrNull { "cover-image" in it.attr("properties").split(' ') }
                ?: elements("meta").firstOrNull { it.attr("name") == "cover" }?.let { items[it.attr("content")] }
            val coverPath = coverItem?.let { EpubPaths.resolve(opfPath, it.attr("href")) }?.takeIf { zip.has(it) }
            val nav = items.values.firstOrNull { "nav" in it.attr("properties").split(' ') }
                ?.let { EpubPaths.resolve(opfPath, it.attr("href")) }
            val ncx = items[spineElement?.attr("toc").orEmpty()]
                ?: items.values.firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" }
            val toc = when {
                nav != null && zip.has(nav) -> navLabels(parse(zip.text(nav)), nav)
                ncx != null -> EpubPaths.resolve(opfPath, ncx.attr("href"))
                    .takeIf { zip.has(it) }
                    ?.let { ncxLabels(parse(zip.text(it)), it) }
                    .orEmpty()
                else -> emptyMap()
            }
            return Package(
                spine = spine,
                vertical = spineElement?.attr("page-progression-direction") == "rtl",
                coverPath = coverPath,
                title = elements("title").firstOrNull()?.text()?.trim().orEmpty(),
                author = elements("creator").firstOrNull()?.text()?.trim().orEmpty(),
                toc = toc,
            )
        }

        private fun parse(text: String): Document = Jsoup.parse(text, "", Parser.xmlParser())

        /** The nav marked as the table of contents (or the first nav): each link's document to its label. */
        private fun navLabels(document: Document, navPath: String): Map<String, String> {
            val navs = EnrichedEpub.elements(document, "nav")
            val toc = navs.firstOrNull { "toc" in it.attr("epub:type").split(' ') } ?: navs.firstOrNull()
                ?: return emptyMap()
            val labels = LinkedHashMap<String, String>()
            for (link in toc.allElements.filter { it.tagName().substringAfter(':') == "a" }) {
                val href = link.attr("href")
                val label = link.text().trim()
                if (href.isEmpty() || label.isEmpty() || EpubPaths.isExternal(href)) continue
                labels.putIfAbsent(EpubPaths.resolve(navPath, href), label)
            }
            return labels
        }

        /** NCX navPoints in order: each one's document to its label. */
        private fun ncxLabels(document: Document, ncxPath: String): Map<String, String> {
            val labels = LinkedHashMap<String, String>()
            for (point in EnrichedEpub.elements(document, "navPoint")) {
                val label = point.children().firstOrNull { it.tagName().substringAfter(':') == "navLabel" }?.text()?.trim()
                val src = point.children().firstOrNull { it.tagName().substringAfter(':') == "content" }?.attr("src")
                if (label.isNullOrEmpty() || src.isNullOrEmpty()) continue
                labels.putIfAbsent(EpubPaths.resolve(ncxPath, src), label)
            }
            return labels
        }
    }
}
