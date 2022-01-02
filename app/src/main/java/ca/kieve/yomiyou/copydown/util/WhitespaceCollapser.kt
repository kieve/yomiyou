package ca.kieve.yomiyou.copydown.util

import org.jsoup.nodes.Node
import ca.kieve.yomiyou.copydown.CopyNode
import org.jsoup.nodes.TextNode

/**
 * This is ported to Kotlin from WhitespaceCollapser.java included in the copy-down repo referenced
 * in the LICENSES directory of this repo.
 *
 * Following is the original notice included there
 *
 * -----------------------------------------------------------------------------------------------
 *
 * The Whitespace collapser is originally adapted from collapse-whitespace
 * by Luc Thevenard.
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Luc Thevenard <lucthevenard@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

private val COMMON_WHITESPACE_REGEX = """[ \r\n\t]+""".toRegex()
private val TRAILING_SPACE_REGEX = " $".toRegex()

fun Node.collapseWhitespace() {
    if (this.childNodeSize() == 0 || isPre(this)) {
        return
    }

    var prevText: TextNode? = null
    var prevVoid = false
    var prev: Node? = null
    var node = next(prev, this)

    // Traverse the tree
    while (node != null && node !== this) {
        if (isNodeType3(node) || isNodeType4(node)) {
            val textNode = node as TextNode
            var value = textNode.attributes()["#text"]
                .replace(COMMON_WHITESPACE_REGEX, " ")
            if ((prevText == null || TRAILING_SPACE_REGEX.containsMatchIn(prevText.text()))
                && !prevVoid
                && value[0] == ' ')
            {
                value = value.substring(1)
            }
            if (value.isEmpty()) {
                node = remove(node)
                continue
            }
            val newNode = TextNode(value)
            node.replaceWith(newNode)
            prevText = newNode
            node = newNode
        } else if (isNodeType1(node)) {
            if (isBlock(node)) {
                prevText?.text(prevText.text().replace(TRAILING_SPACE_REGEX, ""))
                prevText = null
                prevVoid = false
            } else if (isVoid(node)) {
                // avoid trimming space around non block, non br void elements
                prevText = null
                prevVoid = true
            }
        } else {
            node = remove(node)
            continue
        }
        val nextNode = next(prev, node)
        prev = node
        node = nextNode
    }
    if (prevText != null) {
        prevText.text(prevText.text().replace(TRAILING_SPACE_REGEX, ""))
        if (prevText.text().isBlank()) {
            remove(prevText)
        }
    }
}

/**
 * remove(node) removes the given node from the DOM and returns the
 * next node in the sequence.
 */
private fun remove(node: Node): Node? {
    val next = node.nextSibling() ?: node.parentNode()
    node.remove()
    return next
}

/**
 * Returns next node in the sequence given current and previous nodes
 */
private fun next(prev: Node?, current: Node): Node? {
    if (prev != null && prev.parent() === current || isPre(current)) {
        // TODO: beware parentNode might not be element
        return current.nextSibling() ?: current.parentNode()
    }
    if (current.childNodeSize() != 0) {
        return current.childNode(0)
    }
    return current.nextSibling() ?: current.parentNode()
}

private fun isPre(element: Node): Boolean {
    // TODO allow to override with lambda in options
    return element.nodeName() == "pre"
}

private fun isBlock(element: Node): Boolean {
    // TODO allow to override with lambda in optiosn
    return CopyNode.isBlock(element) || element.nodeName() == "br"
}

private fun isVoid(element: Node): Boolean {
    // Allow to override
    return CopyNode.isVoid(element)
}
