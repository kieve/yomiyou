package ca.kieve.yomiyou.data.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SchedulerPriorityTest {
    private class FakeJob(
        override val kind: JobKind,
        override val novelId: Long,
        override val position: Int? = null
    ) : NovelJob() {
        override val key = "$kind:$novelId:$position"
        override suspend fun step() = true
        override fun toString() = key
    }

    private fun info(novel: Long) = FakeJob(JobKind.INFO, novel)
    private fun list(novel: Long) = FakeJob(JobKind.CHAPTER_LIST, novel)
    private fun chapter(novel: Long, position: Int) = FakeJob(JobKind.CHAPTER, novel, position)
    private fun state(inLibrary: Boolean, lastOpened: Long = 0) =
        NovelScheduler.NovelState(inLibrary, lastOpened)

    @Test
    fun `reading chapter, next, previous come before everything, then the open novel, then background`() {
        val jobs = listOf(
            chapter(novel = 9, position = 3),   // background, recently opened
            list(novel = 9),
            chapter(novel = 1, position = 10),  // reading novel, far away
            chapter(novel = 1, position = 6),   // previous
            list(novel = 1),                    // open novel's list
            chapter(novel = 1, position = 8),   // next
            info(novel = 1),                    // open novel's info
            chapter(novel = 1, position = 7),   // current
            chapter(novel = 5, position = 1),   // background, opened longer ago
            info(novel = 5)
        )
        val states = mapOf(
            1L to state(inLibrary = true, lastOpened = 100),
            5L to state(inLibrary = true, lastOpened = 10),
            9L to state(inLibrary = true, lastOpened = 50)
        )

        val sorted = sortJobs(jobs, states, activeNovelId = 1, reading = NovelScheduler.Reading(1, 7))

        assertEquals(
            listOf(
                "CHAPTER:1:7", "CHAPTER:1:8", "CHAPTER:1:6",      // tier 1: reading
                "INFO:1:null", "CHAPTER_LIST:1:null", "CHAPTER:1:10", // tier 2: open novel
                "CHAPTER:9:3", "CHAPTER:5:1",                     // tier 3: chapters, most recent first
                "CHAPTER_LIST:9:null", "INFO:5:null"              // then lists, then info
            ),
            sorted.map { it.toString() }
        )
    }

    @Test
    fun `leaving a non-library novel drops its jobs but keeps library work`() {
        val jobs = listOf(
            list(novel = 2),
            chapter(novel = 2, position = 1),
            list(novel = 3),
            chapter(novel = 4, position = 1)
        )
        val states = mapOf(3L to state(inLibrary = true))

        val kept = jobs.filter { keepWhenInactive(it, states, activeNovelId = 4) }

        assertEquals(listOf("CHAPTER_LIST:3:null", "CHAPTER:4:1"), kept.map { it.toString() })
    }
}
