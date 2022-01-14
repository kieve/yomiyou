package ca.kieve.yomiyou.data.scheduler

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
import ca.kieve.yomiyou.data.NovelRepository
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NovelScheduler(context: Context) {
    companion object {
        val TAG = getTag()
    }

    private val appContainer = (context as YomiApplication).container
    private val novelRepository: NovelRepository get() { return appContainer.novelRepository }

    private val defaultScope = CoroutineScope(context = Job() + Dispatchers.Default)

    private val runMutex = Mutex()
    private val schedulerMutex = Mutex()
    private val mainQueue = ArrayDeque<NovelJob>(listOf())

    /*
     * Metrics for determining job priority
     */

    // Active novel being viewed
    private var activeNovelId: Long? = null

    // TODO: This is unused until I create the chapter download jobs
    private var isReading: Boolean = false

    // Active search set being viewed
    private var activeSearch: Set<Long> = setOf()

    fun schedule(job: NovelJob) {
        defaultScope.launch {
            schedulerMutex.withLock {
                mainQueue.addLast(job)
                reorderJobs()
            }
            tryRunJobs()
        }
    }

    fun setActiveNovel(id: Long?) {
        Log.d(TAG, "setActiveNovel: $id")
        defaultScope.launch {
            schedulerMutex.withLock {
                activeNovelId = id
                reorderJobs()
            }
        }
    }

    fun setActiveSearch(novelIds: Collection<Long>) {
        Log.d(TAG, "setActiveSearch: ${novelIds}")
        defaultScope.launch {
            schedulerMutex.withLock {
                activeSearch = novelIds.toSet()
                reorderJobs()
            }
        }
    }

    private fun reorderJobs() {
        val result = mainQueue.sortedWith { a, b ->
            val aId = a.novel.metadata.id
            val bId = b.novel.metadata.id

            if (activeNovelId != null
                && aId != bId
            ) {
                // If there's an active novel, any related jobs take priority
                if (aId == activeNovelId) {
                    return@sortedWith -1
                } else if (bId == activeNovelId) {
                    return@sortedWith 1
                }
            }

            if (activeSearch.isNotEmpty()
                && aId != bId
            ) {
                // If there's an active search, those have next priority, as the user is likely
                // going to be viewing those.
                if (activeSearch.contains(aId)) {
                    return@sortedWith -1
                } else if (activeSearch.contains(bId)) {
                    return@sortedWith 1
                }
            }

            // When above is equal, novel info comes before chapter info
            if (a is DownloadNovelInfoJob && b !is DownloadNovelInfoJob) {
                return@sortedWith -1
            } else if (b is DownloadNovelInfoJob && a !is DownloadNovelInfoJob) {
                return@sortedWith 1
            }

            // Same type of job, and same activity status
            // Order by novel id
            (bId - aId).toInt()
        }

        mainQueue.clear()
        mainQueue.addAll(result)
    }

    private suspend fun tryRunJobs() {
        if (!runMutex.tryLock()) {
            return
        }
        runJobs()
        Log.d(TAG, "tryRunJobs: All jobs finished")
        runMutex.unlock()
    }

    private suspend fun runJobs() {
        while (true) {
            val job: NovelJob?
            schedulerMutex.withLock {
                job = mainQueue.removeFirstOrNull()
            }
            if (job == null) {
                return
            }
            Log.d(TAG, "running job: ${job.novel.metadata.id}")
            job.execute()
            if (job is DownloadNovelInfoJob) {
                novelRepository.onJobComplete(job)
            } else if (job is DownloadChapterInfoJob) {
                novelRepository.onJobComplete(job)
            }
        }
    }
}
