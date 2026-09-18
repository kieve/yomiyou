package ca.kieve.yomiyou.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.kieve.yomiyou.data.database.dao.ChapterDao
import ca.kieve.yomiyou.data.database.dao.NovelDao
import ca.kieve.yomiyou.data.database.model.Chapter
import ca.kieve.yomiyou.data.database.model.Novel

@Database(
    version = 1,
    entities = [
        Novel::class,
        Chapter::class
    ]
)
abstract class YomiDatabase : RoomDatabase() {
    companion object {
        fun create(context: Context): YomiDatabase {
            return Room.databaseBuilder(context, YomiDatabase::class.java, "yomi_database")
                .build()
        }
    }

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
}
