package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.YomiContext
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun ReaderScreen(
    yomiContext: YomiContext,
    novelId: Long,
    position: Int
) {
    val novelRepository = yomiContext.appContainer.novelRepository

    var current by remember { mutableIntStateOf(position) }
    LaunchedEffect(current) {
        novelRepository.setReading(novelId, current)
    }
    val open by remember(current) { novelRepository.openChapter(novelId, current) }
        .collectAsState(null)
    val scroll = rememberScrollState()
    LaunchedEffect(current) { scroll.scrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 8.dp)
    ) {
        val chapter = open?.chapter
        Text(
            text = chapter?.let { "Chapter ${it.position}: ${it.title}" } ?: "No chapter $current",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        MarkdownText(
            markdown = open?.content ?: if (chapter != null) "Not downloaded yet." else "",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { current-- }, enabled = current > 1) { Text("Previous") }
            Button(onClick = { current++ }) { Text("Next") }
        }
    }
}
