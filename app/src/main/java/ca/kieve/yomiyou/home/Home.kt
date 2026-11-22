package ca.kieve.yomiyou.home

import android.net.Uri
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import ca.kieve.yomiyou.browse.ServerScreen
import ca.kieve.yomiyou.reader.ReaderScreen
import ca.kieve.yomiyou.kavita.KavitaScreen
import kotlinx.serialization.Serializable

@Serializable
data object Tabs : NavKey

@Serializable
data class Reader(val bookId: String) : NavKey

@Serializable
data object ServerShelf : NavKey

@Serializable
data object KavitaShelf : NavKey

/** Every screen slides in from the right over the one before it, and back out to the right, like turning to a page. */
private const val SLIDE_MS = 300

private fun forward(): ContentTransform =
    slideInHorizontally(tween(SLIDE_MS)) { it } togetherWith slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 }

private fun back(): ContentTransform =
    slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } togetherWith slideOutHorizontally(tween(SLIDE_MS)) { it }

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
    val services = LocalContext.current.services
    val request by services.openRequests.collectAsState()
    LaunchedEffect(request) {
        val bookId = request ?: return@LaunchedEffect
        if ((backStack.lastOrNull() as? Reader)?.bookId != bookId) backStack.add(Reader(bookId))
        services.openRequests.value = null
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = { forward() },
        popTransitionSpec = { back() },
        predictivePopTransitionSpec = { back() },
        entryProvider = entryProvider {
            entry<Tabs> {
                TabsScreen(onOpenBook = { backStack.add(Reader(it)) }, onOpen = { backStack.add(it) })
            }
            entry<ServerShelf> {
                ServerScreen(onBack = { backStack.removeLastOrNull() }, onOpenBook = { backStack.add(Reader(it)) })
            }
            entry<KavitaShelf> {
                KavitaScreen(onBack = { backStack.removeLastOrNull() }, onOpenBook = { backStack.add(Reader(it)) })
            }
            entry<Reader> { key -> ReaderScreen(key.bookId, onBack = { backStack.removeLastOrNull() }) }
        }
    )
}
