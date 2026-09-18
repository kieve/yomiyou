package ca.kieve.yomiyou.data

import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.data.database.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MergeChaptersTest {
    @Test
    fun `downloaded flag follows the url when the site inserts a chapter`() {
        val existing = listOf(
            Chapter(novelId = 7, url = "n/chapter-1", position = 1, title = "One", downloaded = true),
            Chapter(novelId = 7, url = "n/chapter-2", position = 2, title = "Two", downloaded = true)
        )
        val crawled = listOf(
            ChapterInfo(position = 1, url = "n/chapter-1", title = "One"),
            ChapterInfo(position = 2, url = "n/chapter-1-5", title = "Interlude"),
            ChapterInfo(position = 3, url = "n/chapter-2", title = "Two (edited)")
        )

        val merged = mergeChapters(7, existing, crawled)

        assertEquals(
            listOf(
                Chapter(7, "n/chapter-1", 1, "One", downloaded = true),
                Chapter(7, "n/chapter-1-5", 2, "Interlude", downloaded = false),
                Chapter(7, "n/chapter-2", 3, "Two (edited)", downloaded = true)
            ),
            merged
        )
    }
}
