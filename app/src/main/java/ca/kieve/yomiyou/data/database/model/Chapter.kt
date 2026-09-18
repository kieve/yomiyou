package ca.kieve.yomiyou.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chapter",
    primaryKeys = ["novel_id", "url"],
    indices = [Index("novel_id")],
    foreignKeys = [
        ForeignKey(
            entity = Novel::class,
            parentColumns = ["id"],
            childColumns = ["novel_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Chapter(
    @ColumnInfo(name = "novel_id")
    val novelId: Long,
    val url: String,
    /** 1-based reading order. Can change when the site inserts a chapter; [url] can't. */
    val position: Int,
    val title: String,
    val downloaded: Boolean = false
)
