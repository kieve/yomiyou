package ca.kieve.yomiyou.data.model

import ca.kieve.yomiyou.crawler.model.NovelInfo
import java.io.File

data class SearchResult(
    val tempId: Long,
    val novelInfo: NovelInfo,
    val coverFile: File? = null
)
