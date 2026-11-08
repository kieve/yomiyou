package ca.kieve.yomiyou.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingStyleStore(private val store: DataStore<Preferences>) {
    val style: Flow<ReadingStyle> = store.data.map { ReadingStyle.of(it[TEXT_SIZE]) }

    suspend fun update(change: (ReadingStyle) -> ReadingStyle) {
        store.edit { it[TEXT_SIZE] = change(ReadingStyle.of(it[TEXT_SIZE])).textSize }
    }

    private companion object {
        val TEXT_SIZE = intPreferencesKey("reader.textSize")
    }
}
