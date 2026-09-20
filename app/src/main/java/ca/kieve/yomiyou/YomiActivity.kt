package ca.kieve.yomiyou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ca.kieve.yomiyou.ui.theme.YomiyouTheme

class YomiActivity : ComponentActivity() {
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
    YomiyouTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                content()
            }
        }
    }
}
