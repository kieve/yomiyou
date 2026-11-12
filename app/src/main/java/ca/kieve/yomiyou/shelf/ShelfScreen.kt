package ca.kieve.yomiyou.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.EmptyMessage
import ca.kieve.yomiyou.home.services
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(onOpenBook: (String) -> Unit) {
    val services = LocalContext.current.services
    val scope = rememberCoroutineScope()
    val changes by services.library.changes.collectAsState()
    val narrated by services.narration.book.collectAsState()
    // Also on coming back to the app: a book folder may have been copied in meanwhile.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose {}
    }
    val books by produceState<List<ShelfBook>?>(null, changes, resumes) {
        if (resumes == 0) return@produceState
        value = Shelf.order(services.library.books().map { YomiageShelfBook(it, services.library) })
    }
    var deleting by remember { mutableStateOf<ShelfBook?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { innerPadding ->
        val shown = books ?: return@Scaffold
        if (shown.isEmpty()) {
            EmptyMessage(stringResource(R.string.shelf_empty), Modifier.padding(innerPadding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                // Room for the floating player.
                bottom = innerPadding.calculateBottomPadding() + if (narrated != null) 96.dp else 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(shown, key = { it.id }) { book ->
                Tile(book, onOpen = { onOpenBook(book.id) }, onDelete = { deleting = book })
            }
        }
    }

    deleting?.let { book ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.shelf_deleteTitle)) },
            text = { Text(book.title) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        if (services.narration.book.value?.bookId == book.id) services.narration.stop()
                        runCatching { book.delete() }
                    }
                }) { Text(stringResource(R.string.shelf_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.shelf_cancel)) }
            }
        )
    }
}

@Composable
private fun Tile(book: ShelfBook, onOpen: () -> Unit, onDelete: () -> Unit) {
    val description = book.title
    Column(
        modifier = Modifier
            .combinedClickable(
                onClickLabel = stringResource(R.string.shelf_open),
                onLongClickLabel = stringResource(R.string.shelf_delete),
                onLongClick = onDelete,
                onClick = onOpen
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (book.cover != null) {
                AsyncImage(
                    model = book.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                )
            }
            if (book.narrated) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_headphones),
                        contentDescription = stringResource(R.string.shelf_narrated),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            book.progress?.let {
                ProgressRing(it, Modifier.align(Alignment.BottomEnd).padding(6.dp).size(40.dp))
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
    }
}

@Composable
private fun ProgressRing(percent: Int, modifier: Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension / 12
            drawCircle(Color.Black.copy(alpha = 0.6f))
            val inset = stroke * 1.5f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(Color.White.copy(alpha = 0.4f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(
                Color.White,
                -90f,
                percent * 3.6f,
                false,
                topLeft,
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text("$percent%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
