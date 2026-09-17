package ca.kieve.yomiyou.data.database

import androidx.room.Upsert

abstract class BaseDao<T> {
    /** @return the rowId when inserted, -1 when an existing row was updated. */
    @Upsert
    abstract suspend fun upsert(obj: T): Long

    @Upsert
    abstract suspend fun upsert(objList: List<T>)
}
