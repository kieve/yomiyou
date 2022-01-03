package ca.kieve.yomiyou.data.model

import ca.kieve.yomiyou.data.database.model.ChapterMeta

data class OpenChapter(
    val chapterMeta: ChapterMeta,
    val content: String
)
