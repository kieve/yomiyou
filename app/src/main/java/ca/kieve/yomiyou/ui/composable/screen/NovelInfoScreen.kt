package ca.kieve.yomiyou.ui.composable.screen

import android.widget.Toast
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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiContext
import ca.kieve.yomiyou.YomiScreen
import ca.kieve.yomiyou.data.NovelRepository
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.model.Novel
import coil.compose.rememberImagePainter

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
    }

    val novels by novelRepository.novels.collectAsState()
    val novel = novels[novelId]

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
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription =
                        stringResource(R.string.yomi_backContentDescription)
                    )
                }
            }
        }
    ) {
        Column(
            Modifier.padding(
                start = 16.dp,
                end = 16.dp
            )
        ) {
            if (novel == null) {
                Text("Novel is null") // TODO FIX THIS
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        NovelInfo(
                            novelRepository = novelRepository,
                            novel = novel
                        )
                    }

                    items(novel.chapters) { chapter ->
                        ChapterRow(
                            navController = navController,
                            novel = novel,
                            chapter = chapter
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NovelInfo(
    novelRepository: NovelRepository,
    novel: Novel
) {
    val context = LocalContext.current
    val title = novel.metadata.title

    Row(
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        Image(
            painter = rememberImagePainter(novel.coverFile),
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
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1.0f))
            Button(
                onClick = {
                    if (novel.inLibrary) {
                        // TODO: Support removing novels
                        Toast.makeText(
                            context,
                            "Not supported",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        novelRepository.addToLibrary(novel.metadata.id)
                    }
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
    chapter: ChapterMeta
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
                        chapter.id.toString()
                    )
                )
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
