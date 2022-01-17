package ca.kieve.yomiyou.util

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

private val TAG = getTag("FileUtil")

fun ensureDirExists(dir: File) {
    if (!dir.exists()) {
        dir.mkdirs()
    }
}

fun DocumentFile.ensureFileExists(mimeType: String, filePath: String): DocumentFile? {
    val parts = filePath.split("/")
    val iterator = parts.iterator()
    var currentDir: DocumentFile? = this
    while (iterator.hasNext()) {
        if (currentDir == null) {
            return null
        }

        val part = iterator.next()
        if (iterator.hasNext()) {
            // This part represents a directory
            currentDir = ensureDfDirExists(
                parentDir = currentDir,
                subDirName = part
            )
        } else {
            // This is the file at the end of the path
            return ensureDfFileExists(
                parentDir = currentDir,
                mimeType = mimeType,
                name = part
            )
        }
    }

    return null
}

private fun ensureDfDirExists(parentDir: DocumentFile, subDirName: String): DocumentFile? {
    var subDir = parentDir.findFile(subDirName)
    if (subDir != null) {
        if (subDir.isFile) {
            Log.d(TAG, "ensureDfDirExists: file exists, but is a file not a dir: $subDirName")
            return null
        }
        return subDir
    }

    subDir = parentDir.createDirectory(subDirName)
    if (subDir == null) {
        Log.d(TAG, "ensureDfDirExists: Failed to create directory: $subDirName")
        return null
    }
    return subDir
}

private fun ensureDfFileExists(
    parentDir: DocumentFile,
    mimeType: String,
    name: String
): DocumentFile? {
    var file = parentDir.findFile(name)
    if (file != null) {
        if (file.isDirectory) {
            Log.d(TAG, "ensureDfFileExists: file exists, but is a dir not a file: $name")
            return null
        }
        return file
    }

    file = parentDir.createFile(mimeType, name)
    if (file == null) {
        Log.d(TAG, "ensureDfFileExists: Failed to create file: $name")
        return null
    }

    return file
}
