package ca.kieve.yomiyou.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class LibraryNovel(
    @Embedded
    val novel: Novel,
    @ColumnInfo(name = "chapter_count")
    val chapterCount: Int,
    @ColumnInfo(name = "downloaded_count")
    val downloadedCount: Int
)
