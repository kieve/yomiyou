package ca.kieve.yomiyou.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import ca.kieve.yomiyou.reader.ReaderScreen
import kotlinx.serialization.Serializable

@Serializable
data object Tabs : NavKey

@Serializable
data class Reader(val bookId: String) : NavKey

@Composable
fun Home() {
    val library = LocalContext.current.services.library
    val folder by library.folder.collectAsState(initial = Uri.EMPTY)
    when (folder) {
        Uri.EMPTY -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        null -> LibraryPrompt()
        else -> Destinations()
    }
}

@Composable
private fun Destinations() {
    val backStack = rememberNavBackStack(Tabs)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Tabs> { TabsScreen(onOpenBook = { backStack.add(Reader(it)) }) }
            entry<Reader> { key -> ReaderScreen(key.bookId, onBack = { backStack.removeLastOrNull() }) }
        }
    )
}
