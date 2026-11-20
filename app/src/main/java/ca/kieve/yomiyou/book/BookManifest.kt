package ca.kieve.yomiyou.book

import kotlinx.serialization.Serializable

/** An enriched EPUB's `yomiage.json`, written by yomiage's build. */
@Serializable
data class BookManifest(
    val format: Int,
    val bookId: String,
    val title: String,
    val author: String = "",
    /** How many paragraphs: a phrase's number is its paragraph times [PHRASES_PER_PARAGRAPH] plus its place in it. */
    val paragraphs: Int? = null,
    /** In the book's own order: the default first. */
    val voices: List<VoiceInfo> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
)

@Serializable
data class VoiceInfo(val id: String, val label: String, val credit: String = "")

@Serializable
data class ChapterInfo(val number: Int, val title: String, val href: String, val firstSpan: Int)

/** Format 2: a phrase's number (`data-y`) is its paragraph, counted through the book, times this, plus its place in it. */
const val PHRASES_PER_PARAGRAPH = 1000
