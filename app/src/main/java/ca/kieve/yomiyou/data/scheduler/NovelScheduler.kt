package ca.kieve.yomiyou.data.scheduler

import android.util.Log
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs scraping jobs one page load at a time, always working on what the user is looking at.
 *
 * Only one page loads at any moment (sites flag parallel fetches as bots), and the user can
 * navigate anywhere at any time, so between every page load the queue is re-sorted and the
 * head job gets the next step. Long jobs (chapter lists) do one page per [NovelJob.step] so
 * they can be preempted. Search runs outside this queue: the WebView mutex is FIFO, so a
 * search gets the very next slot without going through here.
 *
 * Priority, highest first:
 *  1. The chapter being read, then the next one, then the previous one — so paging in the
 *     reader never waits on background work.
 *  2. The novel on screen: its info, then its chapter-list pages, then its chapters.
 *  3. Everything else: chapter downloads for library novels (most recently opened novel
 *     first), then their chapter lists.
 *
 * Work for novels that are not in the library is dropped as soon as the user navigates
 * away from them; what was fetched stays cached, and the list resumes from the novel's
 * last fetched page if it is opened again. Library membership and recency are tracked per
 * novel via [setNovelState], so adding a novel to the library protects its queued jobs.
 */
class NovelScheduler {
    companion object {
        val TAG = getTag()
    }

    data class Reading(val novelId: Long, val position: Int)
    data class NovelState(val inLibrary: Boolean, val lastOpened: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val runMutex = Mutex()
    private val queueMutex = Mutex()
    private val queue = ArrayDeque<NovelJob>()
    private val queuedKeys = HashSet<String>()

    private val novelStates = HashMap<Long, NovelState>()
    private var activeNovelId: Long? = null
    private var reading: Reading? = null

    fun setNovelState(novelId: Long, inLibrary: Boolean, lastOpened: Long) {
        scope.launch {
            queueMutex.withLock {
                novelStates[novelId] = NovelState(inLibrary, lastOpened)
                dropInactiveNonLibraryJobs()
                sortQueue()
            }
        }
    }

    fun schedule(vararg jobs: NovelJob) = schedule(jobs.asList())

    fun schedule(jobs: Collection<NovelJob>) {
        if (jobs.isEmpty()) return
        scope.launch {
            queueMutex.withLock {
                for (job in jobs) {
                    if (queuedKeys.add(job.key)) {
                        queue.addLast(job)
                    }
                }
                sortQueue()
            }
            tryRunJobs()
        }
    }

    /** The novel whose screen is open, or null. Clears any reading position. */
    fun setActiveNovel(id: Long?) {
        Log.d(TAG, "setActiveNovel: $id")
        scope.launch {
            queueMutex.withLock {
                activeNovelId = id
                reading = null
                dropInactiveNonLibraryJobs()
                sortQueue()
            }
        }
    }

    fun setReading(novelId: Long, position: Int) {
        Log.d(TAG, "setReading: $novelId/$position")
        scope.launch {
            queueMutex.withLock {
                activeNovelId = novelId
                reading = Reading(novelId, position)
                dropInactiveNonLibraryJobs()
                sortQueue()
            }
        }
    }

    private fun dropInactiveNonLibraryJobs() {
        val dropped = queue.filterNot { keepWhenInactive(it, novelStates, activeNovelId) }
        if (dropped.isEmpty()) return
        Log.d(TAG, "dropping ${dropped.size} jobs for novels no longer on screen")
        queue.removeAll(dropped)
        dropped.forEach { queuedKeys.remove(it.key) }
    }

    private fun sortQueue() {
        val sorted = sortJobs(queue, novelStates, activeNovelId, reading)
        queue.clear()
        queue.addAll(sorted)
    }

    private suspend fun tryRunJobs() {
        if (!runMutex.tryLock()) {
            return
        }
        try {
            runJobs()
            Log.d(TAG, "tryRunJobs: All jobs finished")
        } finally {
            runMutex.unlock()
        }
    }

    private suspend fun runJobs() {
        while (true) {
            val job = queueMutex.withLock { queue.firstOrNull() } ?: return
            val done = try {
                job.step()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "job ${job.key} failed", e)
                true
            }
            if (done) {
                queueMutex.withLock {
                    queue.remove(job)
                    queuedKeys.remove(job.key)
                }
            }
        }
    }
}

internal fun keepWhenInactive(
    job: NovelJob,
    states: Map<Long, NovelScheduler.NovelState>,
    activeNovelId: Long?
): Boolean {
    return states[job.novelId]?.inLibrary == true || job.novelId == activeNovelId
}

internal fun sortJobs(
    jobs: Collection<NovelJob>,
    states: Map<Long, NovelScheduler.NovelState>,
    activeNovelId: Long?,
    reading: NovelScheduler.Reading?
): List<NovelJob> {
    fun readingRank(job: NovelJob): Int? {
        reading ?: return null
        if (job.kind != JobKind.CHAPTER || job.novelId != reading.novelId) return null
        return when (job.position) {
            reading.position -> 0
            reading.position + 1 -> 1
            reading.position - 1 -> 2
            else -> null
        }
    }

    fun tier(job: NovelJob): Int = when {
        readingRank(job) != null -> 0
        job.novelId == activeNovelId -> 1
        else -> 2
    }

    fun withinTier(job: NovelJob): Int = when (tier(job)) {
        0 -> readingRank(job)!!
        1 -> when (job.kind) {
            JobKind.INFO -> 0
            JobKind.CHAPTER_LIST -> 1
            JobKind.CHAPTER -> 2
        }
        else -> when (job.kind) {
            JobKind.CHAPTER -> 0
            JobKind.CHAPTER_LIST -> 1
            JobKind.INFO -> 2
        }
    }

    fun lastOpened(job: NovelJob): Long = states[job.novelId]?.lastOpened ?: 0

    return jobs.sortedWith(
        compareBy({ tier(it) }, { withinTier(it) }, { -lastOpened(it) }, { it.position ?: 0 })
    )
}
