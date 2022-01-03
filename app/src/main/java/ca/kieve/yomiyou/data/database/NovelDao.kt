package ca.kieve.yomiyou.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta

@Dao
interface NovelDao {
    @Query("SELECT * FROM novel_meta")
    suspend fun getAllNovelMeta(): List<NovelMeta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNovelMeta(novelMeta: NovelMeta): Long

    @Query("SELECT * FROM chapter_meta")
    suspend fun getAllChapterMeta(): List<ChapterMeta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapterMeta(chapterMetas: List<ChapterMeta>)

    @Query("SELECT * FROM chapter_meta WHERE novel_id = :novelId ORDER BY id")
    suspend fun getNovelChapters(novelId: Long): List<ChapterMeta>
}
