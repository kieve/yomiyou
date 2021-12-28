package ca.kieve.yomiyou.util

import kotlin.reflect.KClass

private const val TAG_BASE = "Yomi-"

fun getTag(): String {
    val fullClassPath = Thread.currentThread().stackTrace[3].className
    val className = fullClassPath.substring(
        fullClassPath.lastIndexOf(".") + 1)
    return getTag(className)
}

fun getTag(_class: KClass<*>): String {
    return getTag(_class.java.simpleName)
}

fun getTag(tag: String): String {
    return TAG_BASE + tag
}
