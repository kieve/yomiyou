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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.StringReader
import kotlin.coroutines.resume

class WebViewScraper(context: Context) : Scraper {
    companion object {
        private val TAG = getTag()
        private const val GET_HTML_JS = "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();"
        private const val POLL_MS = 250L
    }

    private val mutex = Mutex()
    private val mainThread = Handler(Looper.getMainLooper())
    private var pageFinished = CompletableDeferred<Unit>()

    @SuppressLint("SetJavaScriptEnabled")
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Bot checks fingerprint the stock WebView UA; make it look like Chrome.
        settings.userAgentString = settings.userAgentString
            .replace("; wv", "")
            .replace("Version/4.0 ", "")

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "onPageFinished: $url")
                if (url != "about:blank") {
                    pageFinished.complete(Unit)
                }
            }
        }
        loadUrl("about:blank")
    }

    override suspend fun loadPage(url: String, waitFor: String?, timeoutMs: Long): String? =
        mutex.withLock {
            Log.d(TAG, "loadPage: $url (waitFor=$waitFor)")
            pageFinished = CompletableDeferred()
            mainThread.post { webView.loadUrl(url) }

            val html = withTimeoutOrNull(timeoutMs) {
                pageFinished.await()
                // onPageFinished also fires for anti-bot interstitials and redirects, so keep
                // polling the live DOM until the content we actually want is there.
                if (waitFor != null) {
                    val check = "String(!!document.querySelector(${JSONObject.quote(waitFor)}))"
                    while (evalJs(check) != "true") {
                        delay(POLL_MS)
                    }
                }
                evalJs(GET_HTML_JS)
            }
            if (html == null) {
                Log.w(TAG, "loadPage: timed out after ${timeoutMs}ms: $url")
            }
            html
        }

    /** Runs [js] on the main thread; returns its result only if it is a string. */
    private suspend fun evalJs(js: String): String? = suspendCancellableCoroutine { cont ->
        mainThread.post {
            webView.evaluateJavascript(js) { result ->
                val reader = JsonReader(StringReader(result)).apply { isLenient = true }
                cont.resume(if (reader.peek() == JsonToken.STRING) reader.nextString() else null)
            }
        }
    }
}
