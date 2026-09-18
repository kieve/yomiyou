package ca.kieve.yomiyou.data

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.data.database.model.Chapter
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class YomiFiles(context: Context) {
    companion object {
        private val TAG = getTag()
        private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9._-]+")
    }

    private val novelsDir = File(context.filesDir, "novels")

    /** `novels/<novelId>/<last URL segment>.md`; stable for the chapter's whole life. */
    fun chapterFile(chapter: Chapter): File {
        val slug = chapter.url.trimEnd('/').substringAfterLast('/').replace(UNSAFE_FILENAME_CHARS, "_")
        return File(File(novelsDir, chapter.novelId.toString()), "$slug.md")
    }

    suspend fun writeChapter(chapter: Chapter, markdown: String): Boolean = withContext(Dispatchers.IO) {
        val file = chapterFile(chapter)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(markdown)
        }.onFailure { e ->
            Log.e(TAG, "writeChapter: $file", e)
        }.isSuccess
    }

    suspend fun readChapter(chapter: Chapter): String? = withContext(Dispatchers.IO) {
        chapterFile(chapter).takeIf { it.exists() }?.readText()
    }
}
