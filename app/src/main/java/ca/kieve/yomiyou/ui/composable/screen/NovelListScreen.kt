package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
                backgroundColor = MaterialTheme.colors.primary,
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
        }
    ) {
        NovelList(navController, novelRepository)
    }
}

@Composable
private fun NovelList(
    navController: NavController,
    novelRepository: NovelRepository
) {
    val novels by novelRepository.novels.collectAsState()
    val novelList = novels.values
        .filter { novel -> novel.inLibrary }
        .toList()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(novelList) { novel ->
            NovelCard(
                novel = novel,
                modifier = Modifier
                    .clickable {
                        navController.navigate(
                            YomiScreen.NovelInfoNav.withArgs(
                                novel.metadata.id.toString())
                        )
                    }
            )
        }
    }
}
