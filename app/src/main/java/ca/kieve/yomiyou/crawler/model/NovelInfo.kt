package ca.kieve.yomiyou.crawler.model

data class NovelInfo(
    var title: String? = null,
    var author: String? = null,
    var coverUrl: String? = null,

    var chapters: MutableList<ChapterInfo> = arrayListOf()
)
