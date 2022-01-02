package ca.kieve.yomiyou.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import java.io.StringReader

class WebViewScraper(context: Context) : Scraper {
    companion object {
        private var TAG = getTag()
        private val GET_HTML_JSON = "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();";
    }

    private var webViewUpdateJob: CompletableJob? = null
    private var fetchedHtml: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == "about:blank") {
                    return
                }
                Log.d(TAG, "onPageFinished: $url")
                view?.evaluateJavascript(GET_HTML_JSON) {
                    val jsonReader = JsonReader(StringReader(it))
                    jsonReader.isLenient = true
                    if (jsonReader.peek() == JsonToken.STRING) {
                        val domString = jsonReader.nextString()
                        if (domString != null) {
                            fetchedHtml = domString
                            webViewUpdateJob!!.complete()
                        }
                    }
                }
            }
        }
        loadUrl("about:blank")
    }

    override suspend fun getPageHtml(url: String): String? {
        Log.d(TAG, "getPageHtml: $url")
        webViewUpdateJob = Job()
        Handler(Looper.getMainLooper()).post {
            webView.loadUrl(url)
        }
        webViewUpdateJob!!.join()
        return fetchedHtml
    }
}
