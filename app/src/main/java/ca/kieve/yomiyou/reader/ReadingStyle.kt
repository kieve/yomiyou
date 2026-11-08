package ca.kieve.yomiyou.reader

/**
 * How every book looks, kept once for the whole app and handed to the reader page as CSS variables. Text size only,
 * for now; colors, the font, spacing and the rest become fields here.
 */
data class ReadingStyle(val textSize: Int = DEFAULT_TEXT_SIZE) {
    fun larger(): ReadingStyle = copy(textSize = (textSize + STEP).coerceAtMost(MAX_TEXT_SIZE))

    fun smaller(): ReadingStyle = copy(textSize = (textSize - STEP).coerceAtLeast(MIN_TEXT_SIZE))

    val variables: Map<String, String>
        get() = mapOf("--text-size" to "${textSize}px")

    companion object {
        const val DEFAULT_TEXT_SIZE = 20
        const val MIN_TEXT_SIZE = 14
        const val MAX_TEXT_SIZE = 48
        const val STEP = 2

        fun of(textSize: Int?): ReadingStyle =
            ReadingStyle(textSize?.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE) ?: DEFAULT_TEXT_SIZE)
    }
}
