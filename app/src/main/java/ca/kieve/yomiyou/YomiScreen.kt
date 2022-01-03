package ca.kieve.yomiyou

sealed class YomiScreen(val route: String) {
    object NovelListNav: YomiScreen("novel_list")
    object NovelSearchNav: YomiScreen("novel_search")
    object ChapterListNav: YomiScreen("chapter_list")
    object ReaderNav: YomiScreen("reader")

    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}
