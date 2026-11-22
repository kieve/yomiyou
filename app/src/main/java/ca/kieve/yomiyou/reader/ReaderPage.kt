package ca.kieve.yomiyou.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Magnifier
import ca.kieve.yomiyou.book.Position
import ca.kieve.yomiyou.dictionary.Dictionary
import androidx.webkit.JavaScriptReplyProxy
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
    private val dictionary: Dictionary,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady()

        fun onPosition(position: Position)

        /** A single tap, on [phrase] or (null) between phrases. */
        fun onTap(phrase: Long?)

        fun onDoubleTap()

        /** A hold began a lookup: the narration pauses. */
        fun onHold()

        /** The lookup opened or closed: while open, Back closes it. */
        fun onLookup(open: Boolean)

        /** Fix reading was chosen in the lookup, for [text] (a book from the server). */
        fun onFix(text: String)

        fun onError(message: String)
    }

    val view = WebView(context)
    /** Made on the first drag of a lookup handle. */
    private var magnifier: PageMagnifier? = null
    private var style: ReadingStyle? = null
    private var hasDictionary = false

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
        WebViewCompat.addWebMessageListener(view, "yomiyouHost", setOf(ReaderUrls.ORIGIN)) { _, message, _, isMainFrame, reply ->
            if (isMainFrame) message.data?.let { received(it, reply) }
        }
        view.loadUrl(ReaderUrls.PAGE)
    }

    /**
     * Starts the book at [chapter]: at [span] (a phrase) or [path] (a plain book's block) if given, else at the
     * chapter's start; [server]: readings can be fixed.
     */
    fun open(chapter: Int, span: Long?, style: ReadingStyle, server: Boolean = false, path: String? = null) {
        this.style = style
        val start = buildJsonObject {
            put("vertical", book.vertical)
            put("plain", book.plain)
            put("path", path?.let(::JsonPrimitive) ?: JsonNull)
            put("count", book.chapters.size)
            put("chapter", chapter)
            put("span", span?.let(::JsonPrimitive) ?: JsonNull)
            put("style", variables(style))
            put("dictionary", hasDictionary)
            put("server", server)
        }
        view.evaluateJavascript("reader.open($start)", null)
    }

    fun jump(chapter: Int) {
        view.evaluateJavascript("reader.jump($chapter)", null)
    }

    /** Lights the phrase being spoken, in [chapter]; null puts the light out. */
    fun light(phrase: Long?, chapter: Int) {
        view.evaluateJavascript("reader.light(${phrase ?: "null"}, $chapter)", null)
    }

    fun applyStyle(style: ReadingStyle) {
        if (style == this.style) return
        this.style = style
        view.evaluateJavascript("reader.style(${variables(style)})", null)
    }

    /** Whether holding a word looks it up. */
    fun setDictionary(has: Boolean) {
        if (has == hasDictionary) return
        hasDictionary = has
        view.evaluateJavascript("reader.dictionary($has)", null)
    }

    fun closeLookup() {
        view.evaluateJavascript("reader.closeLookup()", null)
    }

    fun destroy() {
        magnifier?.dismiss()
        view.destroy()
    }

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

    /** Answers a lookup's query: {id, entries}. */
    private fun find(id: Int, texts: List<String>, reply: JavaScriptReplyProxy) {
        scope.launch {
            val entries = withContext(Dispatchers.IO) { runCatching { dictionary.findJson(texts) }.getOrDefault("[]") }
            reply.postMessage("""{"id":$id,"entries":$entries}""")
        }
    }

    private fun variables(style: ReadingStyle): JsonObject =
        JsonObject(style.variables.mapValues { JsonPrimitive(it.value) })

    private fun received(data: String, reply: JavaScriptReplyProxy) {
        val message = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        fun int(name: String) = message[name]?.jsonPrimitive?.intOrNull
        fun long(name: String) = message[name]?.jsonPrimitive?.longOrNull
        when (message["type"]?.jsonPrimitive?.content) {
            "ready" -> listener.onReady()
            "need" -> int("chapter")?.takeIf { it in book.chapters.indices }?.let(::add)
            "position" -> {
                val chapter = int("chapter") ?: return
                val path = message["path"]?.jsonPrimitive?.contentOrNull
                val span = long("span")
                when {
                    path != null -> listener.onPosition(Position(chapter, -1, path))
                    span != null -> listener.onPosition(Position(chapter, span))
                }
            }
            "tap" -> listener.onTap(long("span"))
            "double" -> listener.onDoubleTap()
            "hold" -> listener.onHold()
            "lookup" -> listener.onLookup(message["open"]?.jsonPrimitive?.booleanOrNull == true)
            "drag" -> {
                val x = message["x"]?.jsonPrimitive?.doubleOrNull
                val y = message["y"]?.jsonPrimitive?.doubleOrNull
                if (x != null && y != null) {
                    val shown = magnifier ?: PageMagnifier(view, book.vertical).also { magnifier = it }
                    shown.show(x.toFloat(), y.toFloat())
                } else {
                    magnifier?.dismiss()
                }
            }
            "fix" -> message["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let(listener::onFix)
            "find" -> find(int("id") ?: return, message["texts"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(), reply)
            "error" -> listener.onError(message["message"]?.jsonPrimitive?.content.orEmpty())
        }
    }
}

/**
 * Android's own text magnifier over the page while a lookup handle is dragged, showing the selection's edge (the
 * finger covers it, or has drifted off to the side). Horizontal lines: a wide window above it. Vertical columns: a tall
 * one to its left, away from a right hand's finger, or to its right where the left has no room, so nothing up or down
 * the column is hidden. Before Android 10, Android's default window, above it.
 */
private class PageMagnifier(private val view: View, private val vertical: Boolean) {
    private val dp = view.resources.displayMetrics.density
    private val width = if (vertical) 72 * dp else 168 * dp
    private val height = if (vertical) 168 * dp else 64 * dp
    /** From the edge character's centre to the window's near edge. */
    private val gap = 36 * dp

    private val magnifier: Magnifier? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> null
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> Magnifier(view)
        else -> Magnifier.Builder(view)
            .setSize(width.toInt(), height.toInt())
            .setInitialZoom(1.5f)
            .setCornerRadius(12 * dp)
            .build()
    }

    fun show(x: Float, y: Float) {
        val magnifier = magnifier ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            magnifier.show(x, y)
            return
        }
        // Centres, in the view's pixels.
        val (centreX, centreY) = if (vertical) {
            val left = x - gap - width / 2
            val right = x + gap + width / 2
            val beside = if (left - width / 2 >= 0) left else right
            beside to y.coerceIn(height / 2, (view.height - height / 2).coerceAtLeast(height / 2))
        } else {
            x to y - gap - height / 2
        }
        magnifier.show(x, y, centreX, centreY)
    }

    fun dismiss() {
        magnifier?.dismiss()
    }
}
