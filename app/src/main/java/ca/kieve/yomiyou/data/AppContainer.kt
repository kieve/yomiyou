package ca.kieve.yomiyou.data

import android.content.Context
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.data.database.YomiDatabase
import ca.kieve.yomiyou.data.scheduler.NovelScheduler
import ca.kieve.yomiyou.scraper.WebViewScraper

class AppContainer(val appContext: Context) {
    val database: YomiDatabase by lazy {
        YomiDatabase.create(appContext)
    }

    val files: YomiFiles by lazy {
        YomiFiles(appContext)
    }

    val novelScheduler: NovelScheduler by lazy {
        NovelScheduler()
    }

    // WebView must be created on the main thread; first touch is from Compose, which is.
    val scraper: WebViewScraper by lazy {
        WebViewScraper(appContext)
    }

    val crawler: Crawler by lazy {
        Crawler(scraper)
    }

    val novelRepository: NovelRepository by lazy {
        NovelRepository(database, files, crawler, novelScheduler)
    }
}
