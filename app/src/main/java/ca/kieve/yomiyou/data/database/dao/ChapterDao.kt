package ca.kieve.yomiyou.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import ca.kieve.yomiyou.data.database.BaseDao
import ca.kieve.yomiyou.data.database.model.ChapterMeta

@Dao
abstract class ChapterDao : BaseDao<ChapterMeta>() {
    @Query("SELECT * FROM chapter_meta")
    abstract suspend fun getAllChapterMeta(): List<ChapterMeta>

    @Query("SELECT * FROM chapter_meta WHERE novel_id = :novelId ORDER BY id")
    abstract suspend fun getNovelChapters(novelId: Long): List<ChapterMeta>
}
