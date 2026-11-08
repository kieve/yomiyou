package ca.kieve.yomiyou.book

import kotlinx.serialization.Serializable

/** An enriched EPUB's `yomiage.json`, written by yomiage's build. */
@Serializable
data class BookManifest(
    val format: Int,
    val bookId: String,
    val title: String,
    val author: String = "",
    /** Missing in books built before it was added. */
    val phrases: Int? = null,
    /** In the book's own order: the default first. */
    val voices: List<VoiceInfo> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
)

@Serializable
data class VoiceInfo(val id: String, val label: String, val credit: String = "")

@Serializable
data class ChapterInfo(val number: Int, val title: String, val href: String, val firstSpan: Int)
