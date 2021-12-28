package ca.kieve.yomiyou.data.repository

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiyouApplication
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NovelRepository(context: Context) {
    companion object {
        private val TAG: String = getTag()
    }

    private val appContainer = (context as YomiyouApplication).container
    private val crawler = appContainer.crawler

    suspend fun crawlNovelInfo(url: String) = withContext(Dispatchers.IO) {
        crawler.initCrawl(url)
        val novelInfo = crawler.crawl(url)
        Log.d(TAG, "crawlNovelInfo: $novelInfo")
    }
}
