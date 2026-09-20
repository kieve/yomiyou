package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiContext
import ca.kieve.yomiyou.YomiScreen
import ca.kieve.yomiyou.data.NovelRepository
import ca.kieve.yomiyou.data.database.model.Chapter
import ca.kieve.yomiyou.data.database.model.Novel
import coil3.compose.rememberAsyncImagePainter

@Composable
fun NovelInfoScreen(
    yomiContext: YomiContext,
    novelId: Long
) {
    val navController = yomiContext.navController
    val novelRepository = yomiContext.appContainer.novelRepository
    val scheduler = yomiContext.appContainer.novelScheduler

    LaunchedEffect(Unit) {
        scheduler.setActiveNovel(novelId)
        novelRepository.loadNovelDetails(novelId)
    }

    val novel by novelRepository.novel(novelId).collectAsState(null)
    val chapters by novelRepository.chapters(novelId).collectAsState(emptyList())

    Scaffold(
        topBar = {
            Row {
                IconButton(
                    modifier = Modifier
                        .padding(4.dp),
                    onClick = {
                        navController.navigateUp()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription =
                        stringResource(R.string.yomi_backContentDescription)
                    )
                }
            }
        }
    ) { innerPadding ->
        val shownNovel = novel ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                NovelInfo(
                    novelRepository = novelRepository,
                    novel = shownNovel
                )
            }

            items(chapters, key = { it.url }) { chapter ->
                ChapterRow(
                    navController = navController,
                    novel = shownNovel,
                    chapter = chapter
                )
            }
        }
    }
}

@Composable
fun NovelInfo(
    novelRepository: NovelRepository,
    novel: Novel
) {
    val title = novel.title

    Row(
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        Image(
            painter = rememberAsyncImagePainter(novel.coverUrl),
            contentDescription = stringResource(
                id = R.string.novelCard_imageContentDescription,
                title),
            modifier = Modifier
                .size(120.dp, 160.dp)
                .padding(end = 16.dp),
            contentScale = ContentScale.FillBounds
        )
        Column(
            modifier = Modifier.fillMaxHeight()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1.0f))
            Button(
                onClick = {
                    novelRepository.setInLibrary(novel.id, !novel.inLibrary)
                },
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp
                )
            ) {
                val image: ImageVector
                val contentDescription: String
                val text: String

                if (novel.inLibrary) {
                    image = Icons.Filled.Favorite
                    contentDescription = stringResource(R.string.novelInfo_removeContentDescription)
                    text = stringResource(R.string.novelInfo_remove)
                } else {
                    image = Icons.Outlined.Favorite
                    contentDescription = stringResource(R.string.novelInfo_addContentDescription)
                    text = stringResource(R.string.novelInfo_add)
                }

                Icon(
                    imageVector = image,
                    contentDescription = contentDescription
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(text)
            }
        }
    }
}

@Composable
private fun ChapterRow(
    navController: NavController,
    novel: Novel,
    chapter: Chapter
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!novel.inLibrary) {
                    return@clickable
                }
                navController.navigate(
                    YomiScreen.ReaderNav.withArgs(
                        chapter.novelId.toString(),
                        chapter.position.toString()
                    )
                )
            }
    ) {
        Text(
            modifier = Modifier
                .width(100.dp)
                .padding(end = 4.dp),
            text = stringResource(R.string.yomi_shortChapterNum, chapter.position),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.headlineSmall,
            color = LocalContentColor.current.copy(alpha = if (chapter.downloaded) 1f else 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider()
}
