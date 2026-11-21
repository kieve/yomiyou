package ca.kieve.yomiyou.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.services
import ca.kieve.yomiyou.server.BookState
import ca.kieve.yomiyou.server.ServerBook
import ca.kieve.yomiyou.server.rememberLocalNetwork

fun megabytes(bytes: Long): String = "${(bytes + 500_000) / 1_000_000} MB"

/** The yomiage server's books, each with what it takes to have it, or have it up to date. A tap asks again. */
object ServerSource : BrowseSource {
    @Composable
    override fun Item(onStarted: () -> Unit) {
        val server = LocalContext.current.services.server
        val address by server.address.collectAsState(initial = "")
        val books by server.books.collectAsState()
        val states by server.states.collectAsState()
        val network = rememberLocalNetwork()
        LaunchedEffect(address, network.granted) {
            if (address.isNotEmpty() && network.granted) server.refresh()
        }
        Column {
            ListItem(
                leadingContent = { Icon(painterResource(R.drawable.ic_cloud_download), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.browse_server)) },
                supportingContent = {
                    val listed = books
                    Text(
                        when {
                            !network.granted -> stringResource(R.string.browse_serverNoAccess)
                            listed == null -> stringResource(R.string.browse_serverLoading)
                            listed.isFailure -> listed.exceptionOrNull()?.message ?: address
                            else -> stringResource(R.string.browse_serverSummary, address.substringAfter("://"))
                        }
                    )
                },
                modifier = Modifier.clickable { if (network.granted) server.refresh() else network.ask() }
            )
            if (network.granted) for (book in books?.getOrNull().orEmpty()) {
                Row(book, states[book.bookId])
            }
        }
    }

    @Composable
    private fun Row(book: ServerBook, state: BookState?) {
        val server = LocalContext.current.services.server
        ListItem(
            headlineContent = { Text(book.title) },
            supportingContent = {
                val note = when (state) {
                    BookState.NotBuilt -> stringResource(R.string.browse_serverNotBuilt)
                    BookState.Current -> stringResource(R.string.browse_serverCurrent)
                    is BookState.Working -> stringResource(R.string.browse_serverWorking, state.stage)
                    is BookState.Downloading ->
                        stringResource(R.string.browse_serverDownloading, megabytes(state.bytes), megabytes(state.total))
                    is BookState.Failed -> state.message
                    else -> book.author
                }
                Text(note, color = if (state is BookState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = (state as? BookState.Available)?.let { available ->
                {
                    TextButton(onClick = { server.download(available.manifest, book.title) }) {
                        val size = megabytes(available.bytes)
                        Text(
                            if (available.update) {
                                stringResource(R.string.browse_serverUpdate, size)
                            } else {
                                stringResource(R.string.browse_serverDownload, size)
                            }
                        )
                    }
                }
            },
        )
    }
}
