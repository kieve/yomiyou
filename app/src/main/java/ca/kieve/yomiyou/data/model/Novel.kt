package ca.kieve.yomiyou.data.model

import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import java.io.File

data class Novel(
    /**
     * True iff the user has chosen to add this to the library.
     * If not in the library:
     *   - Meta data is not stored in the database
     *   - Chapter bodies are not downloaded
     */
    val inLibrary: Boolean,

    val metadata: NovelMeta,
    val chapters: List<ChapterMeta> = emptyList(),

    val coverFile: File? = null,
    val chapterFiles: Map<Long, File> = emptyMap()
)
