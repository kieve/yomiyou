package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiContext
import ca.kieve.yomiyou.YomiScreen
import ca.kieve.yomiyou.data.NovelRepository
import ca.kieve.yomiyou.ui.composable.NovelCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelListScreen(yomiContext: YomiContext) {
    val navController = yomiContext.navController
    val novelRepository = yomiContext.appContainer.novelRepository
    val scheduler = yomiContext.appContainer.novelScheduler

    LaunchedEffect(Unit) {
        scheduler.setActiveNovel(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.novelList_title))
                },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(YomiScreen.NovelSearchNav.route)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription =
                                    stringResource(R.string.novelList_searchContentDescription)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Escape hatch: look at what the scraper's WebView is stuck on and solve it by hand.
            FloatingActionButton(
                onClick = {
                    navController.navigate(YomiScreen.WebViewNav.route)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Scraper browser"
                )
            }
        }
    ) { innerPadding ->
        NovelList(navController, novelRepository, Modifier.padding(innerPadding))
    }
}

@Composable
private fun NovelList(
    navController: NavController,
    novelRepository: NovelRepository,
    modifier: Modifier = Modifier
) {
    val library by novelRepository.library.collectAsState(emptyList())

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(library, key = { it.novel.id }) { entry ->
            NovelCard(
                title = entry.novel.title,
                subTitle = stringResource(
                    R.string.novelCard_chapterCount, entry.downloadedCount, entry.chapterCount),
                coverUrl = entry.novel.coverUrl,
                onClick = {
                    navController.navigate(
                        YomiScreen.NovelInfoNav.withArgs(entry.novel.id.toString())
                    )
                }
            )
        }
    }
}
