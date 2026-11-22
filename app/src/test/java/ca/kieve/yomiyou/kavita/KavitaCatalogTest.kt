package ca.kieve.yomiyou.kavita

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KavitaCatalogTest {
    private val novels = KavitaLibrary(id = 2, name = "Novels", type = 2, folders = listOf("/media/novels/"))

    private fun volume(id: Int, vararg chapters: KavitaChapter) = KavitaVolume(id, chapters = chapters.toList())

    private fun chapter(id: Int, title: String, vararg paths: String) =
        KavitaChapter(id, titleName = title, files = paths.map { KavitaFile(it, bytes = 1000L * id) })

    @Test
    fun `each EPUB is a book, its path the one under the library's folder`() {
        val series = listOf(
            KavitaSeries(10, "夢", 2),
            KavitaSeries(11, "Capital", 2),
            KavitaSeries(12, "Saga", 2),
            KavitaSeries(13, "Elsewhere", 1),
        )
        val volumes = mapOf(
            10 to listOf(volume(100, chapter(1000, "夢", "/media/novels/夢/夢 - 作者.epub"))),
            11 to listOf(volume(110, chapter(1100, "Capital", "/media/novels/Capital/Capital.pdf"))),
            12 to listOf(
                volume(120, chapter(1200, "Saga 1", "/media/novels/Saga/Saga 1.epub")),
                volume(121, chapter(1210, "", "/media/novels/Saga/Saga 2.EPUB")),
            ),
            13 to listOf(volume(130, chapter(1300, "x", "/media/manga/x.epub"))),
        )

        val books = KavitaCatalog.books(novels, series, volumes)

        assertEquals(
            listOf(
                KavitaBook(2, 10, 100, 1000, "夢", "夢/夢 - 作者.epub", 1_000_000, alone = true),
                KavitaBook(2, 12, 120, 1200, "Saga 1", "Saga/Saga 1.epub", 1_200_000, alone = false),
                KavitaBook(2, 12, 121, 1210, "Saga 2", "Saga/Saga 2.EPUB", 1_210_000, alone = false),
            ),
            books
        )
    }

    @Test
    fun `paths compare in one Unicode form`() {
        // が as か plus a combining mark, as some file systems store it.
        val decomposed = "/media/novels/が/本.epub"
        assertEquals("が/本.epub", KavitaCatalog.relative(decomposed, listOf("/media/novels")))
    }

    @Test
    fun `a book folder's link to Kavita reads back`() {
        val link = KavitaLink(2, 10, 100, 1000, "夢/夢.epub")
        assertEquals(link, KavitaLink.parse(link.encode()))
    }
}
