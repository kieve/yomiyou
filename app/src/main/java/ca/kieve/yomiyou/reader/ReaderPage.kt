package ca.kieve.yomiyou.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import ca.kieve.yomiyou.book.Position
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The reader page (`assets/reader/`) in a WebView, served with the book's files from one origin. The page scrolls
 * through the whole book, asking for chapters as the reader nears them; it reports the first phrase on screen and taps.
 */
@SuppressLint("SetJavaScriptEnabled")
class ReaderPage(
    context: Context,
    private val book: ReaderBook,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady()

        fun onPosition(position: Position)

        /** A single tap, on [phrase] or (null) between phrases. */
        fun onTap(phrase: Int?)

        fun onDoubleTap()

        fun onError(message: String)
    }

    val view = WebView(context)
    private var style: ReadingStyle? = null

    init {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/book/") { path ->
                book.resource(path)?.let { WebResourceResponse(it.mediaType, null, it.bytes.inputStream()) }
            }
            .build()
        // With wrap_content the page's viewport shrinks to its content, and height: 100% comes to nothing.
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.setBackgroundColor(Color.BLACK)
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        WebViewCompat.addWebMessageListener(view, "yomiyouHost", setOf(ReaderUrls.ORIGIN)) { _, message, _, isMainFrame, _ ->
            if (isMainFrame) message.data?.let(::received)
        }
        view.loadUrl(ReaderUrls.PAGE)
    }

    /** Starts the book at [chapter]: at [span] if given, else at the chapter's start. */
    fun open(chapter: Int, span: Int?, style: ReadingStyle) {
        this.style = style
        val start = buildJsonObject {
            put("vertical", book.vertical)
            put("count", book.chapters.size)
            put("chapter", chapter)
            put("span", span?.let(::JsonPrimitive) ?: JsonNull)
            put("style", variables(style))
        }
        view.evaluateJavascript("reader.open($start)", null)
    }

    fun jump(chapter: Int) {
        view.evaluateJavascript("reader.jump($chapter)", null)
    }

    /** Lights the phrase being spoken, in [chapter]; null puts the light out. */
    fun light(phrase: Int?, chapter: Int) {
        view.evaluateJavascript("reader.light(${phrase ?: "null"}, $chapter)", null)
    }

    fun applyStyle(style: ReadingStyle) {
        if (style == this.style) return
        this.style = style
        view.evaluateJavascript("reader.style(${variables(style)})", null)
    }

    fun destroy() = view.destroy()

    private fun add(chapter: Int) {
        scope.launch {
            val html = withContext(Dispatchers.Default) { runCatching { book.chapter(chapter) } }
            html.onSuccess {
                val content = buildJsonObject {
                    put("chapter", chapter)
                    put("html", it)
                    put("title", book.chapters[chapter])
                }
                view.evaluateJavascript("reader.add($content)", null)
            }.onFailure { listener.onError(it.message ?: it.toString()) }
        }
    }

    private fun variables(style: ReadingStyle): JsonObject =
        JsonObject(style.variables.mapValues { JsonPrimitive(it.value) })

    private fun received(data: String) {
        val message = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        fun int(name: String) = message[name]?.jsonPrimitive?.intOrNull
        when (message["type"]?.jsonPrimitive?.content) {
            "ready" -> listener.onReady()
            "need" -> int("chapter")?.takeIf { it in book.chapters.indices }?.let(::add)
            "position" -> {
                val chapter = int("chapter") ?: return
                val span = int("span") ?: return
                listener.onPosition(Position(chapter, span))
            }
            "tap" -> listener.onTap(int("span"))
            "double" -> listener.onDoubleTap()
            "error" -> listener.onError(message["message"]?.jsonPrimitive?.content.orEmpty())
        }
    }
}
