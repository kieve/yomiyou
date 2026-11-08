package ca.kieve.yomiyou.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import ca.kieve.yomiyou.ui.theme.YomiyouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (savedInstanceState == null) opened(intent)

        setContent {
            YomiyouTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Home()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened(intent)
    }

    private fun opened(intent: Intent?) {
        if (intent?.action != OPEN_NARRATION) return
        services.narration.book.value?.let { services.openRequests.value = it.bookId }
    }

    companion object {
        /** From the media notification: back to the book being narrated. */
        const val OPEN_NARRATION = "ca.kieve.yomiyou.OPEN_NARRATION"
    }
}
