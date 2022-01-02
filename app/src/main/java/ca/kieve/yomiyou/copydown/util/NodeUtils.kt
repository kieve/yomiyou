package ca.kieve.yomiyou.copydown.util

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

fun isNodeType1(node: Node): Boolean {
    return node is Element
}

fun isNodeType3(node: Node): Boolean {
    return node.nodeName() == "text" || node.nodeName() == "#text"
}

fun isNodeType4(node: Node): Boolean {
    return false
}
