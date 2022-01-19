package ca.kieve.yomiyou.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.kieve.yomiyou.data.database.dao.ChapterDao
import ca.kieve.yomiyou.data.database.dao.NovelDao
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta

@Database(
    version = 1,
    entities = [
        NovelMeta::class,
        ChapterMeta::class
    ]
)
abstract class YomiDatabase : RoomDatabase() {
    companion object {
        @Volatile
        private var INSTANCE: YomiDatabase? = null

        fun getDatabase(context: Context): YomiDatabase {
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YomiDatabase::class.java,
                    "yomi_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
}
