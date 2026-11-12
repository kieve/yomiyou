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
    @Composable
    fun Item()
}

private val sources: List<BrowseSource> = listOf(EpubFiles)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_browse)) })
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            items(sources) { it.Item() }
        }
    }
}

private object EpubFiles : BrowseSource {
    @Composable
    override fun Item() {
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
