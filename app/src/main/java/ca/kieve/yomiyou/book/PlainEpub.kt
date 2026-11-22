package ca.kieve.yomiyou.book

import ca.kieve.yomiyou.reader.ReaderBook
import ca.kieve.yomiyou.reader.Resource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

/**
 * An EPUB as the publisher made it, without narration: each spine document is a chapter, labelled from the table of
 * contents. A place in it is Kavita's: the document's spine index and a path to a block from its `<body>`
 * (`//body/p[11]`); each block the page can report carries its path as `data-x`.
 */
class PlainEpub private constructor(private val zip: ZipIndex, private val pack: Package) : ReaderBook {
    override val title: String get() = pack.title

    val author: String get() = pack.author

    override val vertical: Boolean get() = pack.vertical

    override val plain: Boolean get() = true

    override val chapters: List<String> = pack.spine.mapIndexed { index, href -> pack.toc[href] ?: "${index + 1}" }

    @Synchronized
    override fun chapter(index: Int): String {
        val href = pack.spine.getOrNull(index) ?: throw BookError("No chapter $index")
        if (!zip.has(href)) throw BookError("Damaged EPUB: $href is missing")
        val document = Jsoup.parse(zip.text(href), "", Parser.xmlParser())
        val body = EnrichedEpub.bodyOf(document)?.clone() ?: throw BookError("Damaged chapter $index")
        markPlaces(body)
        val out = Document("")
        out.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        val wrapper = out.appendElement("div")
        wrapper.appendChildren(EnrichedEpub.prepared(zip, body.childNodes().map { it.clone() }, href))
        val namespaces = linkedMapOf(
            "xmlns" to EnrichedEpub.XHTML,
            "xmlns:epub" to EnrichedEpub.EPUB_OPS,
            "xmlns:xlink" to EnrichedEpub.XLINK,
        )
        namespaces.putAll(EnrichedEpub.namespacesOf(document))
        namespaces.forEach { (name, uri) -> wrapper.attr(name, uri) }
        return wrapper.outerHtml()
    }

    override fun resource(path: String): Resource? =
        if (zip.has(path)) Resource(zip.read(path), EpubPaths.mediaType(path)) else null

    override fun spinePlace(position: Position): SpinePlace? =
        position.path?.takeIf { position.chapter in pack.spine.indices }?.let { SpinePlace(position.chapter, it) }

    override fun positionAt(place: SpinePlace): Position? =
        if (place.page in pack.spine.indices) Position(place.page, -1, place.path) else null

    /** The book's cover image, if it names one. */
    fun cover(): Resource? = pack.coverPath?.let(::resource)

    override fun close() = zip.close()

    companion object {
        /** Elements that hold text as a block: a place is one of these with none inside it. */
        private val BLOCKS = setOf(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "section", "article", "aside",
            "header", "footer", "figure", "figcaption", "table", "pre", "dd", "dt",
        )

        /** @throws BookError when it isn't an EPUB */
        fun open(zip: ZipIndex): PlainEpub {
            val opfPath = EnrichedEpub.packagePath(zip)
            return PlainEpub(zip, Package.read(zip, opfPath))
        }

        private fun localName(element: Element) = element.tagName().substringAfter(':').lowercase()

        /** Gives each block with no block inside it its path, as `data-x`. */
        internal fun markPlaces(body: Element) {
            for (element in body.allElements) {
                if (element === body || localName(element) !in BLOCKS) continue
                if (element.allElements.drop(1).none { localName(it) in BLOCKS }) {
                    element.attr("data-x", pathOf(element, body))
                }
            }
        }

        /**
         * The block [path] names in [body] (marked by [markPlaces]), as the reader page finds one: the block itself, the
         * block around or first inside the element `id("…")` names, else the first block under the nearest named
         * ancestor.
         */
        internal fun findPlace(body: Element, path: String): Element? {
            Regex("""^id\("(.+)"\)$""").matchEntire(path)?.let { match ->
                val element = body.getElementsByAttributeValue("id", match.groupValues[1]).firstOrNull() ?: return null
                return element.closest("[data-x]") ?: element.selectFirst("[data-x]")
            }
            val blocks = body.select("[data-x]")
            var prefix = path
            while (prefix.length > "//body".length) {
                blocks.firstOrNull { it.attr("data-x") == prefix || it.attr("data-x").startsWith("$prefix/") }?.let { return it }
                prefix = prefix.substringBeforeLast('/')
            }
            return null
        }

        /** Kavita's path to [element]: from `//body`, each step its name and its place among same-named siblings. */
        internal fun pathOf(element: Element, body: Element): String {
            val steps = ArrayDeque<String>()
            var at = element
            while (at !== body) {
                val name = localName(at)
                val parent = at.parent() ?: break
                val place = parent.children().takeWhile { it !== at }.count { localName(it) == name } + 1
                steps.addFirst("$name[$place]")
                at = parent
            }
            return "//body/" + steps.joinToString("/")
        }
    }
}
