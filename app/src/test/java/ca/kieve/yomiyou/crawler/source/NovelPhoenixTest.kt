package ca.kieve.yomiyou.crawler.source

import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.FixtureScraper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelPhoenixTest {
    private val scraper = FixtureScraper("novelphoenix")
    private val source = NovelPhoenix(scraper)
    private val novelUrl = "https://novelphoenix.com/novel/the-primal-hunter"

    @Test
    fun search() = runBlocking {
        val results = source.search("hunter")

        assertEquals("https://novelphoenix.com/search?keyword=hunter&type=title", scraper.requested.single())
        assertEquals(20, results.size) // not the "Some Popular Novels" block
        assertEquals("The Primal Hunter", results[0].title)
        assertEquals(novelUrl, results[0].url)
        assertEquals("https://novelphoenix.com/server-1/the-primal-hunter.jpg", results[0].coverUrl)
    }

    @Test
    fun novelInfo() = runBlocking {
        val info = source.novelInfo(novelUrl)!!

        assertEquals("The Primal Hunter", info.title)
        assertEquals("Zogarth", info.author)
        assertEquals("https://novelphoenix.com/server-1/the-primal-hunter.jpg", info.coverUrl)
    }

    @Test
    fun `chapters follow rel=next and keep what they have when a page fails`() = runBlocking {
        // Fixtures hold pages 1-2 of 15; page 3 "times out".
        val chapters = source.chapters(novelUrl)

        assertEquals(
            listOf(
                "$novelUrl/chapters",
                "$novelUrl/chapters?page=2",
                "$novelUrl/chapters?page=3"
            ),
            scraper.requested
        )
        assertEquals(200, chapters.size)
        assertEquals(1L, chapters[0].id)
        assertEquals("Chapter 1 - Another Monday Morning", chapters[0].title)
        assertEquals("$novelUrl/chapter-1", chapters[0].url)
        assertEquals("$novelUrl/chapter-101", chapters[100].url)
        assertEquals(200L, chapters[199].id)
    }

    @Test
    fun `chapter html becomes clean markdown`() = runBlocking {
        val html = source.rawChapterHtml("$novelUrl/chapter-2")
        assertNotNull(html)
        assertEquals(94, html!!.select("p").size)

        val markdown = Crawler(scraper).toMarkdown(html, source.filter)

        assertTrue(markdown.startsWith("\\*Initiation of the 93rd Universe confirmed."), markdown.take(80))
        assertTrue(markdown.endsWith("Transporting to tutorial…\\*"), markdown.takeLast(80))
        assertFalse(markdown.contains("iframe"))
        assertFalse(markdown.contains("Restore scroll"))
    }
}
