package ca.kieve.yomiyou.copydown

import ca.kieve.yomiyou.copydown.util.collapseWhitespace
import ca.kieve.yomiyou.copydown.util.isNodeType1
import ca.kieve.yomiyou.copydown.util.isNodeType3
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

class CopyNode {
    data class FlankingWhiteSpaces(
        val leading: String,
        val trailing: String
    )

    companion object {
        private val VOID_ELEMENTS = setOf(
            "area", "base", "br", "col", "command", "embed", "hr", "img", "input", "keygen", "link",
            "meta", "param", "source", "track", "wbr"
        )

        private val MEANINGFUL_WHEN_BLANK_ELEMENTS = setOf(
            "a", "table", "thead", "tbody", "tfoot", "th", "td", "iframe", "script", "audio",
            "video"
        )

        private val BLOCK_ELEMENTS = setOf(
            "address", "article", "aside", "audio", "blockquote", "body", "canvas", "center", "dd",
            "dir", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
            "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "html",
            "isindex", "li", "main", "menu", "nav", "noframes", "noscript", "ol", "output", "p",
            "pre", "section", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul"
        )

        private val ALL_WHITESPACE_REGEX = """(?i)^\s*$""".toRegex()
        private val LEADING_WHITESPACE_REGEX = """^\s""".toRegex()
        private val TRAILING_WHITESPACE_REGEX = """\s$""".toRegex()
        private val LEADING_SPACE_REGEX = "^ ".toRegex()
        private val TRAILING_SPACE_REGEX = " $".toRegex()

        fun isVoid(element: Node): Boolean {
            return VOID_ELEMENTS.contains(element.nodeName())
        }

        fun isMeaningfulWhenBlank(element: Node): Boolean {
            return MEANINGFUL_WHEN_BLANK_ELEMENTS.contains(element.nodeName())
        }

        fun isBlock(element: Node): Boolean {
            return BLOCK_ELEMENTS.contains(element.nodeName())
        }

        private fun hasVoidNodes(node: Node): Boolean {
            return hasTagNodes(node, VOID_ELEMENTS)
        }

        private fun hasMeaningfulWhenBlankNodes(node: Node): Boolean {
            return hasTagNodes(node, MEANINGFUL_WHEN_BLANK_ELEMENTS)
        }

        private fun hasBlockNodes(node: Node): Boolean {
            return hasTagNodes(node, BLOCK_ELEMENTS)
        }

        private fun hasTagNodes(node: Node, tags: Collection<String>): Boolean {
            if (node !is Element) {
                return false
            }
            for (tag in tags) {
                if (node.getElementsByTag(tag).isNotEmpty()) {
                    return true
                }
            }
            return false
        }

        fun isBlank(element: Node): Boolean {
            val textContent = if (element is Element) {
                element.wholeText()
            } else {
                element.outerHtml()
            }
            return !isVoid(element)
                    && !isMeaningfulWhenBlank(element)
                    && ALL_WHITESPACE_REGEX.containsMatchIn(textContent)
                    && !hasVoidNodes(element)
                    && !hasMeaningfulWhenBlankNodes(element)
        }
    }

    val element: Node?
    val parent: CopyNode?

    constructor(element: Node, parent: CopyNode?) {
        this.element = element
        this.parent = parent
    }

    constructor(input: String) {
        val document = Jsoup.parse(
            // DOM parsers arrange elements in the <head> and <body>.
            // Wrapping in a custom element ensures elements are reliably arranged in
            // a single element.
            "<x-copydown id=\"copydown-root\">$input</x-copydown>"
        )
        val root = document.getElementById("copydown-root")
        root?.collapseWhitespace()
        this.element = root
        this.parent = null
    }

    fun isCode(): Boolean {
        element ?: return false
        return element.nodeName() == "code"
                || parent?.isCode() ?: false
    }

    fun flankingWhitespace(): FlankingWhiteSpaces {
        var leading = ""
        var trailing = ""

        if (element == null || isBlock(element)) {
            return FlankingWhiteSpaces(
                leading = leading,
                trailing = trailing
            )
        }

        val textContent = if (element is Element) {
            element.wholeText()
        } else {
            element.outerHtml()
        }

        // TODO original uses textContent
        val hasLeading = LEADING_WHITESPACE_REGEX.containsMatchIn(textContent)
        val hasTrailing = TRAILING_WHITESPACE_REGEX.containsMatchIn(textContent)

        // TODO maybe make node property and avoid recomputing
        val blankWithSpaces = isBlank(element) && hasLeading && hasTrailing
        if (hasLeading && !isLeftFlankedByWhitespaces()) {
            leading = " "
        }
        if (!blankWithSpaces
            && hasTrailing
            && !isRightFlankedByWhitespaces())
        {
            trailing = " "
        }
        return FlankingWhiteSpaces(
            leading = leading,
            trailing = trailing
        )
    }

    private fun isLeftFlankedByWhitespaces(): Boolean {
        return isChildFlankedByWhitespaces(TRAILING_SPACE_REGEX, element?.previousSibling())
    }

    private fun isRightFlankedByWhitespaces(): Boolean {
        return isChildFlankedByWhitespaces(LEADING_SPACE_REGEX, element?.nextSibling())
    }

    private fun isChildFlankedByWhitespaces(regex: Regex, sibling: Node?): Boolean {
        if (sibling == null) {
            return false
        }

        if (isNodeType3(sibling)) {
            // TODO fix. Originally sibling.nodeValue
            return regex.containsMatchIn(sibling.outerHtml())
        }
        if (isNodeType1(sibling)) {
            // TODO fix. Originally textContent
            return regex.containsMatchIn(sibling.outerHtml())
        }
        return false
    }
}
