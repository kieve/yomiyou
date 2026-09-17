package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.scraper.Scraper
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CrawlerTest {
    private val crawler = Crawler(object : Scraper {
        override suspend fun loadPage(url: String, waitFor: String?, timeoutMs: Long) = null
    })

    private fun toMarkdown(html: String) = crawler.toMarkdown(Jsoup.parseBodyFragment(html).body())

    // Both cases are the "Parse bugs" from TODO.txt.
    @Test
    fun `inline italics stay inline`() {
        assertEquals(
            "–_Dokja-ssi, this person is perhaps…_\n\nNext para.",
            toMarkdown("<p>–<i>Dokja-ssi, this person is perhaps…</i></p><p>Next para.</p>")
        )
    }

    @Test
    fun `list does not swallow the rest of the chapter`() {
        assertEquals(
            "*   _Region of Campania, Italy, December 2008_\n\nRest of the chapter.\n\nMore.",
            toMarkdown("<ul><li><em>Region of Campania, Italy, December 2008</em></li></ul><p>Rest of the chapter.</p><p>More.</p>")
        )
    }

    @Test
    fun `strips junk tags, ad css, links and invisible characters`() {
        assertEquals(
            "Keep me and this.",
            toMarkdown(
                "<div class=\"chapter\"><script>x()</script><p class=\"adsbygoogle\">ad</p>" +
                        "<p>Keep​ me <a href=\"https://x\">and</a> this­.</p></div>"
            )
        )
    }

    @Test
    fun `blacklist drops matching lines but keeps paragraph breaks`() {
        crawler.currentFilter = Crawler.DEFAULT_FILTER.copy(
            blacklistRegexes = setOf(Regex("lightnovelpub\\.com"))
        )
        assertEquals(
            "One.\n\nThree.",
            toMarkdown("<p>One.</p><p>Read at lightnovelpub.com</p><p>Three.</p>")
        )
    }
}
