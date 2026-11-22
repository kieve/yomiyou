package ca.kieve.yomiyou.book

import ca.kieve.yomiyou.reader.ReaderBook
import ca.kieve.yomiyou.reader.ReaderUrls
import ca.kieve.yomiyou.reader.Resource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.IdentityHashMap

/** A message fit to show. */
class BookError(message: String) : Exception(message)

/**
 * An enriched EPUB: its package and `yomiage.json`, and each chapter cut from its spine document as the web reader
 * cuts it (`book.ts`), from its first phrase's block to the next chapter's. Markup stays as the book wrote it; what would
 * run or restyle it is dropped.
 */
class EnrichedEpub private constructor(
    private val zip: ZipIndex,
    val manifest: BookManifest,
    private val spine: List<String>,
    override val vertical: Boolean,
    private val coverPath: String?,
) : ReaderBook {
    override val title: String get() = manifest.title

    override val chapters: List<String> = manifest.chapters.map { it.title.ifEmpty { it.number.toString() } }

    private val chapterHrefs = manifest.chapters.map { it.href }

    /** The last document parsed: chapters of one document are read in a row, and parsing a long one takes a while. */
    private var parsed: Pair<String, Document>? = null

    @Synchronized
    override fun chapter(index: Int): String {
        val chapter = manifest.chapters.getOrNull(index) ?: throw BookError("No chapter $index")
        val out = Document("")
        out.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        val wrapper = out.appendElement("div")
        val namespaces = linkedMapOf("xmlns" to XHTML, "xmlns:epub" to EPUB_OPS, "xmlns:xlink" to XLINK)
        for (href in EpubPaths.leadingDocuments(spine, chapterHrefs, index)) {
            val document = document(href)
            namespaces.putAll(namespacesOf(document))
            val body = bodyOf(document) ?: continue
            wrapper.appendChildren(prepared(zip, body.childNodes().map { it.clone() }, href))
        }
        val document = document(chapter.href)
        namespaces.putAll(namespacesOf(document))
        val body = bodyOf(document) ?: throw BookError("Damaged chapter $index")
        val start = if (chapterHrefs.getOrNull(index - 1) == chapter.href) blockOf(body, chapter.firstSpan) else null
        val next = manifest.chapters.getOrNull(index + 1)
        val end = if (next?.href == chapter.href) blockOf(body, next.firstSpan) else null
        wrapper.appendChildren(prepared(zip, cut(body, start, end), chapter.href))
        namespaces.forEach { (name, uri) -> wrapper.attr(name, uri) }
        return wrapper.outerHtml()
    }

    override fun resource(path: String): Resource? =
        if (zip.has(path)) Resource(zip.read(path), EpubPaths.mediaType(path)) else null

    /** The book's cover image, if it names one. */
    fun cover(): Resource? = coverPath?.let(::resource)

    override fun close() = zip.close()

    private fun document(href: String): Document {
        parsed?.let { (parsedHref, document) -> if (parsedHref == href) return document }
        if (!zip.has(href)) throw BookError("Damaged EPUB: $href is missing")
        return Jsoup.parse(zip.text(href), "", Parser.xmlParser()).also { parsed = href to it }
    }

    companion object {
        const val FORMAT = 2
        private const val BOOK_JSON = "yomiage.json"
        internal const val XHTML = "http://www.w3.org/1999/xhtml"
        internal const val EPUB_OPS = "http://www.idpf.org/2007/ops"
        internal const val XLINK = "http://www.w3.org/1999/xlink"
        private val DROPPED = setOf("script", "style", "link", "iframe", "object", "embed")

        /** Blocks a chapter can start at; the same ones the build counts. */
        private const val BLOCK = "p, h1, h2, h3, h4, h5, h6, li, div"

        private val json = Json { ignoreUnknownKeys = true }

        /** @throws BookError when it isn't an enriched EPUB this reader can read */
        fun open(zip: ZipIndex): EnrichedEpub {
            val opfPath = packagePath(zip)
            val manifest = manifestOf(zip, opfPath)
            val pack = Package.read(zip, opfPath)
            return EnrichedEpub(zip, manifest, pack.spine, pack.vertical, pack.coverPath)
        }

        /** Whether the EPUB has yomiage's phrase data, so it's a narrated book. */
        fun isEnriched(zip: ZipIndex): Boolean = zip.has(EpubPaths.folderOf(packagePath(zip)) + BOOK_JSON)

        /** Just `yomiage.json`: which book a file is, without reading the rest. */
        fun manifestOf(zip: ZipIndex): BookManifest = manifestOf(zip, packagePath(zip))

        /** Clean copies, their images linked to [ReaderUrls.BOOK]. */
        internal fun prepared(zip: ZipIndex, nodes: List<Node>, href: String): List<Node> = nodes.mapNotNull { node ->
            if (node !is Element) return@mapNotNull node
            if (node.normalName() in DROPPED) return@mapNotNull null
            node.select(DROPPED.joinToString()).remove()
            for (element in node.selectXpath("descendant-or-self::*")) {
                element.attributes().asList()
                    .filter { it.key == "style" || it.key.startsWith("on") }
                    .forEach { element.removeAttr(it.key) }
            }
            for (image in node.select("img, image")) {
                val linked = image.attr("src").ifEmpty { image.attr("href") }.ifEmpty { image.attr("xlink:href") }
                image.removeAttr("src").removeAttr("href").removeAttr("xlink:href")
                val path = if (linked.isEmpty() || EpubPaths.isExternal(linked)) "" else EpubPaths.resolve(href, linked)
                if (!zip.has(path)) continue
                val url = ReaderUrls.BOOK + EpubPaths.percentEncode(path)
                image.attr(if (image.normalName() == "img") "src" else "xlink:href", url)
            }
            node
        }

        internal fun packagePath(zip: ZipIndex): String {
            if (!zip.has("META-INF/container.xml")) throw BookError("Not an EPUB.")
            val container = Jsoup.parse(zip.text("META-INF/container.xml"), "", Parser.xmlParser())
            val path = elements(container, "rootfile").firstOrNull()?.attr("full-path")
            if (path.isNullOrEmpty() || !zip.has(path)) throw BookError("Damaged EPUB: no package document.")
            return path
        }

        private fun manifestOf(zip: ZipIndex, opfPath: String): BookManifest {
            val path = EpubPaths.folderOf(opfPath) + BOOK_JSON
            if (!zip.has(path)) throw BookError("No phrase data in this EPUB. Build it with yomiage first.")
            val manifest = try {
                json.decodeFromString<BookManifest>(zip.text(path))
            } catch (e: SerializationException) {
                throw BookError("Damaged phrase data in this EPUB.")
            } catch (e: IOException) {
                throw BookError("Damaged EPUB.")
            }
            if (manifest.format != FORMAT) throw BookError("This EPUB was built by a different version of yomiage.")
            return manifest
        }

        /** Elements by local name, whatever prefix the file gave them (`opf:item` in some EPUB 2 packages). */
        internal fun elements(document: Document, localName: String): List<Element> =
            document.allElements.filter { it.tagName().substringAfter(':') == localName }

        internal fun bodyOf(document: Document): Element? = elements(document, "body").firstOrNull()

        internal fun namespacesOf(document: Document): Map<String, String> =
            document.children().firstOrNull()?.attributes()?.asList()
                ?.filter { it.key.startsWith("xmlns:") }
                ?.associate { it.key to it.value }
                .orEmpty()

        private fun blockOf(body: Element, span: Long): Element? =
            body.selectFirst("[data-y=\"$span\"]")?.let { it.closest(BLOCK) ?: it }

        /**
         * Copies of [body]'s contents from just before [start] to just before [end] (null: that edge), keeping the
         * elements around a boundary with only their part inside, as a DOM range's contents do.
         */
        internal fun cut(body: Element, start: Node?, end: Node?): List<Node> {
            val first = IdentityHashMap<Node, Int>()
            val last = IdentityHashMap<Node, Int>()
            var counter = 0
            fun number(node: Node) {
                first[node] = counter++
                node.childNodes().forEach(::number)
                last[node] = counter - 1
            }
            number(body)
            fun holds(outer: Node, inner: Node?) =
                inner != null && first.getValue(outer) < first.getValue(inner) && first.getValue(inner) <= last.getValue(outer)

            fun copy(parent: Node, from: Node?, to: Node?): List<Node> = buildList {
                for (child in parent.childNodes()) {
                    if (to != null && !holds(child, to) && first.getValue(child) >= first.getValue(to)) break
                    if (from != null && last.getValue(child) < first.getValue(from)) continue
                    val holdsFrom = holds(child, from)
                    val holdsTo = holds(child, to)
                    if (holdsFrom || holdsTo) {
                        val shallow = child.shallowClone() as Element
                        shallow.appendChildren(copy(child, from.takeIf { holdsFrom }, to.takeIf { holdsTo }))
                        add(shallow)
                    } else {
                        add(child.clone())
                    }
                }
            }
            return copy(body, start, end)
        }
    }
}
