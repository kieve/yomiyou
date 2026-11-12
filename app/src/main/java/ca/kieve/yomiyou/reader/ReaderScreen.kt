package ca.kieve.yomiyou.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import ca.kieve.yomiyou.book.VoiceInfo
import ca.kieve.yomiyou.book.ZipIndex
import ca.kieve.yomiyou.home.Services
import ca.kieve.yomiyou.home.services
import ca.kieve.yomiyou.narration.Speed
import ca.kieve.yomiyou.narration.VoiceChoice
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
    val narration = services.narration
    val narrated by narration.book.collectAsState()
    val spoken by narration.spoken.collectAsState()
    val playing by narration.playing.collectAsState()
    val speed by narration.speed.collectAsState(initial = Speed.DEFAULT)
    val narrationError by narration.error.collectAsState()
    val remembered by narration.remembered.collectAsState(initial = null)
    val here = narrated?.bookId == entry.info.bookId
    val voices = entry.info.voices
    val voice = voices.firstOrNull { here && it.id == narrated?.voice } ?: VoiceChoice.of(voices, remembered)
    val following by rememberUpdatedState(here && playing)
    val start = spoken?.takeIf { here }?.let { Position(it.chapter, it.phrase) }
        ?: entry.info.position?.takeIf { it.chapter in book.chapters.indices }
    var chapter by rememberSaveable { mutableIntStateOf(start?.chapter ?: 0) }
    var span by rememberSaveable { mutableIntStateOf(start?.span ?: -1) }
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
                // While this book plays, the phrase being spoken is the position, not the screen.
                if (!following) {
                    chapter = position.chapter
                    span = position.span
                }
            }

            override fun onTap(phrase: Int?) {
                if (controls) {
                    controls = false
                    options = false
                } else if (phrase != null) {
                    scope.launch { narration.play(entry, chapter, phrase) }
                }
            }

            override fun onDoubleTap() {
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
    LaunchedEffect(ready, here) {
        if (!ready) return@LaunchedEffect
        if (!here) {
            page.light(null, 0)
            return@LaunchedEffect
        }
        // Straight to the page as each phrase starts, not at the next frame's recomposition.
        withContext(Dispatchers.Main.immediate) {
            narration.spoken.collect { now ->
                if (now == null) return@collect
                page.light(now.phrase, now.chapter)
                chapter = now.chapter
                span = now.phrase
            }
        }
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
                    if (here) narration.moveTo(index)
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
                visible = controls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomControls(
                    style = style,
                    speed = speed,
                    playing = here && playing,
                    options = options,
                    voices = voices,
                    voice = voice,
                    onVoice = { id -> scope.launch { narration.useVoice(entry, id) } },
                    onPlay = {
                        if (here) {
                            narration.toggle()
                        } else {
                            scope.launch { narration.play(entry, chapter, span.takeIf { it >= 0 }) }
                        }
                    },
                    onSlower = { scope.launch { narration.changeSpeed(-1) } },
                    onFaster = { scope.launch { narration.changeSpeed(1) } },
                    onSmaller = { scope.launch { services.readingStyle.update { it.smaller() } } },
                    onLarger = { scope.launch { services.readingStyle.update { it.larger() } } }
                )
            }
            (error ?: narrationError)?.let {
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
private fun BottomControls(
    style: ReadingStyle?,
    speed: Float,
    playing: Boolean,
    options: Boolean,
    voices: List<VoiceInfo>,
    voice: VoiceInfo?,
    onVoice: (String) -> Unit,
    onPlay: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
            if (options && style != null) {
                Text(
                    stringResource(R.string.reader_options),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Stepper(
                    label = stringResource(R.string.reader_textSize),
                    value = style.textSize.toString(),
                    lessIcon = R.drawable.ic_text_decrease,
                    less = stringResource(R.string.reader_smaller),
                    canLess = style.textSize > ReadingStyle.MIN_TEXT_SIZE,
                    onLess = onSmaller,
                    moreIcon = R.drawable.ic_text_increase,
                    more = stringResource(R.string.reader_larger),
                    canMore = style.textSize < ReadingStyle.MAX_TEXT_SIZE,
                    onMore = onLarger,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            if (voices.isNotEmpty()) VoiceMenu(voices, voice, onVoice)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilledIconButton(onClick = onPlay) {
                    if (playing) {
                        Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause))
                    } else {
                        Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.reader_play))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Stepper(
                    label = stringResource(R.string.reader_speed),
                    value = Speed.label(speed),
                    lessIcon = R.drawable.ic_remove,
                    less = stringResource(R.string.reader_slower),
                    canLess = speed > Speed.MIN,
                    onLess = onSlower,
                    moreIcon = R.drawable.ic_add,
                    more = stringResource(R.string.reader_faster),
                    canMore = speed < Speed.MAX,
                    onMore = onFaster,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VoiceMenu(voices: List<VoiceInfo>, voice: VoiceInfo?, onVoice: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reader_voice), modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(voice?.label.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (each in voices) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(each.label)
                                if (each.credit.isNotEmpty()) {
                                    Text(
                                        each.credit,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            if (each.id == voice?.id) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                        onClick = {
                            open = false
                            onVoice(each.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    @DrawableRes lessIcon: Int,
    less: String,
    canLess: Boolean,
    onLess: () -> Unit,
    @DrawableRes moreIcon: Int,
    more: String,
    canMore: Boolean,
    onMore: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onLess, enabled = canLess) { Icon(painterResource(lessIcon), less) }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp)
        )
        IconButton(onClick = onMore, enabled = canMore) { Icon(painterResource(moreIcon), more) }
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
