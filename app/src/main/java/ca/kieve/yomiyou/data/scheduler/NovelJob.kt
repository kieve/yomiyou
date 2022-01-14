package ca.kieve.yomiyou.data.scheduler

import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.NovelInfo
import ca.kieve.yomiyou.data.model.Novel

sealed class NovelJob {
    abstract val novel: Novel
    abstract suspend fun execute()
}

class DownloadNovelInfoJob(
    override val novel: Novel,
    private val crawler: Crawler
) : NovelJob() {
    var result: NovelInfo? = null
        private set

    override suspend fun execute() {
        result = crawler.getNovelInfo(novel.metadata.url)
    }
}

class DownloadChapterInfoJob(
    override val novel: Novel,
    private val crawler: Crawler
) : NovelJob() {
    var result: List<ChapterInfo>? = null
        private set

    override suspend fun execute() {
        result = crawler.getChapterInfo(novel.metadata.url)
    }
}
