package ca.kieve.yomiyou

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ca.kieve.yomiyou.ui.theme.YomiyouTheme
import ca.kieve.yomiyou.util.getTag

class YomiActivity : AppCompatActivity() {
    companion object {
        private val TAG = getTag()
    }

    private val viewModel by viewModels<YomiViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as YomiApplication).container

        setContent {
            MyApp {
                YomiNavigation(appContainer = container)
            }
        }
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    YomiyouTheme(true) {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight())
        {
            content()
        }
    }
}
