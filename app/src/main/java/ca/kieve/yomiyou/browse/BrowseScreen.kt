package ca.kieve.yomiyou.browse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.services

/** A way to add books, listed in Browse. */
interface BrowseSource {
    /** [onStarted]: work began that the Activity tab follows. */
    @Composable
    fun Item(onStarted: () -> Unit)
}

private val sources: List<BrowseSource> = listOf(EpubFiles, DictionaryFile)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onStarted: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_browse)) })
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            items(sources) { it.Item(onStarted) }
        }
    }
}

private object EpubFiles : BrowseSource {
    @Composable
    override fun Item(onStarted: () -> Unit) {
        val importer = LocalContext.current.services.importer
        val busy by importer.busy.collectAsState()
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            importer.import(it)
        }
        val summary = if (busy) R.string.browse_busy else R.string.browse_epubFilesSummary
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_file_open), contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.browse_epubFiles)) },
            supportingContent = { Text(stringResource(summary)) },
            modifier = Modifier.clickable(enabled = !busy) { pick.launch(arrayOf("*/*")) }
        )
    }
}

private object DictionaryFile : BrowseSource {
    @Composable
    override fun Item(onStarted: () -> Unit) {
        val dictionary = LocalContext.current.services.dictionary
        val busy by dictionary.busy.collectAsState()
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                dictionary.import(uri)
                onStarted()
            }
        }
        val summary = if (busy) R.string.browse_busy else R.string.browse_dictionarySummary
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_dictionary), contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.browse_dictionary)) },
            supportingContent = { Text(stringResource(summary)) },
            modifier = Modifier.clickable(enabled = !busy) { pick.launch(arrayOf("application/zip", "*/*")) }
        )
    }
}
