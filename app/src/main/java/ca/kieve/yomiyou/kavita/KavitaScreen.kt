package ca.kieve.yomiyou.kavita

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.browse.BrowseSource
import ca.kieve.yomiyou.browse.CoverGrid
import ca.kieve.yomiyou.browse.CoverTile
import ca.kieve.yomiyou.browse.Notice
import ca.kieve.yomiyou.browse.TileState
import ca.kieve.yomiyou.browse.megabytes
import ca.kieve.yomiyou.home.KavitaShelf
import ca.kieve.yomiyou.home.services
import ca.kieve.yomiyou.server.ServerBook
import ca.kieve.yomiyou.server.rememberLocalNetwork
import androidx.navigation3.runtime.NavKey
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/** Kavita in Browse: its books on a screen of their own. */
object KavitaSource : BrowseSource {
    @Composable
    override fun Item(onStarted: () -> Unit, onOpen: (NavKey) -> Unit) {
        val address by LocalContext.current.services.kavita.address.collectAsState(initial = "")
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_cloud_download), contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.browse_kavita)) },
            supportingContent = {
                Text(
                    if (address.isEmpty()) {
                        stringResource(R.string.browse_kavitaUnset)
                    } else {
                        stringResource(R.string.browse_kavitaSummary, address.substringAfter("://"))
                    }
                )
            },
            modifier = Modifier.clickable { onOpen(KavitaShelf) }
        )
    }
}

/**
 * Kavita's book libraries, then a library's EPUBs as a shelf: those here checked, the rest to download, with whether
 * yomiage has a narration of each. Tapping a book here opens it; tapping another downloads its original.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaScreen(onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val context = LocalContext.current
    val services = context.services
    val kavita = services.kavita
    val address by kavita.address.collectAsState(initial = null)
    val key by kavita.key.collectAsState(initial = null)
    val libraries by kavita.libraries.collectAsState()
    val books by kavita.books.collectAsState()
    val states by kavita.states.collectAsState()
    val serverBooks by services.server.books.collectAsState()
    val changes by services.library.changes.collectAsState()
    val network = rememberLocalNetwork()
    val configured = !address.isNullOrEmpty() && !key.isNullOrEmpty()
    LaunchedEffect(configured, network.granted) {
        if (configured && network.granted) {
            kavita.refresh()
            services.server.refresh()
        }
    }
    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    val listed = libraries?.getOrNull()
    // One library: straight to its books.
    val library = listed?.firstOrNull { it.id == chosen } ?: listed?.singleOrNull()
    LaunchedEffect(library?.id, books.containsKey(library?.id)) {
        if (library != null && !books.containsKey(library.id)) kavita.load(library)
    }
    BackHandler(enabled = chosen != null) { chosen = null }
    /** Each book here by its Kavita chapter, and every book ID here. */
    val shelf by produceState(Pair(emptyMap<Int, String>(), emptySet<String>()), changes) {
        val here = services.library.books()
        value = kavita.links(here) to here.mapTo(HashSet()) { it.info.bookId }
    }
    val narrations = remember(serverBooks) {
        serverBooks?.getOrNull().orEmpty().filter { it.file != null }.associateBy { KavitaCatalog.nfc(it.file!!) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(library?.name ?: stringResource(R.string.browse_kavita)) },
                navigationIcon = {
                    IconButton(onClick = { if (chosen != null) chosen = null else onBack() }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (library != null) kavita.load(library) else kavita.refresh() },
                        enabled = configured && network.granted
                    ) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.server_refresh))
                    }
                }
            )
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        val shown = library?.let { books[it.id] }
        when {
            address == null || key == null -> {}
            !configured -> Notice(stringResource(R.string.browse_kavitaUnset), modifier) {}
            !network.granted -> Notice(stringResource(R.string.browse_serverNoAccess), modifier) {
                Button(onClick = network.ask) { Text(stringResource(R.string.server_allow)) }
            }
            libraries == null -> Notice(stringResource(R.string.kavita_loading), modifier) { CircularProgressIndicator() }
            libraries!!.isFailure -> Notice(libraries!!.exceptionOrNull()?.message.orEmpty(), modifier) {
                Button(onClick = { kavita.refresh() }) { Text(stringResource(R.string.server_retry)) }
            }
            listed.isNullOrEmpty() -> Notice(stringResource(R.string.kavita_noLibraries), modifier) {}
            library == null -> LazyColumn(contentPadding = innerPadding) {
                items(listed, key = { it.id }) {
                    ListItem(
                        headlineContent = { Text(it.name) },
                        modifier = Modifier.clickable { chosen = it.id }
                    )
                }
            }
            shown == null -> Notice(stringResource(R.string.kavita_loading), modifier) { CircularProgressIndicator() }
            shown.isFailure -> Notice(shown.exceptionOrNull()?.message.orEmpty(), modifier) {
                Button(onClick = { kavita.load(library) }) { Text(stringResource(R.string.server_retry)) }
            }
            shown.getOrThrow().isEmpty() -> Notice(stringResource(R.string.kavita_noBooks), modifier) {}
            else -> CoverGrid(innerPadding) {
                items(shown.getOrThrow(), key = { it.chapterId }) { book ->
                    val narration = narrations[book.file]
                    val here = shelf.first[book.chapterId] ?: narration?.bookId?.takeIf { it in shelf.second }
                    val state = states[book.chapterId]
                    if (here != null && book.chapterId !in shelf.first) {
                        LaunchedEffect(here) { kavita.adopt(here, book) }
                    }
                    CoverTile(
                        title = book.title,
                        cover = ImageRequest.Builder(context)
                            .data(Kavita.cover(address!!, book))
                            .httpHeaders(NetworkHeaders.Builder().set(Kavita.KEY_HEADER, key!!).build())
                            .build(),
                        state = tileState(book, here != null, state, narration),
                        onClick = {
                            when {
                                here != null -> onOpenBook(here)
                                state is KavitaState.Downloading -> {}
                                else -> kavita.download(book)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun tileState(book: KavitaBook, here: Boolean, state: KavitaState?, narration: ServerBook?): TileState {
    val available = stringResource(R.string.kavita_narration)
    val withNarration = { status: String -> if (narration?.version != null) "$status\n$available" else status }
    return when {
        here -> TileState(stringResource(R.string.browse_serverCurrent), checked = true)
        state is KavitaState.Downloading -> TileState(
            stringResource(R.string.server_downloading, megabytes(state.bytes), megabytes(state.total)),
            progress = if (state.total > 0) state.bytes.toFloat() / state.total else 0f,
        )
        state is KavitaState.Failed -> TileState(state.message, tone = TileState.Tone.Error)
        else -> TileState(
            withNarration(stringResource(R.string.browse_serverDownload, megabytes(book.bytes))),
            tone = TileState.Tone.Action,
        )
    }
}
