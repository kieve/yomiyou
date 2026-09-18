package ca.kieve.yomiyou.data.scheduler

import android.util.Log
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.data.AppContainer
import ca.kieve.yomiyou.data.NovelRepository
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.model.Novel
import ca.kieve.yomiyou.util.getTag

sealed class NovelJob {
    abstract val novel: Novel
    abstract suspend fun execute()
}

class DownloadNovelInfoJob(
    override val novel: Novel,
    private val crawler: Crawler,
    private val novelRepository: NovelRepository
) : NovelJob() {
    override suspend fun execute() {
        novelRepository.onNovelInfoUpdate(
            novelId = novel.metadata.id,
            novelInfo = crawler.getNovelInfo(novel.metadata.url)
        )
    }
}

class DownloadChapterListJob(
    override val novel: Novel,
    private val crawler: Crawler,
    private val novelRepository: NovelRepository
) : NovelJob() {
    override suspend fun execute() {
        novelRepository.onChapterListUpdate(
            novelId = novel.metadata.id,
            chapterInfoList = crawler.getChapters(novel.metadata.url)
        )
    }
}

class DownloadChapterJob(
    override val novel: Novel,
    private val chapterMeta: ChapterMeta,
    appContainer: AppContainer
) : NovelJob() {
    companion object {
        val TAG = getTag()
    }

    private val crawler = appContainer.crawler
    private val files = appContainer.files
    private val novelRepository = appContainer.novelRepository

    override suspend fun execute() {
        Log.d(TAG, "Downloading: ${chapterMeta.novelId},${chapterMeta.id}")

        val markdown = crawler.downloadChapter(chapterMeta.url)
        if (markdown == null) {
            Log.d(TAG, "Chapter failed to download")
            return
        }

        val chapterFile = files.writeChapter(
            chapterMeta = chapterMeta,
            content = markdown
        )
        if (chapterFile == null || !chapterFile.exists()) {
            Log.w(TAG, "DownloadChapterJob: Chapter failed to save")
            return
        }

        novelRepository.onChapterDownloaded(
            chapterMeta = chapterMeta,
            chapterFile = chapterFile
        )
    }
}
