package ca.kieve.yomiyou.data.scheduler

import android.util.Log
import ca.kieve.yomiyou.copydown.CopyDown
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.model.ChapterInfo
import ca.kieve.yomiyou.crawler.model.ChapterListInfo
import ca.kieve.yomiyou.data.AppContainer
import ca.kieve.yomiyou.data.NovelRepository
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.model.Novel
import ca.kieve.yomiyou.util.getTag

sealed class MultiStepJob {
    abstract val novel: Novel
    abstract var isDone: Boolean protected set
    abstract suspend fun executeNextStep()
}

sealed class SingleStepJob: MultiStepJob() {
    abstract override val novel: Novel
    abstract suspend fun execute()

    override var isDone: Boolean = false
    override suspend fun executeNextStep() {
        execute()
        isDone = true
    }
}

class DownloadNovelInfoJob(
    override val novel: Novel,
    private val crawler: Crawler,
    private val novelRepository: NovelRepository
) : SingleStepJob() {
    override suspend fun execute() {
        novelRepository.onNovelInfoUpdate(
            novelId = novel.metadata.id,
            novelInfo = crawler.getNovelInfo(novel.metadata.url)
        )
    }
}

class DownloadChapterInfoJob(
    override val novel: Novel,
    private val crawler: Crawler,
    private val novelRepository: NovelRepository
) : MultiStepJob() {
    companion object {
        private val TAG = getTag()
    }

    override var isDone: Boolean = false

    private var chapterListInfo: ChapterListInfo? = null
    private var nextPage: Int = 0
    private val chapters: MutableList<ChapterInfo> = arrayListOf()

    override suspend fun executeNextStep() {
        isDone = if (chapterListInfo == null) {
            getInitialInfo()
        } else {
            getNextPage()
        }
    }

    private suspend fun getInitialInfo(): Boolean {
        val info = crawler.getChapterListInfo(novel.metadata.url)
        chapterListInfo = info

        if (info.totalPages == 0) {
            Log.d(TAG, "Can't get chapter list info for ${novel.metadata.title}")
            return true
        }

        nextPage = 1
        if (info.firstPageChapters != null) {
            chapters.addAll(info.firstPageChapters)
            novelRepository.onChapterListUpdate(novel.metadata.id, info.firstPageChapters)

            if (info.totalPages == 1) {
                return true
            }
            nextPage = 2
        }

        return false
    }

    private suspend fun getNextPage(): Boolean {
        val info = chapterListInfo ?: return true

        val newChapters = crawler.getChapterListPage(novel.metadata.url, nextPage++)
        val renumbered: MutableList<ChapterInfo> = arrayListOf()
        for (chapter in newChapters) {
            chapters.add(chapter)
            renumbered.add(chapter.copy(
                id = chapters.size.toLong()
            ))
        }

        novelRepository.onChapterListUpdate(
            novelId = novel.metadata.id,
            chapterInfoList = renumbered
        )
        return nextPage > info.totalPages
    }
}

class DownloadChapterJob(
    override val novel: Novel,
    private val chapterMeta: ChapterMeta,
    private val appContainer: AppContainer
) : SingleStepJob() {
    companion object {
        val TAG = getTag()
    }

    private val crawler = appContainer.crawler
    private val files = appContainer.files
    private val novelRepository = appContainer.novelRepository

    override suspend fun execute() {
        val debugDir = "novels/${chapterMeta.novelId} - ${novel.metadata.title}"
        Log.d(TAG, "Downloading: debug = $debugDir")

        val htmlNode = crawler.downloadChapter(chapterMeta.url)
        files.writeDebugFile(
            appContainer = appContainer,
            filePath = "$debugDir/${chapterMeta.id}.html",
            contents = htmlNode?.html() ?: "HTML was empty"
        )
        if (htmlNode == null) {
            Log.d(TAG, "HTML node was null")
            return
        }

        val extracted = crawler.extractContents(htmlNode)
        files.writeDebugFile(
            appContainer = appContainer,
            filePath = "$debugDir/${chapterMeta.id}.extracted.html",
            contents = extracted ?: "HTML was empty"
        )

        val markdown = CopyDown().convert(extracted)
        val debugMd = if (markdown.isBlank()) "Markdown was empty" else markdown
        files.writeDebugFile(
            appContainer = appContainer,
            filePath = "$debugDir/${chapterMeta.id}.md",
            contents = debugMd
        )

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
