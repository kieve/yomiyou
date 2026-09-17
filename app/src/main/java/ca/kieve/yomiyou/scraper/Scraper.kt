package ca.kieve.yomiyou.scraper

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
