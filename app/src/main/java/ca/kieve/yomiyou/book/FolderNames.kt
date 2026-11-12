package ca.kieve.yomiyou.book

/** Book folders and their files are named after their titles, so they read well in a file manager or a backup. */
object FolderNames {
    /** A name holds 255 bytes: about 85 Japanese characters. */
    private const val MAX_BYTES = 255
    private const val FORBIDDEN = "\\/:*?\"<>|"

    /** A folder name for [title] that isn't in [taken]; shared storage ignores case, so neither does this. */
    fun of(title: String, taken: Set<String>): String {
        val clean = clean(title)
        val lowerTaken = taken.mapTo(HashSet()) { it.lowercase() }
        var number = 1
        while (true) {
            val suffix = if (number == 1) "" else " ($number)"
            val name = fit(clean, MAX_BYTES - suffix.encodeToByteArray().size) + suffix
            if (name.lowercase() !in lowerTaken) return name
            number++
        }
    }

    /** A file name: [title], shortened to leave room for [ending] (`.yomiage.epub`, say). */
    fun file(title: String, ending: String): String =
        fit(clean(title), MAX_BYTES - ending.encodeToByteArray().size) + ending

    private fun clean(title: String): String =
        title.map { if (it in FORBIDDEN || it.code < 0x20) '_' else it }
            .joinToString("")
            .trim()
            .trimEnd('.', ' ')
            .ifEmpty { "Book" }

    /** The longest start of [text] within [bytes] of UTF-8, never splitting a character. */
    private fun fit(text: String, bytes: Int): String {
        var used = 0
        var end = 0
        while (end < text.length) {
            val next = text.offsetByCodePoints(end, 1)
            val size = text.substring(end, next).encodeToByteArray().size
            if (used + size > bytes) break
            used += size
            end = next
        }
        return text.substring(0, end).trimEnd('.', ' ')
    }
}
