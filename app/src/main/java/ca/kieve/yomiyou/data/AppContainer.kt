package ca.kieve.yomiyou.data

import android.content.Context
import ca.kieve.yomiyou.YomiActivity
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.data.database.YomiDatabase
import ca.kieve.yomiyou.data.scheduler.NovelScheduler
import ca.kieve.yomiyou.scraper.WebViewScraper

class AppContainer(val appContext: Context) {
    lateinit var activity: YomiActivity

    val database: YomiDatabase by lazy {
        YomiDatabase.getDatabase(appContext)
    }

    val files: YomiFiles by lazy {
        YomiFiles(appContext)
    }

    val novelRepository: NovelRepository by lazy {
        NovelRepository(appContext)
    }

    val novelScheduler: NovelScheduler by lazy {
        NovelScheduler()
    }

    val crawler: Crawler by lazy {
        Crawler(WebViewScraper(appContext))
    }
}
