package ca.kieve.yomiyou.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "chapter_meta",
    primaryKeys = [ "id", "novel_id" ],
    foreignKeys = [
        ForeignKey(
            entity = NovelMeta::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("novel_id"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChapterMeta(
    // Not auto-generated, this maps to the chapter index from the crawled website
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "novel_id", index = true)
    val novelId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "url")
    val url: String
)
