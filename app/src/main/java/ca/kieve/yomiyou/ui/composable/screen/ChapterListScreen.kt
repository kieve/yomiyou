package ca.kieve.yomiyou.ui.composable.screen

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiContext
import ca.kieve.yomiyou.data.model.Novel
import ca.kieve.yomiyou.data.repository.NovelRepository
import ca.kieve.yomiyou.util.getTag

@Composable
fun ChapterListScreen(
    yomiContext: YomiContext,
    novelId: Long
) {
    val navController = yomiContext.navController
    val novelRepository = yomiContext.appContainer.novelRepository
    val novel = novelRepository.getNovel(novelId)

    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                title = {
                    Text(
                        text = stringResource(
                            R.string.chapterList_title,
                            novel?.metadata?.title ?: "Null"), // TODO: Fix this
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    ) {
        if (novel == null) {
            Text("Novel is null") // TODO FIX THIS
        } else {
            ChapterList(
                navController = navController,
                novel = novel,
                novelRepository = novelRepository
            )
        }
    }
}

@Composable
private fun ChapterList(
    navController: NavController,
    novel: Novel,
    novelRepository: NovelRepository)
{
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(novel.chapters) { chapter ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        novelRepository.debugPrint(chapter)
                    }
            ) {
                Text(
                    modifier = Modifier
                        .width(100.dp)
                        .padding(end = 4.dp),
                    text = stringResource(R.string.yomi_shortChapterNum, chapter.id),
                    style = MaterialTheme.typography.h5
                )
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.h5,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Divider()
        }
    }
}
