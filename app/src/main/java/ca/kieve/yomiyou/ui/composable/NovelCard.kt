package ca.kieve.yomiyou.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.data.model.Novel
import coil.compose.ImagePainter
import coil.compose.rememberImagePainter
import java.io.File

@Composable
fun NovelCard(
    novel: Novel,
    modifier: Modifier = Modifier
) {
    NovelCard(
        title = novel.metadata.title,
        subTitle = stringResource(id = R.string.novelCard_chapterCount, novel.chapters.size),
        coverPainter = rememberImagePainter(novel.coverFile),
        modifier = modifier
    )
}

@Composable
fun NovelCard(
    title: String,
    subTitle: String,
    coverFile: File?,
    modifier: Modifier = Modifier
) {
    NovelCard(
        title = title,
        subTitle = subTitle,
        coverPainter = rememberImagePainter(coverFile),
        modifier
    )
}

@Composable
fun NovelCard(
    title: String,
    subTitle: String,
    coverPainter: ImagePainter,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = MaterialTheme.shapes.medium,
        elevation = 5.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = coverPainter,
                contentDescription = stringResource(
                    id = R.string.novelCard_imageContentDescription,
                    title),
                modifier = Modifier
                    .size(60.dp, 80.dp)
                    .padding(end = 8.dp),
                contentScale = ContentScale.FillBounds
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
