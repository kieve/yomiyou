package ca.kieve.yomiyou.data.scheduler

import android.util.Log
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.data.NovelRepository
import ca.kieve.yomiyou.data.database.model.Chapter
import ca.kieve.yomiyou.data.database.model.Novel
import ca.kieve.yomiyou.util.getTag

enum class JobKind { INFO, CHAPTER_LIST, CHAPTER }

/**
 * A unit of scraping work. [step] does one page load; the scheduler re-evaluates priorities
 * between steps, so long jobs must return `false` and keep their own place.
 */
abstract class NovelJob {
    abstract val kind: JobKind
    abstract val novelId: Long
    /** Chapter position for [JobKind.CHAPTER]; null otherwise. */
    open val position: Int? = null
    /** Two jobs with the same key do the same work; the scheduler queues only one. */
    abstract val key: String

    /** @return true when finished. */
    abstract suspend fun step(): Boolean
}

class DownloadNovelInfoJob(
    private val novel: Novel,
    private val crawler: Crawler,
    private val novelRepository: NovelRepository
) : NovelJob() {
    override val kind = JobKind.INFO
    override val novelId = novel.id
    override val key = "info:${novel.id}"

    override suspend fun step(): Boolean {
        novelRepository.onNovelInfo(novel.id, crawler.getNovelInfo(novel.url))
        return true
    }
}

/** One chapter-list page per step, starting by re-fetching the novel's last fetched page. */
class DownloadChapterListJob(
    private val novel: Novel,
    private val crawler: Crawler,
    private val novelRepository: NovelRepository
) : NovelJob() {
    companion object {
        private val TAG = getTag()
    }

    override val kind = JobKind.CHAPTER_LIST
    override val novelId = novel.id
    override val key = "list:${novel.id}"

    private var page: String? = novel.listPage
    private var firstPosition: Int = novel.listPagePosition

    override suspend fun step(): Boolean {
        val result = crawler.getChapterListPage(novel.url, page, firstPosition)
        if (result == null) {
            Log.w(TAG, "chapter list stopped at position $firstPosition for ${novel.title}; will resume")
            return true
        }
        novelRepository.onChapterListPage(novel.id, result, firstPosition)
        page = result.nextPage
        firstPosition += result.chapters.size
        if (page != null) {
            return false
        }
        novelRepository.onChapterListComplete(novel.id)
        return true
    }
}

class DownloadChapterJob(
    private val chapter: Chapter,
    private val crawler: Crawler,
    private val novelRepository: NovelRepository
) : NovelJob() {
    companion object {
        private val TAG = getTag()
    }

    override val kind = JobKind.CHAPTER
    override val novelId = chapter.novelId
    override val position = chapter.position
    override val key = "chapter:${chapter.novelId}:${chapter.url}"

    override suspend fun step(): Boolean {
        if (novelRepository.isDownloaded(chapter)) {
            return true
        }
        Log.d(TAG, "Downloading ${chapter.novelId}/${chapter.position}")
        val markdown = crawler.downloadChapter(chapter.url)
        if (markdown == null) {
            Log.w(TAG, "Chapter failed to download: ${chapter.url}")
            return true
        }
        novelRepository.onChapterDownloaded(chapter, markdown)
        return true
    }
}
