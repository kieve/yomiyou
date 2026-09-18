package ca.kieve.yomiyou.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ca.kieve.yomiyou.data.database.model.LibraryNovel
import ca.kieve.yomiyou.data.database.model.Novel
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query(
        """SELECT novel.*,
           (SELECT COUNT(*) FROM chapter WHERE chapter.novel_id = novel.id) AS chapter_count,
           (SELECT COUNT(*) FROM chapter WHERE chapter.novel_id = novel.id AND downloaded = 1) AS downloaded_count
           FROM novel WHERE in_library = 1 ORDER BY title"""
    )
    fun library(): Flow<List<LibraryNovel>>

    @Query("SELECT * FROM novel WHERE in_library = 1")
    suspend fun libraryNow(): List<Novel>

    @Query("SELECT * FROM novel WHERE id = :id")
    fun novel(id: Long): Flow<Novel?>

    @Query("SELECT * FROM novel WHERE id = :id")
    suspend fun get(id: Long): Novel?

    @Query("SELECT * FROM novel WHERE url = :url")
    suspend fun getByUrl(url: String): Novel?

    /** @return the rowId when inserted, -1 when an existing row was updated. */
    @Upsert
    suspend fun upsert(novel: Novel): Long

    @Query("UPDATE novel SET in_library = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(id: Long, inLibrary: Boolean)

    @Query("UPDATE novel SET last_opened = :time WHERE id = :id")
    suspend fun setLastOpened(id: Long, time: Long)

    @Query("UPDATE novel SET list_page = :page, list_page_position = :position, list_complete = :complete WHERE id = :id")
    suspend fun setListPage(id: Long, page: String, position: Int, complete: Boolean)
}
