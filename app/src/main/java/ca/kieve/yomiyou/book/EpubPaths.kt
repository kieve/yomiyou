package ca.kieve.yomiyou.book

import java.io.ByteArrayOutputStream

/** Paths inside an EPUB's zip, and which documents make up a chapter: the web reader's `epubpath.ts`. */
object EpubPaths {
    /** The folder of a zip path, with its slash; empty at the root. */
    fun folderOf(path: String): String = path.substring(0, path.lastIndexOf('/') + 1)

    /** A link in a document, as a zip path: relative parts resolved, any fragment dropped, percent escapes decoded. */
    fun resolve(from: String, href: String): String {
        val bare = percentDecode(href.substringBefore('#'))
        val parts = ArrayDeque<String>()
        val joined = if (bare.startsWith("/")) bare.substring(1) else folderOf(from) + bare
        for (part in joined.split('/')) {
            when (part) {
                ".." -> parts.removeLastOrNull()
                ".", "" -> {}
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    /** Whether a link leaves the book. */
    fun isExternal(href: String): Boolean = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(href)

    /**
     * Spine documents with no chapter of their own (a cover image, say) that come just before this chapter's document:
     * they have no text, so the build gave them no chapter, and they'd never be shown. Only a document's first chapter
     * takes them.
     */
    fun leadingDocuments(spine: List<String>, chapterHrefs: List<String>, index: Int): List<String> {
        val href = chapterHrefs.getOrNull(index) ?: return emptyList()
        if (chapterHrefs.getOrNull(index - 1) == href) return emptyList()
        val leading = ArrayDeque<String>()
        for (at in spine.indexOf(href) - 1 downTo 0) {
            val document = spine[at]
            if (document in chapterHrefs) break
            leading.addFirst(document)
        }
        return leading.toList()
    }

    private val MEDIA_TYPES = mapOf(
        "gif" to "image/gif",
        "jpeg" to "image/jpeg",
        "jpg" to "image/jpeg",
        "png" to "image/png",
        "svg" to "image/svg+xml",
        "webp" to "image/webp",
        "css" to "text/css",
        "otf" to "font/otf",
        "ttf" to "font/ttf",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
    )

    fun mediaType(path: String): String =
        MEDIA_TYPES[path.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

    /** For a path in a URL: each segment percent-encoded, slashes kept. */
    fun percentEncode(path: String): String = buildString {
        for (byte in path.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            if (c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "-._~/")) {
                append(c.toChar())
            } else {
                append('%').append("%02X".format(c))
            }
        }
    }

    private fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val out = ByteArrayOutputStream()
        var at = 0
        while (at < text.length) {
            val c = text[at]
            if (c == '%' && at + 3 <= text.length) {
                val code = text.substring(at + 1, at + 3).toIntOrNull(16)
                if (code != null) {
                    out.write(code)
                    at += 3
                    continue
                }
            }
            out.write(c.toString().encodeToByteArray())
            at++
        }
        return out.toByteArray().decodeToString()
    }
}
