package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.kieve.yomiyou.YomiContext
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun ChapterScreen(
    yomiContext: YomiContext,
    novelId: Long,
    chapterId: Long
) {
    val novelRepository = yomiContext.appContainer.novelRepository
    novelRepository.openChapter(
        novelId = novelId,
        chapterId = chapterId
    )
    val chapterMeta = novelRepository.getChapter(
        novelId = novelId,
        chapterId = chapterId
    )
    val chapter by novelRepository.openChapter.collectAsState()

    val chapterNum = "Chapter ${chapterMeta?.id}:"
    val title = chapterMeta?.title ?: "Unknown title"

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                start = 8.dp,
                end = 8.dp
            )
    ) {
        item {
            Text(
                text = "$chapterNum $title",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(
                    top = 8.dp,
                    bottom = 8.dp)
            )
        }
        item {
            MarkdownText(
                markdown = chapter ?: "**Not downloaded.**",
                fontSize = 14.sp,
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body1)
        }
    }
}
