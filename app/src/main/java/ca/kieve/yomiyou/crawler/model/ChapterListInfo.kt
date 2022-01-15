package ca.kieve.yomiyou.crawler.model

data class ChapterListInfo(
    val firstPageChapters: List<ChapterInfo>? = null,
    val totalPages: Int,
)
