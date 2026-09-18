package ca.kieve.yomiyou.data.model

import ca.kieve.yomiyou.data.database.model.Chapter

data class OpenChapter(
    val chapter: Chapter,
    val content: String?
)
