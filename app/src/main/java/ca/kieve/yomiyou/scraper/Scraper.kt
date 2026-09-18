package ca.kieve.yomiyou.scraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

interface Scraper {
    /**
     * @param[url] URL to load
     * @param[waitFor] CSS selector that must be present before the page counts as loaded. Pass
     *                 the content selector so anti-bot interstitials are waited out instead of
     *                 returned.
     * @param[timeoutMs] Give up after this long.
     * @return The HTML of the loaded page, or null on timeout.
     */
    suspend fun loadPage(
        url: String,
        waitFor: String? = null,
        timeoutMs: Long = 30_000
    ): String?
}

suspend fun Scraper.soup(url: String, waitFor: String? = null): Document? {
    val html = loadPage(url, waitFor) ?: return null
    return Jsoup.parse(html, url)
}
