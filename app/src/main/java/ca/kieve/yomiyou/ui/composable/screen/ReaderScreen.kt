package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.kieve.yomiyou.YomiContext
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    yomiContext: YomiContext,
    novelId: Long,
    chapterId: Long
) {
    val novelRepository = yomiContext.appContainer.novelRepository
    novelRepository.openNovel(novelId)
    val chapters by novelRepository.openNovel.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(true) {
        coroutineScope.launch {
            listState.scrollToItem(index = chapterId.toInt() - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                start = 8.dp,
                end = 8.dp
            )
    ) {
        items(chapters) { chapter ->
            Text(
                text = "Chapter ${chapter.chapterMeta.id}: ${chapter.chapterMeta.title}",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .padding(
                        top = 8.dp,
                        bottom = 8.dp
                    )
            )
            MarkdownText(
                markdown = chapter.content,
                fontSize = 14.sp,
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .padding(
                        bottom = 8.dp
                    )
            )
            Divider()
        }
    }
}
