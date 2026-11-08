package ca.kieve.yomiyou.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.book.BookError
import ca.kieve.yomiyou.book.EnrichedEpub
import ca.kieve.yomiyou.book.LibraryBook
import ca.kieve.yomiyou.book.Position
import ca.kieve.yomiyou.book.ZipIndex
import ca.kieve.yomiyou.home.Services
import ca.kieve.yomiyou.home.services
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private sealed interface Opening {
    data object Loading : Opening
    data class Failed(val message: String) : Opening
    class Open(val entry: LibraryBook, val book: EnrichedEpub) : Opening
}

private const val SAVE_DELAY_MS = 1500L

@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val services = context.services
    val notFound = stringResource(R.string.reader_notFound)
    val opening by produceState<Opening>(Opening.Loading, bookId) {
        value = withContext(Dispatchers.IO) { open(services, bookId) ?: Opening.Failed(notFound) }
    }
    when (val state = opening) {
        Opening.Loading -> Box(Modifier.fillMaxSize().background(Color.Black))
        is Opening.Failed -> Failure(state.message, onBack)
        is Opening.Open -> {
            DisposableEffect(state.book) { onDispose { state.book.close() } }
            Reader(state.entry, state.book, onBack)
        }
    }
}

private suspend fun open(services: Services, bookId: String): Opening? {
    val entry = services.library.find(bookId) ?: return null
    val file = entry.file("book.epub") ?: return null
    val access = try {
        services.library.open(file)
    } catch (e: IOException) {
        return Opening.Failed(e.message ?: e.toString())
    }
    return try {
        Opening.Open(entry, EnrichedEpub.open(ZipIndex.open(access)))
    } catch (e: Exception) {
        access.close()
        Opening.Failed(if (e is BookError) e.message.orEmpty() else e.toString())
    }
}

@Composable
private fun Reader(entry: LibraryBook, book: EnrichedEpub, onBack: () -> Unit) {
    val context = LocalContext.current
    val services = context.services
    val scope = rememberCoroutineScope()
    val style by services.readingStyle.style.collectAsState(initial = null)
    val saved = entry.info.position?.takeIf { it.chapter in book.chapters.indices }
    var chapter by rememberSaveable { mutableIntStateOf(saved?.chapter ?: 0) }
    var span by rememberSaveable { mutableIntStateOf(saved?.span ?: -1) }
    var controls by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)

    val page = remember(book) {
        ReaderPage(context, book, scope, object : ReaderPage.Listener {
            override fun onReady() {
                ready = true
            }

            override fun onPosition(position: Position) {
                error = null
                chapter = position.chapter
                span = position.span
            }

            override fun onTap() {
                controls = !controls
                if (!controls) options = false
            }

            override fun onError(message: String) {
                error = message
            }
        })
    }
    DisposableEffect(page) { onDispose { page.destroy() } }

    LaunchedEffect(ready, style == null) {
        val current = style ?: return@LaunchedEffect
        if (ready) page.open(chapter, span.takeIf { it >= 0 }, current)
    }
    LaunchedEffect(style) {
        style?.let { if (ready) page.applyStyle(it) }
    }
    LaunchedEffect(chapter, span) {
        if (span < 0) return@LaunchedEffect
        delay(SAVE_DELAY_MS)
        services.library.updateInfo(entry) { it.copy(position = Position(chapter, span)) }
    }
    val latest by rememberUpdatedState(Position(chapter, span))
    DisposableEffect(entry) {
        onDispose {
            val position = latest
            if (position.span >= 0) {
                services.scope.launch { services.library.updateInfo(entry) { it.copy(position = position) } }
            }
        }
    }

    SystemBars(visible = controls)
    BackHandler(enabled = controls) {
        controls = false
        options = false
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.reader_chapters),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                ChapterList(book.chapters, chapter) { index ->
                    scope.launch { drawer.close() }
                    controls = false
                    options = false
                    chapter = index
                    span = -1
                    page.jump(index)
                }
            }
        }
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { page.view },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
            )
            AnimatedVisibility(
                visible = controls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Controls(
                    title = book.title,
                    onBack = onBack,
                    onChapters = { scope.launch { drawer.open() } },
                    onOptions = { options = !options }
                )
            }
            AnimatedVisibility(
                visible = controls && options,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                style?.let { current ->
                    Options(
                        style = current,
                        onSmaller = { scope.launch { services.readingStyle.update { it.smaller() } } },
                        onLarger = { scope.launch { services.readingStyle.update { it.larger() } } }
                    )
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SystemBars(visible: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    LaunchedEffect(controller, visible) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(title: String, onBack: () -> Unit, onChapters: () -> Unit, onOptions: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back))
                }
            },
            actions = {
                IconButton(onClick = onChapters) {
                    Icon(painterResource(R.drawable.ic_toc), stringResource(R.string.reader_chapters))
                }
                IconButton(onClick = onOptions) {
                    Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.reader_options))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun Options(style: ReadingStyle, onSmaller: () -> Unit, onLarger: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.reader_options), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reader_textSize), modifier = Modifier.weight(1f))
                IconButton(onClick = onSmaller, enabled = style.textSize > ReadingStyle.MIN_TEXT_SIZE) {
                    Icon(painterResource(R.drawable.ic_text_decrease), stringResource(R.string.reader_smaller))
                }
                Text(
                    style.textSize.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp)
                )
                IconButton(onClick = onLarger, enabled = style.textSize < ReadingStyle.MAX_TEXT_SIZE) {
                    Icon(painterResource(R.drawable.ic_text_increase), stringResource(R.string.reader_larger))
                }
            }
        }
    }
}

@Composable
private fun ChapterList(chapters: List<String>, current: Int, onChoose: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    LazyColumn(state = state) {
        itemsIndexed(chapters) { index, label ->
            ListItem(
                headlineContent = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                colors = if (index == current) {
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    ListItemDefaults.colors(containerColor = Color.Transparent)
                },
                modifier = Modifier.clickable { onChoose(index) }
            )
        }
    }
}

@Composable
private fun Failure(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onBack) { Text(stringResource(R.string.reader_back)) }
    }
}
