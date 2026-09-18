package ca.kieve.yomiyou.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ca.kieve.yomiyou.data.database.model.Chapter
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter WHERE novel_id = :novelId ORDER BY position")
    fun chapters(novelId: Long): Flow<List<Chapter>>

    @Query("SELECT * FROM chapter WHERE novel_id = :novelId ORDER BY position")
    suspend fun chaptersNow(novelId: Long): List<Chapter>

    @Query("SELECT * FROM chapter WHERE novel_id = :novelId AND position = :position")
    fun chapter(novelId: Long, position: Int): Flow<Chapter?>

    @Query("SELECT * FROM chapter WHERE novel_id = :novelId AND url = :url")
    suspend fun get(novelId: Long, url: String): Chapter?

    @Query("SELECT * FROM chapter WHERE novel_id = :novelId AND position BETWEEN :from AND :to ORDER BY position")
    suspend fun range(novelId: Long, from: Int, to: Int): List<Chapter>

    @Query("SELECT * FROM chapter WHERE novel_id = :novelId AND downloaded = 0 ORDER BY position")
    suspend fun notDownloaded(novelId: Long): List<Chapter>

    @Upsert
    suspend fun upsert(chapters: List<Chapter>)

    @Query("UPDATE chapter SET downloaded = 1 WHERE novel_id = :novelId AND url = :url")
    suspend fun markDownloaded(novelId: Long, url: String)
}
