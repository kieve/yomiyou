package ca.kieve.yomiyou

import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.crawler.Crawler
import ca.kieve.yomiyou.crawler.CrawlingWebView
import ca.kieve.yomiyou.ui.theme.YomiyouTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    companion object {
        // val TAG: String = MainActivity::class.java.simpleName
        private const val TAG = "FUCK-MainActivity"
    }

    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        setContent {
            MyApp {
                MyScreenContent(viewModel.crawler)
            }
        }
    }
}

@Composable
fun MyScreenContent(crawler: Crawler) {
    val requestUrl by crawler.requestedUrl

    val job = Job()
    val scope = CoroutineScope(Dispatchers.Default + job)

    Column {
        Row {
            Button(onClick = {
                scope.launch(Dispatchers.Default) {
                    crawler.readNovelInfo()
                }
            }) {
                Text(text = "Do Debug stuff")
            }
        }
        CrawlingWebView(requestUrl) {
            crawler.webViewUpdated(it)
        }
    }
}

@Composable
fun Counter(count: Int, increment: () -> Unit) {
    Button(onClick = { increment() }) {
        Text(text = "I've been clicked $count times")
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

@Composable
fun Greeting(name: String) {
    Surface(color = Color.Yellow) {
        Text(
            text = "Hello $name!",
            modifier = Modifier.padding(16.dp))
    }
}