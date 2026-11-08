package ca.kieve.yomiyou.home

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import ca.kieve.yomiyou.book.Library
import ca.kieve.yomiyou.book.TestBook
import ca.kieve.yomiyou.reader.ReadingStyleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settings by preferencesDataStore(name = "settings")

/** The new screens' app-wide objects, made on first use; the webnovel screens keep their own `AppContainer`. */
class Services private constructor(context: Context) {
    /** Work that outlives a screen, such as saving a reading position on the way out. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val library = Library(context, context.settings)
    val readingStyle = ReadingStyleStore(context.settings)
    val testBook = TestBook(context, library, scope)

    companion object {
        @Volatile
        private var instance: Services? = null

        fun of(context: Context): Services =
            instance ?: synchronized(this) {
                instance ?: Services(context.applicationContext).also { instance = it }
            }
    }
}

val Context.services: Services get() = Services.of(this)
