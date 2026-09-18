package ca.kieve.yomiyou.crawler.model

data class ChapterListPage(
    val chapters: List<ChapterInfo>,
    /** Token that re-fetches this page. */
    val page: String,
    /** Token for the following page; null on the last page. */
    val nextPage: String?
)
