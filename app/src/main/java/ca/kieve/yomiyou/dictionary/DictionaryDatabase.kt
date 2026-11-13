package ca.kieve.yomiyou.dictionary

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A word in one sense, as the popup shows it; the fields are those of yomiage's `DictionaryEntry`. */
@Serializable
@Entity(tableName = "entries", indices = [Index("term"), Index("reading")])
data class DictionaryEntry(
    val term: String,
    val reading: String,
    /** The kinds of word it inflects as: "v5", "v1", "adj-i"... */
    val rules: List<String>,
    val score: Int,
    /** Part of speech and other notes, as the dictionary's tag names. */
    val tags: List<String>,
    val meanings: List<String>,
    /** Senses of one word share it. */
    val sequence: Int,
    @PrimaryKey(autoGenerate = true) @kotlinx.serialization.Transient val id: Long = 0,
)

/** The dictionary in the database; written last by an import, so an import cut short leaves none. */
@Entity(tableName = "info")
data class StoredInfo(
    @PrimaryKey val key: Int = 0,
    val title: String,
    val revision: String,
    val attribution: String,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
)

@Dao
interface DictionaryDao {
    @Insert
    suspend fun insert(entries: List<DictionaryEntry>)

    @Query("SELECT * FROM entries WHERE term IN (:texts) OR reading IN (:texts)")
    suspend fun find(texts: List<String>): List<DictionaryEntry>

    @Query("SELECT * FROM info WHERE `key` = 0")
    suspend fun info(): StoredInfo?

    @Upsert
    suspend fun setInfo(info: StoredInfo)

    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM info")
    suspend fun clearInfo()
}

class Lists {
    @TypeConverter
    fun toText(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun toList(text: String): List<String> = Json.decodeFromString(text)
}

@Database(version = 1, entities = [DictionaryEntry::class, StoredInfo::class])
@TypeConverters(Lists::class)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dao(): DictionaryDao

    companion object {
        fun create(context: Context): DictionaryDatabase =
            Room.databaseBuilder(context, DictionaryDatabase::class.java, "dictionary.db").build()
    }
}
