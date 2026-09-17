package ca.kieve.yomiyou.ui.composable.screen

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ca.kieve.yomiyou.YomiContext

/**
 * Shows the scraper's own WebView so a bot challenge that needs a human (click, captcha) can be
 * solved in place. Any pending loadPage keeps polling for its selector while this is open.
 */
@Composable
fun WebViewScreen(yomiContext: YomiContext) {
    val webView = yomiContext.appContainer.scraper.webView
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        }
    )
}
