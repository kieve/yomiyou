package ca.kieve.yomiyou.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel",
    indices = [Index("url", unique = true)]
)
data class Novel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val author: String? = null,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,
    @ColumnInfo(name = "in_library")
    val inLibrary: Boolean = false,
    /** Epoch millis of the last time the user opened this novel; orders background work. */
    @ColumnInfo(name = "last_opened")
    val lastOpened: Long = 0,
    /**
     * The last chapter-list page fetched (source token), where its chapters start, and whether
     * it was the final page. Resuming or refreshing re-fetches this page: a "last" page can
     * gain chapters after we saw it.
     */
    @ColumnInfo(name = "list_page")
    val listPage: String? = null,
    @ColumnInfo(name = "list_page_position")
    val listPagePosition: Int = 1,
    @ColumnInfo(name = "list_complete")
    val listComplete: Boolean = false
)
