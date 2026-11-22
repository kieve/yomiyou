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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import coil3.compose.AsyncImage

/** What a tile shows under its title, and over its cover. */
data class TileState(
    val status: String,
    val tone: Tone = Tone.Quiet,
    /** On the shelf already: a check over the cover. */
    val checked: Boolean = false,
    /** A download's progress, 0 to 1; [working]: work of unknown length. */
    val progress: Float? = null,
    val working: Boolean = false,
    val dimmed: Boolean = false,
) {
    enum class Tone { Quiet, Action, Error }
}

/** A grid of books, as the shelf lays them out. */
@Composable
fun CoverGrid(innerPadding: PaddingValues, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

/** A book offered by a source: its cover (the title until it loads), the title, and where it stands. */
@Composable
fun CoverTile(title: String, cover: Any?, state: TileState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .alpha(if (state.dimmed) 0.5f else 1f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Text(
                title,
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
            if (state.checked) {
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
            val bar = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp)
            if (state.progress != null) {
                LinearProgressIndicator(progress = { state.progress }, modifier = bar)
            } else if (state.working) {
                LinearProgressIndicator(modifier = bar)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier
                .padding(top = 8.dp)
                .basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2000)
        )
        Text(
            state.status,
            style = MaterialTheme.typography.bodySmall,
            color = when (state.tone) {
                TileState.Tone.Error -> MaterialTheme.colorScheme.error
                TileState.Tone.Action -> MaterialTheme.colorScheme.primary
                TileState.Tone.Quiet -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2
        )
    }
}

/** A message in the middle of the screen, with what to do about it. */
@Composable
fun Notice(text: String, modifier: Modifier, action: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action()
    }
}
