package ca.kieve.yomiyou.crawler.model

data class ChapterInfo(
    /** 1-based reading order. */
    val position: Int,
    val url: String,
    val title: String
)
