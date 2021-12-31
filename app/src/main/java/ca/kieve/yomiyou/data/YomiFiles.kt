package ca.kieve.yomiyou.data

import android.content.Context
import android.util.Log
import ca.kieve.yomiyou.YomiApplication
import ca.kieve.yomiyou.data.database.model.NovelMeta
import ca.kieve.yomiyou.util.ensureDirExists
import ca.kieve.yomiyou.util.getTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class YomiFiles(context: Context) {
    companion object {
        private val TAG = getTag()

        private const val NOVELS_DIR = "novels"
        private const val NOVEL_COVER_FILE_NAME = "cover"
    }

    private val appContainer = (context as YomiApplication).container

    val novelsDir = File(context.filesDir, NOVELS_DIR)

    private fun getNovelDir(novelMeta: NovelMeta): File {
        val novelDir = File(novelsDir, novelMeta.id.toString())
        ensureDirExists(novelDir)
        return novelDir
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

    suspend fun novelCoverExists(novelMeta: NovelMeta): Boolean = withContext(Dispatchers.IO) {
        return@withContext getNovelCoverFile(novelMeta) != null
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
}
