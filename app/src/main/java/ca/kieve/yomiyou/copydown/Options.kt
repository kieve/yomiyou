package ca.kieve.yomiyou.copydown

import ca.kieve.yomiyou.copydown.style.CodeBlockStyle
import ca.kieve.yomiyou.copydown.style.HeadingStyle
import ca.kieve.yomiyou.copydown.style.LinkReferenceStyle
import ca.kieve.yomiyou.copydown.style.LinkStyle

data class Options(
    val br: String = "  ",
    val hr: String = "* * *",
    val emDelimiter: String = "_",
    val strongDelimiter: String = "**",
    val bulletListMarker: String = "*",
    val fence: String = "```",
    val headingStyle: HeadingStyle = HeadingStyle.SETEXT,
    val codeBlockStyle: CodeBlockStyle = CodeBlockStyle.INDENTED,
    val linkStyle: LinkStyle = LinkStyle.INLINED,
    val linkReferenceStyle: LinkReferenceStyle = LinkReferenceStyle.DEFAULT
)
