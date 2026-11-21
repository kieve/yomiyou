package ca.kieve.yomiyou.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.services
import ca.kieve.yomiyou.server.BookState
import ca.kieve.yomiyou.server.ServerBook
import ca.kieve.yomiyou.server.rememberLocalNetwork
import coil3.compose.AsyncImage

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
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(listed.getOrThrow(), key = { it.bookId }) { book ->
                    val state = states[book.bookId]
                    Tile(
                        book = book,
                        cover = "$address/books/${book.bookId}/cover",
                        state = state,
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
private fun Notice(text: String, modifier: Modifier, action: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action()
    }
}

@Composable
private fun Tile(book: ServerBook, cover: String, state: BookState?, onClick: () -> Unit) {
    val status = when (state) {
        BookState.Current -> stringResource(R.string.browse_serverCurrent)
        BookState.NotBuilt -> stringResource(R.string.browse_serverNotBuilt)
        is BookState.Working -> stringResource(R.string.server_working)
        is BookState.Downloading ->
            stringResource(R.string.server_downloading, megabytes(state.bytes), megabytes(state.total))
        is BookState.Available -> stringResource(
            if (state.update) R.string.browse_serverUpdate else R.string.browse_serverDownload,
            megabytes(state.bytes)
        )
        is BookState.Failed -> state.message
        null -> book.author
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .alpha(if (state == BookState.NotBuilt) 0.5f else 1f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Text(
                book.title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 6,
                modifier = Modifier.align(Alignment.Center).padding(8.dp)
            )
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (state == BookState.Current) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (state is BookState.Downloading) {
                val fraction = if (state.total > 0) state.bytes.toFloat() / state.total else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp)
                )
            } else if (state is BookState.Working) {
                LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
            }
        }
        Text(
            book.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier
                .padding(top = 8.dp)
                .basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2000)
        )
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = when (state) {
                is BookState.Failed -> MaterialTheme.colorScheme.error
                is BookState.Available -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2
        )
    }
}
