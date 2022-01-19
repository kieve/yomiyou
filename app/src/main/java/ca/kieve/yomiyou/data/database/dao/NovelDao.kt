package ca.kieve.yomiyou.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import ca.kieve.yomiyou.data.database.BaseDao
import ca.kieve.yomiyou.data.database.model.NovelMeta

@Dao
abstract class NovelDao : BaseDao<NovelMeta>() {
    @Query("SELECT * FROM novel_meta")
    abstract suspend fun getAllNovelMeta(): List<NovelMeta>
}
