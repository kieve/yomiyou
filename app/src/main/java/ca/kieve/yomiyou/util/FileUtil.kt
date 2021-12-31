package ca.kieve.yomiyou.util

import java.io.File

fun ensureDirExists(dir: File) {
    if (!dir.exists()) {
        dir.mkdirs()
    }
}
