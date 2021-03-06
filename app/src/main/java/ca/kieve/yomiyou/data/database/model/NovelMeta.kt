package ca.kieve.yomiyou.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ca.kieve.yomiyou.data.database.HasId

@Entity(tableName = "novel_meta")
data class NovelMeta(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    override val id: Long = 0,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String?,

    @ColumnInfo(name = "cover_url")
    val coverUrl: String?,

    @ColumnInfo(name = "in_library")
    val inLibrary: Boolean = false
) : HasId

