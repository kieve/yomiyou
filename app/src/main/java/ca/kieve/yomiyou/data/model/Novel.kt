package ca.kieve.yomiyou.data.model

import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta

data class Novel(
    val metadata: NovelMeta,
    val chapters: List<ChapterMeta> = emptyList()
)
