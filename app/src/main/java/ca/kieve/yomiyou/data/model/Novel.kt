package ca.kieve.yomiyou.data.model

import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import java.io.File

data class Novel(
    val metadata: NovelMeta,
    val chapters: List<ChapterMeta> = emptyList(),

    val coverFile: File? = null,
    val chapterFiles: Map<Long, File> = emptyMap()
)
