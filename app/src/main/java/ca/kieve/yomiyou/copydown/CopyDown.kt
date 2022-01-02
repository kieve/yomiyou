package ca.kieve.yomiyou.copydown

import ca.kieve.yomiyou.copydown.util.isNodeType1
import ca.kieve.yomiyou.copydown.util.isNodeType3
import org.jsoup.nodes.TextNode
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.Collections.nCopies
import java.util.regex.Pattern

class CopyDown(options: Options = Options()) {
    private data class Escape(
        val pattern: Regex,
        val replace: String
    ) {
        constructor(pattern: String, replace: String): this(
            pattern.toRegex(),
            replace
        )
    }

    companion object {
        private val LEADING_NEW_LINE_PATTERN = Pattern.compile("""^(\n*)""")
        private val TRAILING_NEW_LINE_PATTERN = Pattern.compile("""(\n*)$""")
    }

    private val rules = Rules(options)
    private val escapes = listOf(
        Escape("\\\\", "\\\\\\\\"),
        Escape("\\*", "\\\\*"),
        Escape("^-", "\\\\-"),
        Escape("^\\+ ", "\\\\+ "),
        Escape("^(=+)", "\\\\$1"),
        Escape("^(#{1,6}) ", "\\\\$1 "),
        Escape("`", "\\\\`"),
        Escape("^~~~", "\\\\~~~"),
        Escape("\\[", "\\\\["),
        Escape("\\]", "\\\\]"),
        Escape("^>", "\\\\>"),
        Escape("_", "\\\\_"),
        Escape("^(\\d+)\\. ", "$1\\\\. ")
    )

    /**
     * Accepts a HTML string and converts it to markdown
     *
     * Note, if LinkStyle is chosen to be REFERENCED the method is not thread safe.
     * @param input html to be converted
     * @return markdown text
     */
    fun convert(input: String): String {
        rules.reset()
        return postProcess(
            process(CopyNode(input))
        )
            .replace("""^[\t\n\r]+""".toRegex(), "")
            .replace("""[\t\r\n\s]+$""".toRegex(), "")
    }

    private fun escape(string: String): String {
        var result = string
        for (escape in escapes) {
            result = result.replace(escape.pattern, escape.replace)
        }
        return result
    }

    private fun join(left: String, right: String): String {
        val trailingMatcher = TRAILING_NEW_LINE_PATTERN.matcher(left)
        val leadingMatcher = LEADING_NEW_LINE_PATTERN.matcher(right)
        trailingMatcher.find()
        leadingMatcher.find()

        val nNewLines = min(2,
            max(trailingMatcher.group().length, leadingMatcher.group().length))
        val newLineJoin = nCopies(nNewLines, "\n").joinToString("")

        return (trailingMatcher.replaceAll("")
                + newLineJoin
                + leadingMatcher.replaceAll(""))
    }

    private fun replacementForNode(node: CopyNode): String {
        var content = process(node)
        val flankingWhiteSpaces = node.flankingWhitespace()
        if (flankingWhiteSpaces.leading.isNotEmpty() || flankingWhiteSpaces.trailing.isNotEmpty()) {
            content = content.trim()
        }

        val rule = rules.findRule(node.element)
        if (rule != null && node.element != null) {
            content = rule.replacement.apply(content, node.element)
        }

        return flankingWhiteSpaces.leading + content + flankingWhiteSpaces.trailing
    }

    private fun postProcess(output: String): String {
        var result = output
        for (rule in rules.rules) {
            if (rule.append != null) {
                result = join(output, rule.append.get())
            }
        }
        return result
    }

    private fun process(node: CopyNode): String {
        var result = ""
        node.element ?: return result

        for (child in node.element.childNodes()) {
            val copyNodeChild = CopyNode(child, node)
            var replacement = ""
            if (isNodeType3(child)) {
                // TODO it should be child.nodeValue
                child as TextNode
                replacement = if (copyNodeChild.isCode()) child.text() else escape(child.text())
            } else if (isNodeType1(child)) {
                replacement = replacementForNode(copyNodeChild)
            }
            result = join(result, replacement)
        }
        return result
    }
}
