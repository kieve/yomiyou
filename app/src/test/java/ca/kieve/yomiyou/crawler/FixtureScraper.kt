package ca.kieve.yomiyou.crawler

import ca.kieve.yomiyou.scraper.Scraper
import org.jsoup.Jsoup
import java.io.File

/**
 * Serves saved pages from `src/test/resources/sources/<source>/`, mapped by that folder's
 * `urls.txt` (`<file>  <url>` per line). Unknown URLs and pages missing the [Scraper.loadPage]
 * `waitFor` selector return null, the same as a timeout on device.
 */
class FixtureScraper(source: String) : Scraper {
    private val dir = File(javaClass.getResource("/sources/$source/urls.txt")!!.toURI()).parentFile

    private val files: Map<String, File> = File(dir, "urls.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line ->
            val (file, url) = line.trim().split(Regex("\\s+"), limit = 2)
            url to File(dir, file)
        }

    val requested = mutableListOf<String>()

    override suspend fun loadPage(url: String, waitFor: String?, timeoutMs: Long): String? {
        requested += url
        val file = files[url] ?: return null
        check(!file.isUtf16()) { "${file.name} is UTF-16; fixtures must be UTF-8 (tools/capture-fixture.ps1)" }
        val doc = Jsoup.parse(file, "UTF-8", url)
        if (waitFor != null && doc.selectFirst(waitFor) == null) {
            return null
        }
        return doc.outerHtml()
    }

    private fun File.isUtf16(): Boolean {
        val bom = inputStream().use { it.readNBytes(2) }
        return bom.contentEquals(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
                || bom.contentEquals(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
    }
}
