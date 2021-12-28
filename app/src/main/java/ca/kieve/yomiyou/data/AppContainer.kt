package ca.kieve.yomiyou.data

import android.content.Context
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.data.database.YomiDatabase
import ca.kieve.yomiyou.data.repository.NovelRepository
import ca.kieve.yomiyou.scraper.WebViewScraper

class AppContainer(private val appContext: Context) {
    val database: YomiDatabase by lazy {
        YomiDatabase.getDatabase(appContext)
    }

    val crawler: Crawler by lazy {
        Crawler(WebViewScraper(appContext))
    }

    val novelRepository: NovelRepository by lazy {
        NovelRepository(appContext)
    }
}
