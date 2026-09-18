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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class WebViewScraper(context: Context) : Scraper {
    companion object {
        private val TAG = getTag()
        private const val GET_HTML_JS = "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();"
        private const val POLL_MS = 250L
        private const val CAPTURE_KEEP = 20
    }

    private val mutex = Mutex()
    private val mainThread = Handler(Looper.getMainLooper())
    private var pageFinished = CompletableDeferred<Unit>()

    // Every page the scraper saw (or was stuck on), for debugging parsers and grabbing fixtures:
    // Device Explorer -> data/data/ca.kieve.yomiyou/cache/scraper/
    private val captureDir = File(context.cacheDir, "scraper")

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
                capture(url, evalJs(GET_HTML_JS), "TIMEOUT")
            } else {
                capture(url, html, "ok")
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

    private suspend fun capture(url: String, html: String?, status: String) = withContext(Dispatchers.IO) {
        runCatching {
            captureDir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val slug = url.substringAfter("://").replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80)
            File(captureDir, "$stamp-$status-$slug.html").writeText(html ?: "")
            captureDir.listFiles()
                ?.sortedBy { it.name }
                ?.dropLast(CAPTURE_KEEP)
                ?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "capture failed", it) }
    }
}
