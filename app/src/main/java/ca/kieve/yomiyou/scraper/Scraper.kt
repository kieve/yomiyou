package ca.kieve.yomiyou.scraper

interface Scraper {
    /**
     * @param[url] URL to load
     * @return The HTML of the loaded page, when finished loading.
     */
    suspend fun loadPage(url: String): String?

    /**
     * @return The HTML of the currently loaded page.
     */
    suspend fun getCurrentPageHtml(): String?

    /**
     * @param[js] JavaScript to execute
     * @return The string result of executing the JavaScript.
     */
    suspend fun executeJs(js: String): String?
}
