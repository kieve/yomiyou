package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.FabPosition
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiContext
import ca.kieve.yomiyou.YomiScreen
import ca.kieve.yomiyou.data.repository.NovelRepository
import ca.kieve.yomiyou.ui.composable.NovelCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun NovelListScreen(yomiContext: YomiContext) {
    /** Debug stuff **/
    val perfectRun = "the-perfect-run"
    val lordOfMysteries = "lord-of-the-mysteries-wn-24121303"
    val magesAreTooOp = "mages-are-too-op-wn-19072354"
    val novelsExtra = "the-novels-extra-19072354"
    val theBeginAfterEnd = "the-beginning-after-the-end-web-novel-02121804"
    val omniscentReadersVP = "orv-wn-24121303"
    val swordGod = "reincarnation-of-the-strongest-sword-god-lnv-24121303"
    val novel = "https://www.lightnovelpub.com/novel/$omniscentReadersVP"
    /*****************/

    val navController = yomiContext.navController
    val novelRepository = yomiContext.appContainer.novelRepository

    val job = Job()
    val scope = CoroutineScope(Dispatchers.IO + job)

    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                title = {
                    Text(stringResource(R.string.novelList_title))
                }
            )
    },
    floatingActionButtonPosition = FabPosition.End,
    floatingActionButton = {
        FloatingActionButton(onClick = {
            scope.launch {
//                novelRepository.crawlNovelInfo(novel)
                val chapterMeta = novelRepository.getNovel(1)?.chapters?.get(0)
                if (chapterMeta != null) {
                    novelRepository.downloadChapter(chapterMeta)
                }
            }
        }) {
            Icon(
                Icons.Filled.Warning,
                "Debug")
        }
    }
    ) {
        NovelList(navController, novelRepository)
    }
}

@Composable
private fun NovelList(
    navController: NavController,
    novelRepository: NovelRepository)
{
    val novels by novelRepository.novels.collectAsState()
    val novelList = novels.values.toList()
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(novelList) { novel ->
            NovelCard(novel, Modifier
                .clickable {
                    navController.navigate(
                        YomiScreen.ChapterListNav.withArgs(
                            novel.metadata.id.toString()))
                }
            )
        }
    }
}
