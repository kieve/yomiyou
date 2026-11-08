package ca.kieve.yomiyou.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt

/** The narration, floating over the tabs: a tap goes back to the book, a swipe to either side ends it. */
@Composable
fun MiniPlayer(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val narration = LocalContext.current.services.narration
    val book by narration.book.collectAsState()
    val playing by narration.playing.collectAsState()
    val spoken by narration.spoken.collectAsState()
    val current = book ?: return
    var offset by remember(current.bookId) { mutableFloatStateOf(0f) }
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .offset { IntOffset(offset.roundToInt(), 0) }
            .alpha(1f - (abs(offset) / (dismissAt * 2)).coerceIn(0f, 0.8f))
            .draggable(
                state = rememberDraggableState { offset += it },
                orientation = Orientation.Horizontal,
                onDragStopped = { if (abs(offset) > dismissAt) narration.stop() else offset = 0f }
            )
            .clickable { onOpen(current.bookId) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = current.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 36.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                spoken?.let { current.chapters.getOrNull(it.chapter) }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = narration::toggle) {
                if (playing) {
                    Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause))
                } else {
                    Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.reader_play))
                }
            }
        }
    }
}
