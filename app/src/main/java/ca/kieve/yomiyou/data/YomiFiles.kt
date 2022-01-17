package ca.kieve.yomiyou.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import ca.kieve.yomiyou.data.database.model.ChapterMeta
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.util.ensureDirExists
import ca.kieve.yomiyou.util.ensureFileExists
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8

class YomiFiles(context: Context) {
    companion object {
        private val TAG = getTag()

        private const val NOVELS_DIR = "novels"
        private const val NOVEL_COVER_FILE_NAME = "cover"

        private const val SEARCH_DIR = "search"
    }

    private val novelsDir = File(context.filesDir, NOVELS_DIR)
    private val searchCache = File(context.cacheDir, SEARCH_DIR)

    private var debugFiles: Uri? = null

    fun registerDebugFiles(uri: Uri) {
        debugFiles = uri
        Log.d(TAG, "registerDebugFiles: $uri")
    }

    fun writeDebugFile(
        appContainer: AppContainer,
        filePath: String,
        contents: String
    ): Boolean {
        val uri = debugFiles ?: return false
        val docFile = DocumentFile.fromTreeUri(appContainer.appContext, uri)
        if (docFile == null) {
            Log.d(TAG, "writeDebugFile: DocFile is null for root folder")
            return false
        }

        val resultFile = docFile.ensureFileExists(
            mimeType = "",
            filePath = filePath
        )
        if (resultFile == null) {
            Log.d(TAG, "writeDebugFile: Failed to create file to write")
            return false
        }

        val fileUri = resultFile.uri
        val out = appContainer.appContext.contentResolver.openOutputStream(fileUri, "w")
        if (out == null) {
            Log.d(TAG, "writeDebugFile: Can't open out stream")
            return false
        }

        val writer = OutputStreamWriter(out, UTF_8.name())
        writer.write("Hello, world!\n$contents")
        writer.close()
        return true
    }

    private fun getNovelDir(novelMeta: NovelMeta): File {
        return getNovelDir(novelMeta.id)
    }

    private fun getNovelDir(novelId: Long): File {
        val novelDir = File(novelsDir, novelId.toString())
        ensureDirExists(novelDir)
        return novelDir
    }

    suspend fun clearSearchCache() = withContext(Dispatchers.IO) {
        searchCache.delete()
    }

    suspend fun getNovelCoverFile(novelMeta: NovelMeta): File? = withContext(Dispatchers.IO) {
        val novelDir = getNovelDir(novelMeta)
        if (!novelDir.exists()) {
            return@withContext null
        }
        val covers = novelDir.listFiles { file ->
            file.isFile
                    && file.name.startsWith(NOVEL_COVER_FILE_NAME)
        }
        if (covers == null || covers.isEmpty()) {
            return@withContext null
        }
        return@withContext covers[0]
    }

    suspend fun writeNovelCover(
        novelMeta: NovelMeta,
        extension: String,
        bytes: ByteArray
    ): File? = withContext(Dispatchers.IO)
    {
        val novelDir = getNovelDir(novelMeta)

        // Delete any existing cover images
        for (file in novelDir.listFiles() ?: emptyArray()) {
            if (file.name.startsWith(NOVEL_COVER_FILE_NAME)) {
                file.delete()
            }
        }

        // Write the new cover image
        val coverFile = File(novelDir, NOVEL_COVER_FILE_NAME + extension)
        runCatching {
            coverFile.outputStream()
                .buffered()
                .write(bytes)
        }.onFailure { e ->
            Log.e(TAG, "writeNovelCover", e)
            return@withContext null
        }
        return@withContext coverFile
    }

    suspend fun writeSearchCover(
        tempId: Long,
        extension: String,
        bytes: ByteArray
    ): File? = withContext(Dispatchers.IO)
    {
        ensureDirExists(searchCache)

        val coverFile = File(searchCache, "$tempId$extension")
        runCatching {
            coverFile.outputStream()
                .buffered()
                .write(bytes)
        }.onFailure { e ->
            Log.e(TAG, "writeSearchCover", e)
            return@withContext null
        }
        return@withContext coverFile
    }

    suspend fun migrateSearchCover(
        novelMeta: NovelMeta,
        coverFile: File
    ): File = withContext(Dispatchers.IO)
    {
        val novelDir = getNovelDir(novelMeta)
        val newFile = File(novelDir, "$NOVEL_COVER_FILE_NAME.${coverFile.extension}")
        coverFile.copyTo(newFile)
        return@withContext newFile
    }

    suspend fun chapterExists(chapterMeta: ChapterMeta): Boolean = withContext(Dispatchers.IO) {
        return@withContext getChapterFile(chapterMeta) != null
    }

    suspend fun getChapterFile(chapterMeta: ChapterMeta): File? = withContext(Dispatchers.IO) {
        val novelDir = getNovelDir(chapterMeta.novelId)
        if (!novelDir.exists()) {
            return@withContext null
        }

        val chapterFile = File(novelDir, chapterMeta.id.toString())
        if (!chapterFile.exists()) {
            return@withContext null
        }
        return@withContext chapterFile
    }

    suspend fun writeChapter(
        chapterMeta: ChapterMeta,
        content: String
    ): File? = withContext(Dispatchers.IO)
    {
        val novelDir = getNovelDir(chapterMeta.novelId)
        val chapterFile = File(novelDir, chapterMeta.id.toString())

        // Delete the existing chapter
        if (chapterFile.exists()) {
            chapterFile.delete()
        }

        // Write the new chapter
        runCatching {
            chapterFile.outputStream()
                .buffered()
                .write(content.toByteArray())
        }.onFailure { e ->
            Log.e(TAG, "writeChapter", e)
            return@withContext null
        }
        return@withContext chapterFile
    }
}
