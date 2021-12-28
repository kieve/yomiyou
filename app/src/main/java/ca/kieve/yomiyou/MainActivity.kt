package ca.kieve.yomiyou

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import ca.kieve.yomiyou.data.repository.NovelRepository
import ca.kieve.yomiyou.ui.theme.YomiyouTheme
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = getTag()
    }

    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as YomiyouApplication).container

        setContent {
            MyApp {
                MyScreenContent(container.novelRepository)
            }
        }
    }
}

@Composable
fun MyScreenContent(novelRepository: NovelRepository) {
    val perfectRun = "the-perfect-run"
    val swordGod = "reincarnation-of-the-strongest-sword-god-lnv-24121303"
    val novel = "https://www.lightnovelpub.com/novel/$perfectRun"

    val job = Job()
    val scope = CoroutineScope(Dispatchers.IO + job)

    Column {
        Row {
            Button(onClick = {
                scope.launch {
                    novelRepository.crawlNovelInfo(novel)
                }
            }) {
                Text(text = "Do Debug stuff")
            }
        }
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    YomiyouTheme {
        // A surface container using the 'background' color from the theme
        Surface(color = MaterialTheme.colors.background) {
            content()
        }
    }
}
