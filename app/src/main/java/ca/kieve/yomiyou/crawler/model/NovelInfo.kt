package ca.kieve.yomiyou.crawler.model

data class NovelInfo(
    var url: String,
    var title: String? = null,
    var author: String? = null,
    var coverUrl: String? = null,
)
