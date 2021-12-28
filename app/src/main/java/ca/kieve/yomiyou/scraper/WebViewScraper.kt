package ca.kieve.yomiyou.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import android.util.JsonToken
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import java.io.StringReader

class WebViewScraper(context: Context) : Scraper {
    companion object {
        private val GET_HTML_JS = """
        "(function() {
            return('<html>'
                + document.getElementsByTagName('html')[0].innerHTML
                + '</html>');
        })();"
        """.trimIndent()
    }

    private var webViewUpdateJob: CompletableJob? = null
    private var fetchedHtml: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    val webView = WebView(context).apply {
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
                view?.evaluateJavascript(GET_HTML_JS) {
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
        loadUrl(url ?: "about:blank")
    }

    override suspend fun getPageHtml(url: String): String? {
        webViewUpdateJob = Job()
        Handler(Looper.getMainLooper()).post {
            webView.loadUrl(url)
        }
        webViewUpdateJob!!.join()
        return fetchedHtml
    }
}
