package ca.kieve.yomiyou.scraper

interface Scraper {
    suspend fun getPageHtml(url: String): String?
}
