package ca.kieve.yomiyou.browse

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.services
import ca.kieve.yomiyou.server.BookState
import ca.kieve.yomiyou.server.ServerBook
import ca.kieve.yomiyou.server.rememberLocalNetwork

/**
 * The server's books as a shelf, covers and all: those here marked, a download or an update with its size, its
 * progress on the tile. Tapping a book here opens it; tapping one to download or update starts that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val server = LocalContext.current.services.server
    val address by server.address.collectAsState(initial = "")
    val books by server.books.collectAsState()
    val states by server.states.collectAsState()
    val network = rememberLocalNetwork()
    LaunchedEffect(address, network.granted) {
        if (address.isNotEmpty() && network.granted) server.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browse_server)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back))
                    }
                },
                actions = {
                    IconButton(onClick = { server.refresh() }, enabled = network.granted) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.server_refresh))
                    }
                }
            )
        }
    ) { innerPadding ->
        val listed = books
        val modifier = Modifier.padding(innerPadding)
        when {
            !network.granted -> Notice(stringResource(R.string.browse_serverNoAccess), modifier) {
                Button(onClick = network.ask) { Text(stringResource(R.string.server_allow)) }
            }
            listed == null -> Notice(stringResource(R.string.browse_serverLoading), modifier) { CircularProgressIndicator() }
            listed.isFailure -> Notice(listed.exceptionOrNull()?.message ?: address, modifier) {
                Button(onClick = { server.refresh() }) { Text(stringResource(R.string.server_retry)) }
            }
            listed.getOrThrow().isEmpty() -> Notice(stringResource(R.string.server_empty), modifier) {}
            else -> CoverGrid(innerPadding) {
                items(listed.getOrThrow(), key = { it.bookId }) { book ->
                    val state = states[book.bookId]
                    CoverTile(
                        title = book.title,
                        cover = "$address/books/${book.bookId}/cover",
                        state = tileState(book, state),
                        onClick = {
                            when (state) {
                                BookState.Current -> onOpenBook(book.bookId)
                                is BookState.Available -> server.download(state.manifest, book.title)
                                is BookState.Failed -> server.refresh()
                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun tileState(book: ServerBook, state: BookState?): TileState = when (state) {
    BookState.Current -> TileState(stringResource(R.string.browse_serverCurrent), checked = true)
    BookState.NotBuilt, BookState.NotOnServer -> TileState(stringResource(R.string.browse_serverNotBuilt), dimmed = true)
    is BookState.Working -> TileState(stringResource(R.string.server_working), working = true)
    is BookState.Downloading -> TileState(
        stringResource(R.string.server_downloading, megabytes(state.bytes), megabytes(state.total)),
        progress = if (state.total > 0) state.bytes.toFloat() / state.total else 0f,
    )
    is BookState.Available -> TileState(
        stringResource(if (state.update) R.string.browse_serverUpdate else R.string.browse_serverDownload, megabytes(state.bytes)),
        tone = TileState.Tone.Action,
    )
    is BookState.Failed -> TileState(state.message, tone = TileState.Tone.Error)
    null -> TileState(book.author)
}
