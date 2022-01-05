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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.StringReader

class WebViewScraper(context: Context) : Scraper {
    private sealed class State {
        object Default: State()
        object LoadPage: State()
    }

    companion object {
        private var TAG = getTag()
        private const val GET_HTML_JS = "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();"
    }

    private val webViewMutex = Mutex()
    private var state: State = State.Default
    private var webViewUpdateJob: CompletableJob = Job()
    private var javascriptResult: String? = null

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
                Log.d(TAG, "onPageFinished ($state): $url")
                if (state == State.LoadPage) {
                    internalExecuteJs(GET_HTML_JS)
                }
            }
        }
        loadUrl("about:blank")
    }

    private fun internalExecuteJs(js: String) {
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript(js) { result ->
                val jsonReader = JsonReader(StringReader(result))
                jsonReader.isLenient = true
                javascriptResult = if (jsonReader.peek() == JsonToken.STRING) {
                    jsonReader.nextString()
                } else {
                    null
                }
                webViewUpdateJob.complete()
            }
        }
    }

    override suspend fun loadPage(url: String): String? {
        webViewMutex.withLock {
            Log.d(TAG, "loadPage: $url")
            state = State.LoadPage
            webViewUpdateJob = Job()
            Handler(Looper.getMainLooper()).post {
                webView.loadUrl(url)
            }
            webViewUpdateJob.join()
            state = State.Default
            return javascriptResult
        }
    }

    override suspend fun getCurrentPageHtml(): String? {
        webViewMutex.withLock {
            Log.d(TAG, "getCurrentPageHtml")
            webViewUpdateJob = Job()
            internalExecuteJs(GET_HTML_JS)
            webViewUpdateJob.join()
            return javascriptResult
        }
    }

    override suspend fun executeJs(js: String): String? {
        webViewMutex.withLock {
            Log.d(TAG, "executeJs")
            webViewUpdateJob = Job()
            internalExecuteJs(js)
            webViewUpdateJob.join()
            return javascriptResult
        }
    }
}
