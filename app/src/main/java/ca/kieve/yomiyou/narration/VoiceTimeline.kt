package ca.kieve.yomiyou.narration

/** One phrase's place in the audio: its segment, and its start and stop in seconds into that segment. */
data class Timing(val phrase: Int, val chapter: Int, val segment: Int, val start: Double, val stop: Double)

/** A voice's timings for the whole book, for finding the phrase being spoken and where a phrase is spoken. */
class VoiceTimeline private constructor(private val bySegment: Map<Int, List<Timing>>, private val ordered: List<Timing>) {
    private val byPhrase = ordered.associateBy { it.phrase }

    /** The chapter (by place in the book's list) each segment belongs to. */
    val segmentChapters: Map<Int, Int> = bySegment.mapValues { (_, timings) -> timings.first().chapter }

    /**
     * The phrase being spoken at [seconds] into segment [segment]: the last one started, as a pause belongs to the
     * phrase before it. Null before the segment's first phrase.
     */
    fun phraseAt(segment: Int, seconds: Double): Timing? {
        val timings = bySegment[segment] ?: return null
        // A seek to a phrase's start can land a hair before it; without the margin the phrase before would be found.
        val now = seconds + SEEK_MARGIN
        var low = 0
        var high = timings.size - 1
        if (timings[0].start > now) return null
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (timings[middle].start <= now) low = middle else high = middle - 1
        }
        return timings[low]
    }

    /** When the phrase after the one [phraseAt] finds starts, in seconds into [segment]; null after the last. */
    fun nextStart(segment: Int, seconds: Double): Double? {
        val timings = bySegment[segment] ?: return null
        val now = seconds + SEEK_MARGIN
        var low = 0
        var high = timings.size
        while (low < high) {
            val middle = (low + high) / 2
            if (timings[middle].start <= now) low = middle + 1 else high = middle
        }
        return timings.getOrNull(low)?.start
    }

    /** Where [phrase] is spoken, or the next phrase that is: some (a title page, say) have no audio. */
    fun from(phrase: Int): Timing? = byPhrase[phrase] ?: ordered.firstOrNull { it.phrase > phrase }

    /** The first phrase spoken in [chapter], or in the first chapter after it with audio. */
    fun chapterStart(chapter: Int): Timing? = ordered.firstOrNull { it.chapter >= chapter }

    companion object {
        const val SEEK_MARGIN = 0.02

        /**
         * @param timings rows of `[phrase, segment, start, stop]` by chapter number
         * @param chapterNumbers the book's chapter numbers, by place in its chapter list
         */
        fun of(timings: Map<Int, List<List<Double>>>, chapterNumbers: List<Int>): VoiceTimeline {
            val places = chapterNumbers.withIndex().associate { (index, number) -> number to index }
            val ordered = timings.flatMap { (number, rows) ->
                val chapter = places[number] ?: return@flatMap emptyList()
                rows.map { Timing(it[0].toInt(), chapter, it[1].toInt(), it[2], it[3]) }
            }.sortedBy { it.phrase }
            val bySegment = ordered.groupBy { it.segment }.mapValues { (_, list) -> list.sortedBy { it.start } }
            return VoiceTimeline(bySegment, ordered)
        }
    }
}
