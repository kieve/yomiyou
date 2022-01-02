package ca.kieve.yomiyou.copydown

import ca.kieve.yomiyou.copydown.style.CodeBlockStyle
import ca.kieve.yomiyou.copydown.style.HeadingStyle
import ca.kieve.yomiyou.copydown.style.LinkReferenceStyle
import ca.kieve.yomiyou.copydown.style.LinkStyle
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.util.Collections.nCopies
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.math.max

class Rules(private val options: Options = Options()) {
    data class Rule(
        val name: String? = null,
        val filter: Predicate<Node>,
        val replacement: BiFunction<String, Node, String>,
        val append: Supplier<String>? = null
    ) {
        constructor(
            name: String? = null,
            filter: String,
            replacement: BiFunction<String, Node, String>,
            append: Supplier<String>? = null
        ): this(
            name = name,
            filter = Predicate { el: Node -> el.nodeName().lowercase() == filter },
            replacement = replacement,
            append = append
        )

        constructor(
            name: String? = null,
            filter: Set<String>,
            replacement: BiFunction<String, Node, String>,
            append: Supplier<String>? = null
        ): this(
            name = name,
            filter = Predicate { el: Node -> filter.contains(el.nodeName()) },
            replacement = replacement,
            append = append
        )
    }

    private val references = mutableListOf<String>()
    val rules: List<Rule>

    fun reset() {
        references.clear()
    }

    fun findRule(node: Node?): Rule? {
        node ?: return null
        for (rule in rules) {
            if (rule.filter.test(node)) {
                return rule
            }
        }
        return null
    }

    private fun cleanAttribute(attribute: String): String {
        return attribute.replace("(\n+\\s*)+".toRegex(), "\n")
    }

    init {
        rules = listOf(
            Rule(
                name = "blankReplacement",
                filter = { element -> CopyNode.isBlank(element) },
                replacement = { _, element -> if (CopyNode.isBlock(element)) "\n\n" else "" }
            ),
            Rule(
                name = "paragraph",
                filter = "p",
                replacement = { content, _ -> "\n\n$content\n\n" },
            ),
            Rule(
                name = "br",
                filter = "br",
                replacement = { _, _ -> options.br + "\n" }
            ),
            Rule(
                name = "heading",
                filter = setOf("h1", "h2", "h3", "h4", "h5", "h6"),
                replacement = { content, element ->
                    val hLevel = element.nodeName().substring(1, 2).toInt()
                    if (options.headingStyle == HeadingStyle.SETEXT && hLevel < 3) {
                        val underline = nCopies(
                            content.length,
                            if (hLevel == 1) "=" else "-")
                            .joinToString("")
                        "\n\n$content\n$underline\n\n"
                    } else {
                        val prefix = nCopies(hLevel, "#")
                            .joinToString("")
                        "\n\n$prefix $content\n\n"
                    }
                }
            ),
            Rule(
                name = "blockquote",
                filter = "blockquote",
                replacement = { content, _ ->
                    val result = content
                        .replace("""^\n+|\n+${'$'}""".toRegex(), "")
                        .replace("""(?m)^""".toRegex(), "> ")
                    "\n\n$result\n\n"
                }
            ),
            Rule(
                name = "list",
                filter = setOf("ul", "ol"),
                replacement = { content, element ->
                    val parent = element.parentNode() as Element
                    if (parent.nodeName() == "li"
                        && parent.child(parent.childrenSize() - 1) == element)
                    {
                        "\n$content"
                    } else {
                        "\n\n$content\n\n"
                    }
                }
            ),
            Rule(
                name = "listItem",
                filter = "li",
                replacement = { content, element ->
                    val replaced = content
                        // remove leading new lines
                        .replace("^\n+".toRegex(), "")
                        // remove trailing new lines with just a single one
                        .replace("\n+$".toRegex(), "\n")
                        // indent
                        .replace("(?m)\n".toRegex(), "\n    ")
                    val parent = element.parentNode() as Element
                    val prefix = if (parent.nodeName() == "ol") {
                        val start = parent.attr("start")
                        val index = parent.children().indexOf(element)
                        val parsedStart = start.toIntOrNull() ?: 1
                        "${parsedStart + index}.  "
                    } else {
                        options.bulletListMarker + "   "
                    }

                    val lineSeparator = if (element.nextSibling() != null
                        && !"\n$".toRegex().containsMatchIn(replaced))
                    {
                        "\n"
                    } else {
                        ""
                    }

                    "$prefix$replaced$lineSeparator"
                }
            ),
            Rule(
                name = "indentedCodeBlock",
                filter = { element ->
                    options.codeBlockStyle == CodeBlockStyle.INDENTED
                            && element.nodeName() == "pre"
                            && element.childNodeSize() > 0
                            && element.childNode(0).nodeName() == "code"
                },
                replacement = { _, element ->
                    // TODO check textContent
                    val content = (element.childNode(0) as Element)
                        .wholeText()
                        .replace("\n", "\n    ")
                    "\n\n    $content"
                }
            ),
            Rule(
                name = "fencedCodeBlock",
                filter = { element ->
                    options.codeBlockStyle == CodeBlockStyle.FENCED
                            && element.nodeName() == "pre"
                            && element.childNodeSize() > 0
                            && element.childNode(0).nodeName() == "code"
                },
                replacement = { content, element ->
                    val childClass = element.childNode(0).attr("class") ?: ""
                    val languageMatches = """language-(\S+)""".toRegex().find(childClass)
                    val language = languageMatches?.groupValues?.get(1) ?: ""

                    var code = if (element.childNode(0) is Element) {
                        (element.childNode(0) as Element).wholeText()
                    } else {
                        element.childNode(0).outerHtml()
                    }

                    val fenceChar = options.fence.substring(0, 1)
                    var fenceSize = 3
                    var fenceMatches = """(?m)^($fenceChar{3,})""".toRegex().find(content)
                    while (fenceMatches != null) {
                        val group = fenceMatches.groupValues[1]
                        fenceSize = max(group.length + 1, fenceSize)
                        fenceMatches = fenceMatches.next()
                    }
                    val fence = nCopies(fenceSize, fenceChar).joinToString("")
                    if (code.isNotEmpty() && code[code.length - 1] == '\n') {
                        code = code.substring(0, code.length - 1)
                    }
                    "\n\n$fence$language\n$code\n$fence\n\n"
                }
            ),
            Rule(
                name = "horizontalRule",
                filter = "hr",
                replacement = { _, _ -> "\n\n${options.hr}\n\n"}
            ),
            Rule(
                name = "inlineLink",
                filter = { element ->
                    options.linkStyle == LinkStyle.INLINED
                            && element.nodeName() == "a"
                            && element.attr("href").isNotEmpty()
                },
                replacement = { content, element ->
                    val href = element.attr("href")
                    var title = cleanAttribute(element.attr("title"))
                    if (title.isNotEmpty()) {
                        title = " \"$title\""
                    }
                    "[$content]($href$title)"
                }
            ),
            Rule(
                name = "referenceLink",
                filter = { element ->
                    options.linkStyle == LinkStyle.REFERENCED
                            && element.nodeName() == "a"
                            && element.attr("href").isNotEmpty()
                },
                replacement = { content, element ->
                    val href = element.attr("href")
                    var title = cleanAttribute(element.attr("title"))
                    if (title.isNotEmpty()) {
                        title = " \"$title\""
                    }
                    val replacement: String
                    val reference: String
                    when (options.linkReferenceStyle) {
                        LinkReferenceStyle.COLLAPSED -> {
                            replacement = "[$content][]"
                            reference = "[$content]: $href$title"
                        }
                        LinkReferenceStyle.SHORTCUT -> {
                            replacement = "[$content]"
                            reference = "[$content]: $href$title"
                        }
                        else -> {
                            val id = references.size + 1
                            replacement = "[$content][$id]"
                            reference = "[$id]: $href$title"
                        }
                    }
                    references.add(reference)
                    replacement
                },
                append = {
                    if (references.size > 0) {
                        val referenceStrings = references.joinToString("\n")
                        "\n\n$referenceStrings\n\n"
                    } else {
                        ""
                    }
                }
            ),
            Rule(
                name = "emphasis",
                filter = setOf("em", "i"),
                replacement = { content, _ ->
                    if (content.trim().isEmpty()) {
                        ""
                    } else {
                        options.emDelimiter + content + options.emDelimiter
                    }
                }
            ),
            Rule(
                name = "strong",
                filter = setOf("strong", "b"),
                replacement = { content, _ ->
                    if (content.trim().isEmpty()) {
                        ""
                    } else {
                        options.strongDelimiter + content + options.strongDelimiter
                    }
                }
            ),
            Rule(
                name = "code",
                filter = { element ->
                    val hasSiblings = element.previousSibling() != null
                            || element.nextSibling() != null
                    val isCodeBlock = element.parentNode()?.nodeName() == "pre" && !hasSiblings
                    element.nodeName() == "code" && !isCodeBlock
                },
                replacement = { content, _ ->
                    if (content.trim().isEmpty()) {
                        return@Rule ""
                    }
                    var delimiter = "`"
                    var leadingSpace = ""
                    var trailingSpace = ""
                    val regex = "(?m)(`)+".toRegex()
                    var matches = regex.find(content)
                    if (matches != null) {
                        if ("^`".toRegex().containsMatchIn(content)) {
                            leadingSpace = " "
                        }
                        if ("`$".toRegex().containsMatchIn(content)) {
                            trailingSpace = " "
                        }
                        var counter = 1
                        while (matches != null) {
                            if (delimiter == matches.groupValues[0]) {
                                counter++
                            }
                            matches = matches.next()
                        }
                        delimiter = nCopies(counter, "`").joinToString("")
                    }
                    "$delimiter$leadingSpace$content$trailingSpace$delimiter"
                }
            ),
            Rule(
                name = "img",
                filter = "img",
                replacement = { _, element ->
                    val alt = cleanAttribute(element.attr("alt"))
                    val src = element.attr("src")
                    if (src.isEmpty()) {
                        return@Rule ""
                    }

                    val title = cleanAttribute(element.attr("title"))
                    val titlePart = if (title.isNotEmpty()) {
                        " \"$title\""
                    } else {
                        ""
                    }
                    "![$alt]($src$titlePart)"
                }
            ),
            Rule(
                name = "default",
                filter = { true },
                replacement = { content, element ->
                    if (CopyNode.isBlock(element)) {
                        "\n\n$content\n\n"
                    } else {
                        content
                    }
                }
            )
        )
    }
}
