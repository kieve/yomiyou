package ca.kieve.yomiyou.crawler

import android.annotation.SuppressLint
import android.util.JsonReader
import android.util.JsonToken
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import java.io.StringReader

/**
 * All attempts to use "normal" crawlers failed due to Cloudflare.
 * So, we'll just use a WebView and pull the HTML from this.
 */

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CrawlingWebView(url: String?, pageLoaded: (html: String) -> Unit) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url == "about:blank") {
                        return
                    }
                    view?.evaluateJavascript("(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();") {
                        val jsonReader = JsonReader(StringReader(it))
                        jsonReader.isLenient = true
                        if (jsonReader.peek() == JsonToken.STRING) {
                            val domString = jsonReader.nextString()
                            if (domString != null) {
                                pageLoaded(domString)
                            }
                        }
                    }
                }
            }
            loadUrl(url ?: "about:blank")
        }
    }, update = {
        it.loadUrl(url ?: "about:blank")
    })
}